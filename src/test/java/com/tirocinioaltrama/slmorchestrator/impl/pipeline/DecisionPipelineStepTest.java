package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DecisionPipelineStepTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private ModelClient mockModelClient;

    private final String sampleRolesJson = """
            {
              "direzione": {
                "ruolo": "Direttore",
                "persona": "Stefano Vena",
                "email": "stefano.vena@azienda.it",
                "ambiti": ["strategia", "budget"]
              },
              "risorse_umane": {
                "ruolo": "H.R.",
                "persona": "Claudia Frammartino",
                "email": "claudia.frammartino@azienda.it",
                "ambiti": ["recruiting", "stage"]
              },
              "reparto_tecnico": {
                "ruolo": "Reparto Tecnico",
                "persona": "Marco Rossi",
                "email": "marco.rossi@azienda.it",
                "ambiti": ["server", "cloud"]
              }
            }
            """;

    @BeforeEach
    void setUp() {
        mockModelClient = mock(ModelClient.class);
    }

    @Test
    @DisplayName("buildCandidateOptions mappa correttamente le chiavi del catalogo nelle lettere A, B, C...")
    void testBuildCandidateOptions() {
        List<DecisionPipelineStep.CandidateOption> options = DecisionPipelineStep.buildCandidateOptions(sampleRolesJson);
        assertEquals(3, options.size());

        assertEquals('A', options.get(0).letter());
        assertEquals("direzione", options.get(0).key());
        assertEquals("Direttore", options.get(0).role());
        assertEquals("Stefano Vena", options.get(0).person());

        assertEquals('B', options.get(1).letter());
        assertEquals("risorse_umane", options.get(1).key());
        assertEquals("Claudia Frammartino", options.get(1).person());

        assertEquals('C', options.get(2).letter());
        assertEquals("reparto_tecnico", options.get(2).key());
        assertEquals("Marco Rossi", options.get(2).person());
    }

    @Test
    @DisplayName("DecisionPipelineStep riceve single-token B e inietta deterministica associazione H.R. nel JSON")
    void testDecisionExecutionSuccess() throws Exception {
        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("B", 15L, null));

        DecisionPipelineStep step = new DecisionPipelineStep(
                "recipient_decision",
                mockModelClient,
                "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M",
                "company_roles",
                "destinatario",
                null
        );

        PipelineContext context = new PipelineContext("Invio candidatura per posizione junior developer", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);
        context.setModelOutput("""
                {
                  "mittente": "Alessandro Bianchi",
                  "email": "a.bianchi@gmail.com",
                  "oggetto": "Candidatura spontanea"
                }
                """);

        ValidationResult result = step.execute(context);
        assertTrue(result.isValid(), "Lo step decisionale deve risultare valido");

        JsonNode outputJson = mapper.readTree(context.getModelOutput());
        assertEquals("Alessandro Bianchi", outputJson.get("mittente").asText());
        assertEquals("Claudia Frammartino", outputJson.get("destinatario").asText());
        assertEquals("H.R.", outputJson.get("ruoloDestinatario").asText());
        assertEquals("claudia.frammartino@azienda.it", outputJson.get("emailDestinatario").asText());

        assertEquals("risorse_umane", context.getAttribute("decision.recipient_decision"));
        assertEquals("B", context.getAttribute("decision.recipient_decision.choice"));
    }

    @Test
    @DisplayName("DecisionPipelineStep gestisce risposte con prefisso o spazi come '  Choice: C '")
    void testDecisionPrefixedAnswer() throws Exception {
        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("Choice: C", 20L, null));

        DecisionPipelineStep step = new DecisionPipelineStep(
                "recipient_decision",
                mockModelClient,
                "reflex-model"
        );

        PipelineContext context = new PipelineContext("Problemi di rete e connettività server", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);

        ValidationResult result = step.execute(context);
        assertTrue(result.isValid());

        JsonNode outputJson = mapper.readTree(context.getModelOutput());
        assertEquals("Marco Rossi", outputJson.get("destinatario").asText());
        assertEquals("Reparto Tecnico", outputJson.get("ruoloDestinatario").asText());
    }

    @Test
    @DisplayName("DecisionPipelineStep restituisce errore se il modello genera un token fuori catalogo (es. 'Z')")
    void testDecisionInvalidTokenReturnsError() {
        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("Z", 10L, null));

        DecisionPipelineStep step = new DecisionPipelineStep(
                "decision_step",
                mockModelClient,
                "reflex-model"
        );

        PipelineContext context = new PipelineContext("Testo generico", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);

        ValidationResult result = step.execute(context);
        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.VALUE_NOT_IN_CATALOG, result.fieldErrors().get(0).code());
    }

    @Test
    @DisplayName("DecisionPipelineStep fallisce fast se la sorgente candidati è assente nel contesto")
    void testMissingCandidatesSource() {
        DecisionPipelineStep step = new DecisionPipelineStep(
                "decision_step",
                mockModelClient,
                "reflex-model"
        );

        PipelineContext context = new PipelineContext("Testo generico", DocumentType.EMAIL);
        // Nessun attributo company_roles impostato

        ValidationResult result = step.execute(context);
        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INFERENCE_ERROR, result.fieldErrors().get(0).code());
    }
}
