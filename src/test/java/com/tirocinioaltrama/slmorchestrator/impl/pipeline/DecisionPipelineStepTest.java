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
    @DisplayName("Fase A2: DecisionPipelineStep su token 'The' ritenta; se il retry restituisce token valido 'A', ha successo")
    void testDecisionInvalidTokenRetryRecovers() {
        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("The", 10L, null))
                .thenReturn(new ModelResponse("A", 10L, null));

        DecisionPipelineStep step = new DecisionPipelineStep(
                "decision_step",
                mockModelClient,
                "reflex-model"
        );

        PipelineContext context = new PipelineContext("Testo generico", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);

        ValidationResult result = step.execute(context);
        assertTrue(result.isValid(), "Il retry deve consentire il recupero e la validazione dello step");
        assertEquals("A", context.getAttribute("decision.decision_step.choice"));
        assertEquals("direzione", context.getAttribute("decision.decision_step"));
    }

    @Test
    @DisplayName("Fase A2: DecisionPipelineStep su token non ammesso ('The' o 'Z') ritenta; se persiste, restituisce INDETERMINATE")
    void testDecisionInvalidTokenReturnsIndeterminateAfterRetry() {
        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("The", 10L, null))
                .thenReturn(new ModelResponse("The", 10L, null));

        DecisionPipelineStep step = new DecisionPipelineStep(
                "decision_step",
                mockModelClient,
                "reflex-model"
        );

        PipelineContext context = new PipelineContext("Testo generico", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);

        ValidationResult result = step.execute(context);
        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.INDETERMINATE, result.fieldErrors().get(0).code());
        assertTrue(result.fieldErrors().get(0).message().contains("anche dopo retry"));
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

    @Test
    @DisplayName("Fase 4: Voto su k permutazioni con consenso supera la soglia e valida la scelta")
    void testVotingConsensusSucceeds() {
        // Mock che restituisce sempre l'opzione associata a 'risorse_umane' (H.R.)
        when(mockModelClient.generate(any(ModelRequest.class))).thenAnswer(inv -> {
            ModelRequest req = inv.getArgument(0);
            for (String line : req.userPrompt().split("\n")) {
                if (line.contains("H.R.") || line.contains("Claudia Frammartino")) {
                    return new ModelResponse(String.valueOf(line.trim().charAt(0)), 15L, null);
                }
            }
            return new ModelResponse("A", 15L, null);
        });

        DecisionPipelineStep step = new DecisionPipelineStep(
                "decision_step",
                mockModelClient,
                "reflex-model",
                "company_roles",
                "destinatario",
                null,
                true,
                new java.util.Random(42),
                3,     // k=3 permutazioni
                0.60   // soglia consenso 60%
        );

        PipelineContext context = new PipelineContext("Email per ferie", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);

        ValidationResult result = step.execute(context);
        assertTrue(result.isValid(), "Il voto su 3 permutazioni con accordo unanime deve essere valido");

        assertEquals(3, context.getAttribute("decision.decision_step.invocations"));
        assertNotNull(context.getAttribute("decision.decision_step.votes"));
        assertEquals(1.0, (Double) context.getAttribute("decision.decision_step.agreementRatio"), 0.001);
        assertFalse(context.getStepMetrics().isEmpty());
        assertEquals("DECISION_VOTING", context.getStepMetrics().get(0).type());
    }

    @Test
    @DisplayName("Fase 4: Voto su k permutazioni con disaccordo transita a INDETERMINATE")
    void testVotingDisagreementYieldsIndeterminate() {
        // Mock che restituisce ogni volta un candidato semantico diverso (direzione, risorse_umane, reparto_tecnico)
        final List<String> distinctRoles = List.of("Direttore", "H.R.", "Reparto Tecnico");
        final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        when(mockModelClient.generate(any(ModelRequest.class))).thenAnswer(inv -> {
            int c = counter.getAndIncrement();
            String targetRole = distinctRoles.get(c % distinctRoles.size());
            ModelRequest req = inv.getArgument(0);
            for (String line : req.userPrompt().split("\n")) {
                if (line.contains(targetRole)) {
                    return new ModelResponse(String.valueOf(line.trim().charAt(0)), 15L, null);
                }
            }
            return new ModelResponse("A", 15L, null);
        });

        DecisionPipelineStep step = new DecisionPipelineStep(
                "decision_step",
                mockModelClient,
                "reflex-model",
                "company_roles",
                "destinatario",
                null,
                true,
                new java.util.Random(123),
                3,     // k=3
                0.60   // soglia 60% (1/3 = 33.3% < 60%)
        );

        PipelineContext context = new PipelineContext("Email ambigua tra reparti", DocumentType.EMAIL);
        context.setAttribute("company_roles", sampleRolesJson);

        ValidationResult result = step.execute(context);
        assertFalse(result.isValid(), "Senza consenso sufficiente il passo deve restituire esito non valido");
        assertEquals(1, result.fieldErrors().size());
        assertEquals(ValidationErrorCode.INDETERMINATE, result.fieldErrors().get(0).code());
        assertTrue(result.fieldErrors().get(0).message().contains("accordo insufficiente"));
        assertEquals(3, context.getAttribute("decision.decision_step.invocations"));
        assertEquals(1.0 / 3.0, (Double) context.getAttribute("decision.decision_step.agreementRatio"), 0.01);
    }
}
