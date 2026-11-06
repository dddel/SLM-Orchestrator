package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.BasePipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;

class EmailPipelineToolCallingTest {

    private YamlPipelineLoader loader;
    private ModelClient mockModelClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setProperty("ollama.model", "default-model");
        env.setProperty("ollama.email-model", "email-worker-model");
        env.setProperty("ollama.invoice-model", "invoice-worker-model");
        env.setProperty("ollama.anagrafica-model", "anagrafica-worker-model");
        ValidatorRegistry validatorRegistry = new ValidatorRegistry();
        ToolRegistry toolRegistry = new ToolRegistry();
        PipelineConfigValidator configValidator = new PipelineConfigValidator(validatorRegistry, toolRegistry, new DefaultResourceLoader());
        loader = new YamlPipelineLoader(env, validatorRegistry, configValidator, toolRegistry);
        mockModelClient = mock(ModelClient.class);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("La pipeline EMAIL carica correttamente tutti gli step incluso il tool roles e l'inferenza destinatario")
    void testEmailPipelineLoading() {
        List<Pipeline> pipelines = loader.loadPipelines(mockModelClient);
        Pipeline emailPipeline = pipelines.stream()
                .filter(p -> p.getSupportedType() == DocumentType.EMAIL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pipeline EMAIL non trovata"));

        assertNotNull(emailPipeline);
        assertTrue(emailPipeline instanceof BasePipeline);
    }

    @Test
    @DisplayName("EmailPipeline esegue estrazione mittente, consultazione ruoli e associazione destinatario HR")
    void testEmailPipelineCandidaturaHrAssociation() throws Exception {
        List<Pipeline> pipelines = loader.loadPipelines(mockModelClient);
        Pipeline emailPipeline = pipelines.stream()
                .filter(p -> p.getSupportedType() == DocumentType.EMAIL)
                .findFirst()
                .orElseThrow();

        // 1° invocazione (email_inference): estrae mittente e dati email
        String emailExtractionJson = """
                {
                  "mittente": "Chiara Neri",
                  "email": "chiara.neri@gmail.com",
                  "telefono": null,
                  "azienda": null,
                  "oggetto": "Candidatura spontanea e CV allegato",
                  "citta": "Milano",
                  "numeroPartecipanti": null,
                  "budgetEuro": null,
                  "dataEvento": null
                }
                """;

        // 2° invocazione (recipient_inference): associa a Claudia Frammartino (H.R.)
        String recipientAssociationJson = """
                {
                  "destinatario": "Claudia Frammartino",
                  "ruoloDestinatario": "H.R.",
                  "motivoInstradamento": "Candidatura lavorativa o invio curriculum"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(emailExtractionJson, 120))
                .thenReturn(new ModelResponse(recipientAssociationJson, 95));

        String emailPayload = """
                Gentile azienda,
                Invio in allegato la mia candidatura per la posizione aperta.
                In fede,
                Chiara Neri
                chiara.neri@gmail.com
                """;

        PipelineContext context = new PipelineContext(emailPayload, DocumentType.EMAIL);
        PipelineResult result = emailPipeline.execute(context);

        assertTrue(result.success(), "La pipeline deve completare con successo");
        assertNotNull(result.output());

        JsonNode root = objectMapper.readTree(result.output());
        // Verifica mittente estratto al passo 1
        assertEquals("Chiara Neri", root.get("mittente").asText());
        assertEquals("chiara.neri@gmail.com", root.get("email").asText());

        // Verifica destinatario associato al passo 2 con directory ruoli
        assertEquals("Claudia Frammartino", root.get("destinatario").asText());
        assertEquals("H.R.", root.get("ruoloDestinatario").asText());
        assertEquals("Candidatura lavorativa o invio curriculum", root.get("motivoInstradamento").asText());

        // Verifica che il modello sia stato invocato esattamente 2 volte (estrazione + destinatario)
        verify(mockModelClient, times(2)).generate(any(ModelRequest.class));
    }

    @Test
    @DisplayName("EmailPipeline associa correttamente segnalazione bug a Testers (Luca Bianchi)")
    void testEmailPipelineBugReportTestersAssociation() throws Exception {
        List<Pipeline> pipelines = loader.loadPipelines(mockModelClient);
        Pipeline emailPipeline = pipelines.stream()
                .filter(p -> p.getSupportedType() == DocumentType.EMAIL)
                .findFirst()
                .orElseThrow();

        String emailExtractionJson = """
                {
                  "mittente": "Paolo Bianchi",
                  "email": "paolo.b@client.com",
                  "telefono": null,
                  "azienda": "Acme Corp",
                  "oggetto": "Segnalazione bug bloccante in produzione",
                  "citta": null,
                  "numeroPartecipanti": null,
                  "budgetEuro": null,
                  "dataEvento": null
                }
                """;

        String recipientAssociationJson = """
                {
                  "destinatario": "Luca Bianchi",
                  "ruoloDestinatario": "Testers",
                  "motivoInstradamento": "Segnalazione anomalia software o bug"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(emailExtractionJson, 110))
                .thenReturn(new ModelResponse(recipientAssociationJson, 85));

        PipelineContext context = new PipelineContext("Bug riscontrato!", DocumentType.EMAIL);
        PipelineResult result = emailPipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());
        assertEquals("Paolo Bianchi", root.get("mittente").asText());
        assertEquals("Luca Bianchi", root.get("destinatario").asText());
        assertEquals("Testers", root.get("ruoloDestinatario").asText());
    }

    @Test
    @DisplayName("EmailPipeline associa accordo contrattuale alla Direzione (Stefano Vena)")
    void testEmailPipelineDirezioneAssociation() throws Exception {
        List<Pipeline> pipelines = loader.loadPipelines(mockModelClient);
        Pipeline emailPipeline = pipelines.stream()
                .filter(p -> p.getSupportedType() == DocumentType.EMAIL)
                .findFirst()
                .orElseThrow();

        String emailExtractionJson = """
                {
                  "mittente": "Avv. Ferrari",
                  "email": "ferrari@legalpartners.it",
                  "telefono": null,
                  "azienda": "Legal Partners",
                  "oggetto": "Bozza accordo quadro e partnership strategica",
                  "citta": "Roma",
                  "numeroPartecipanti": null,
                  "budgetEuro": null,
                  "dataEvento": null
                }
                """;

        String recipientAssociationJson = """
                {
                  "destinatario": "Stefano Vena",
                  "ruoloDestinatario": "Direttore",
                  "motivoInstradamento": "Accordo contrattuale o decisione direzionale"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(emailExtractionJson, 100))
                .thenReturn(new ModelResponse(recipientAssociationJson, 80));

        PipelineContext context = new PipelineContext("Bozza contrattuale", DocumentType.EMAIL);
        PipelineResult result = emailPipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());
        assertEquals("Stefano Vena", root.get("destinatario").asText());
        assertEquals("Direttore", root.get("ruoloDestinatario").asText());
    }
}
