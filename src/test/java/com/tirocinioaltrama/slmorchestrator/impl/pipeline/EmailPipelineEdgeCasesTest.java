package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;
import com.tirocinioaltrama.slmorchestrator.impl.tool.CompanyRolesTool;

class EmailPipelineEdgeCasesTest {

    private YamlPipelineLoader loader;
    private ToolRegistry toolRegistry;
    private ValidatorRegistry validatorRegistry;
    private ModelClient mockModelClient;
    private ObjectMapper objectMapper;

    private static final String VALID_STEP1_OUTPUT = """
            {
              "mittente": "Mario Bianchi",
              "email": "mario.bianchi@partner.it",
              "telefono": "+39 02 123456",
              "azienda": "Partner Srl",
              "oggetto": "Segnalazione malfunzionamento router di rete",
              "citta": "Milano"
            }
            """;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ollama.model", "default-model");
        env.setProperty("ollama.email-model", "email-slm-worker");
        env.setProperty("ollama.invoice-model", "invoice-worker");
        env.setProperty("ollama.anagrafica-model", "anagrafica-worker");

        validatorRegistry = new ValidatorRegistry();
        toolRegistry = new ToolRegistry();
        PipelineConfigValidator configValidator = new PipelineConfigValidator(validatorRegistry, toolRegistry, new DefaultResourceLoader());
        loader = new YamlPipelineLoader(env, validatorRegistry, configValidator, toolRegistry);
        mockModelClient = mock(ModelClient.class);
        objectMapper = new ObjectMapper();
    }

    private Pipeline getEmailPipeline() {
        List<Pipeline> pipelines = loader.loadPipelines(mockModelClient);
        return pipelines.stream()
                .filter(p -> p.getSupportedType() == DocumentType.EMAIL)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pipeline EMAIL non trovata"));
    }

    @Test
    @DisplayName("Edge Case: Fallimento del Tool dei ruoli preserva l'output del passo 1 e transita a INDETERMINATE")
    void testToolExecutionFailure_gracefulFallbackToIndeterminate() {
        // Registra un mock tool guasto che ritorna failure
        Tool failingTool = mock(Tool.class);
        when(failingTool.getName()).thenReturn(CompanyRolesTool.TOOL_NAME);
        when(failingTool.execute(any())).thenReturn(ToolResult.failure("Database organigramma non raggiungibile"));
        toolRegistry.register(failingTool);

        Pipeline pipeline = getEmailPipeline();

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100));

        PipelineContext context = new PipelineContext("Test payload", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertFalse(result.success(), "Il fallimento del tool non deve dare esito positivo");
        assertNotNull(result.unvalidatedOutput(), "L'output del passo 1 deve essere preservato in unvalidatedOutput");
        assertTrue(result.unvalidatedOutput().contains("Mario Bianchi"), "Deve mantenere i dati estratti prima del fallimento");
        assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("Database organigramma non raggiungibile")));
    }

    @Test
    @DisplayName("Edge Case: Tool solleva eccezione imprevista durante l'esecuzione")
    void testToolThrowsUnexpectedException() {
        Tool explodingTool = mock(Tool.class);
        when(explodingTool.getName()).thenReturn(CompanyRolesTool.TOOL_NAME);
        when(explodingTool.execute(any())).thenThrow(new RuntimeException("Crash imprevisto nel filesystem"));
        toolRegistry.register(explodingTool);

        Pipeline pipeline = getEmailPipeline();

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100));

        PipelineContext context = new PipelineContext("Test payload", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertNotNull(result.unvalidatedOutput());
        assertTrue(result.unvalidatedOutput().contains("mario.bianchi@partner.it"));
        assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("Crash imprevisto")));
    }

    @Test
    @DisplayName("Edge Case: CompanyRolesTool inizializzato con risorsa mancante restituisce ToolResult.failure")
    void testCompanyRolesToolWithMissingResource() {
        CompanyRolesTool brokenTool = new CompanyRolesTool("mock/non_existent_file.json");
        assertNull(brokenTool.getRolesRoot());
        ToolResult result = brokenTool.execute(Map.of());

        assertFalse(result.success());
        assertTrue(result.message().contains("non disponibile o vuota"));
    }

    @Test
    @DisplayName("Edge Case: Invocazione SLM al passo 2 solleva eccezione di rete/timeout")
    void testRecipientInferenceModelThrowsException() {
        Pipeline pipeline = getEmailPipeline();

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenThrow(new RuntimeException("Connessione con Ollama interrotta (Read Timeout)"));

        PipelineContext context = new PipelineContext("Email testo", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertNotNull(result.unvalidatedOutput());
        assertTrue(result.unvalidatedOutput().contains("mario.bianchi@partner.it"));
        assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("Connessione con Ollama interrotta")));
    }

    @Test
    @DisplayName("Edge Case: Invocazione SLM al passo 2 restituisce risposta vuota o blank")
    void testRecipientInferenceReturnsEmptyString() {
        Pipeline pipeline = getEmailPipeline();

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse("", 10));

        PipelineContext context = new PipelineContext("Email testo", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertNotNull(result.unvalidatedOutput());
        assertTrue(result.unvalidatedOutput().contains("Mario Bianchi"));
    }

    @Test
    @DisplayName("Edge Case: Risposta SLM tronco/malformato al passo 2 innesca retry e si riprende al 2° tentativo")
    void testRecipientInferenceMalformedJsonRecoversOnRetry() throws Exception {
        Pipeline pipeline = getEmailPipeline();

        String malformedJson = "{\"destinatario\": \"Stefano Vena\", \"ruoloDestinatario\":"; // JSON tronco
        String recoveredJson = """
                {
                  "destinatario": "Stefano Vena",
                  "ruoloDestinatario": "Direttore",
                  "motivoInstradamento": "Bozza contratto quadro e approvazione"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100)) // Step 1 OK
                .thenReturn(new ModelResponse(malformedJson, 50))       // Step 4 (Tentativo 1) Malformato
                .thenReturn(new ModelResponse(recoveredJson, 75));      // Step 4 (Retry 1) Valido

        PipelineContext context = new PipelineContext("Testo contratto", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success(), "La pipeline deve recuperare con successo grazie al retry");
        JsonNode root = objectMapper.readTree(result.output());
        assertEquals("Mario Bianchi", root.get("mittente").asText());
        assertEquals("Stefano Vena", root.get("destinatario").asText());
        assertEquals("Direttore", root.get("ruoloDestinatario").asText());

        // Verifichiamo che il ModelClient sia stato invocato 3 volte:
        // 1 per email_inference, 1 per il primo tentativo recipient_inference, 1 per il retry
        verify(mockModelClient, times(3)).generate(any(ModelRequest.class));
    }

    @Test
    @DisplayName("Edge Case: Risposta persistentemente malformata al passo 2 esaurisce i retry e va in INDETERMINATE")
    void testRecipientInferenceMalformedJsonExhaustsRetries() {
        Pipeline pipeline = getEmailPipeline();

        String malformedJson = "{\"destinatario\": NON_JSON";

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse(malformedJson, 50))  // Tentativo iniziale
                .thenReturn(new ModelResponse(malformedJson, 50))  // Retry 1
                .thenReturn(new ModelResponse(malformedJson, 50)); // Retry 2 (max_retries: 2)

        PipelineContext context = new PipelineContext("Testo", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertFalse(result.success());
        assertNotNull(result.unvalidatedOutput());
        assertTrue(result.unvalidatedOutput().contains("NON_JSON"));
        // L'estrazione pulita del passo 1 rimane salvata negli attributi di contesto
        assertEquals(VALID_STEP1_OUTPUT, context.getAttribute("email.extraction_output"));
    }

    @Test
    @DisplayName("Edge Case: Tentativo di sovrascrittura avversariale non modifica i dati estratti al passo 1")
    void testAdversarialRecipientInferenceCannotOverwriteSenderData() throws Exception {
        Pipeline pipeline = getEmailPipeline();

        String adversarialJson = """
                {
                  "mittente": "ATTACKER_OVERWRITE",
                  "email": "hacked@evil.com",
                  "destinatario": "Claudia Frammartino",
                  "ruoloDestinatario": "H.R.",
                  "motivoInstradamento": "Iniezione malevola per sovrascrivere il mittente"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse(adversarialJson, 80));

        PipelineContext context = new PipelineContext("Testo sicuro", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());

        // Integrità dei dati: mittente ed email originali NON devono essere sovrascritti
        assertEquals("Mario Bianchi", root.get("mittente").asText());
        assertEquals("mario.bianchi@partner.it", root.get("email").asText());

        // Il nuovo campo destinatario deve invece essere integrato
        assertEquals("Claudia Frammartino", root.get("destinatario").asText());
        assertEquals("H.R.", root.get("ruoloDestinatario").asText());
    }

    @Test
    @DisplayName("Edge Case: Contesto non associabile (campi destinatario nulli) viene accettato correttamente dallo schema")
    void testRecipientInferenceWithNullValuesAccepted() throws Exception {
        Pipeline pipeline = getEmailPipeline();

        String nullRecipientJson = """
                {
                  "destinatario": null,
                  "ruoloDestinatario": null,
                  "motivoInstradamento": "Email generica senza riferimento a ruoli aziendali"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse(nullRecipientJson, 60));

        PipelineContext context = new PipelineContext("Newsletter spam generico", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());
        assertTrue(root.get("destinatario").isNull());
        assertTrue(root.get("ruoloDestinatario").isNull());
        assertEquals("Email generica senza riferimento a ruoli aziendali", root.get("motivoInstradamento").asText());
        assertEquals("Mario Bianchi", root.get("mittente").asText());
    }

    @Test
    @DisplayName("Edge Case: Instradamento semantico verso Developers (Alessandro Ferrari / Matteo Gallo)")
    void testRecipientInferenceDevelopersRole() throws Exception {
        Pipeline pipeline = getEmailPipeline();

        String developersJson = """
                {
                  "destinatario": "Alessandro Ferrari",
                  "ruoloDestinatario": "Developers",
                  "motivoInstradamento": "Richiesta review codice per pull request API"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse(developersJson, 80));

        PipelineContext context = new PipelineContext("Pull request open per endpoint REST", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());
        assertEquals("Alessandro Ferrari", root.get("destinatario").asText());
        assertEquals("Developers", root.get("ruoloDestinatario").asText());
    }

    @Test
    @DisplayName("Edge Case: Instradamento semantico verso Reparto Tecnico (Marco Rossi)")
    void testRecipientInferenceRepartoTecnicoRole() throws Exception {
        Pipeline pipeline = getEmailPipeline();

        String tecnicoJson = """
                {
                  "destinatario": "Marco Rossi",
                  "ruoloDestinatario": "Reparto Tecnico",
                  "motivoInstradamento": "Guasto infrastrutturale switch di sala server"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse(tecnicoJson, 85));

        PipelineContext context = new PipelineContext("Switch down in sala server", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());
        assertEquals("Marco Rossi", root.get("destinatario").asText());
        assertEquals("Reparto Tecnico", root.get("ruoloDestinatario").asText());
    }

    @Test
    @DisplayName("Verifica Strutturale Prompt: accerta che <company_roles> ed <extracted_info> siano iniettati nella richiesta SLM")
    void testPromptStructureInjectionVerification() {
        Pipeline pipeline = getEmailPipeline();

        String recipientJson = """
                {
                  "destinatario": "Claudia Frammartino",
                  "ruoloDestinatario": "H.R.",
                  "motivoInstradamento": "Candidatura"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(VALID_STEP1_OUTPUT, 100))
                .thenReturn(new ModelResponse(recipientJson, 70));

        PipelineContext context = new PipelineContext("Corpo email per test", DocumentType.EMAIL);
        pipeline.execute(context);

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(mockModelClient, times(2)).generate(captor.capture());

        List<ModelRequest> requests = captor.getAllValues();
        ModelRequest step1Request = requests.get(0);
        ModelRequest step2Request = requests.get(1);

        // Step 1: non deve avere company_roles né extracted_info
        assertFalse(step1Request.userPrompt().contains("<company_roles>"));
        assertFalse(step1Request.userPrompt().contains("<extracted_info>"));

        // Step 2: DEVE contenere il blocco dei ruoli dal Tool e l'output estratto dello Step 1
        assertTrue(step2Request.userPrompt().contains("<company_roles>"));
        assertTrue(step2Request.userPrompt().contains("Stefano Vena"));
        assertTrue(step2Request.userPrompt().contains("Claudia Frammartino"));
        assertTrue(step2Request.userPrompt().contains("<extracted_info>"));
        assertTrue(step2Request.userPrompt().contains("Mario Bianchi"));
        assertTrue(step2Request.userPrompt().contains("mario.bianchi@partner.it"));

        // Step 2: System prompt deve contenere le istruzioni del prompt di recipient
        assertNotNull(step2Request.systemPrompt());
        assertTrue(step2Request.systemPrompt().contains("destinatario"));
    }

    @Test
    @DisplayName("Edge Case: Step 1 restituisce stringa vuota o whitespace per destinatario, Step 2 lo sovrascrive correttamente")
    void testStep1ReturnsBlankStringForDestinatario_step2OverwritesCorrectly() throws Exception {
        Pipeline pipeline = getEmailPipeline();

        String step1WithBlankRecipient = """
                {
                  "mittente": "Chiara Gialli",
                  "email": "chiara.gialli@azienda.com",
                  "destinatario": "",
                  "ruoloDestinatario": "   ",
                  "motivoInstradamento": null
                }
                """;

        String step2Recipient = """
                {
                  "destinatario": "Stefano Vena",
                  "ruoloDestinatario": "Direttore",
                  "motivoInstradamento": "Richiesta approvazione budget annuale"
                }
                """;

        when(mockModelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(step1WithBlankRecipient, 100))
                .thenReturn(new ModelResponse(step2Recipient, 90));

        PipelineContext context = new PipelineContext("Payload budget", DocumentType.EMAIL);
        PipelineResult result = pipeline.execute(context);

        assertTrue(result.success());
        JsonNode root = objectMapper.readTree(result.output());

        // Mittente preservato
        assertEquals("Chiara Gialli", root.get("mittente").asText());
        assertEquals("chiara.gialli@azienda.com", root.get("email").asText());

        // Destinatario sovrascritto e popolato dal passo dedicato
        assertEquals("Stefano Vena", root.get("destinatario").asText());
        assertEquals("Direttore", root.get("ruoloDestinatario").asText());
        assertEquals("Richiesta approvazione budget annuale", root.get("motivoInstradamento").asText());
    }

    @Test
    @DisplayName("Edge Case: ToolPipelineStep gestisce contesto nullo senza lanciare NullPointerException")
    void testToolPipelineStepHandlesNullContextGracefully() {
        ToolPipelineStep step = new ToolPipelineStep("roles_lookup", CompanyRolesTool.TOOL_NAME, toolRegistry);
        var result = step.execute(null);
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("PipelineContext non può essere nullo")));
    }

    @Test
    @DisplayName("Edge Case: CompanyRolesTool query variants (args null, empty, query, role)")
    void testCompanyRolesToolQueryVariants() {
        CompanyRolesTool tool = new CompanyRolesTool();
        assertNotNull(tool.getRolesJson());
        assertNotNull(tool.getRolesRoot());

        // Argomenti null
        ToolResult r1 = tool.execute(null);
        assertTrue(r1.success());
        assertNotNull(r1.data());

        // Argomenti vuoti
        ToolResult r2 = tool.execute(Map.of());
        assertTrue(r2.success());

        // Argomento con 'query'
        ToolResult r3 = tool.execute(Map.of("query", "developers"));
        assertTrue(r3.success());
        assertTrue(r3.message().contains("developers"));

        // Argomento con 'role'
        ToolResult r4 = tool.execute(Map.of("role", "H.R."));
        assertTrue(r4.success());
        assertTrue(r4.message().contains("h.r."));
    }

    @Test
    @DisplayName("Edge Case: CompanyRolesTool con file JSON malformato non crasha e ritorna failure")
    void testCompanyRolesToolMalformedJsonHandling() throws Exception {
        // Creiamo un file temporaneo con JSON sintatticamente errato
        java.io.File tempFile = java.io.File.createTempFile("malformed_roles", ".json");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), "{ not a valid json ::: ");

        // Usiamo il costruttore passando il path assoluto
        CompanyRolesTool brokenTool = new CompanyRolesTool("file:" + tempFile.getAbsolutePath());
        assertNull(brokenTool.getRolesRoot());

        ToolResult result = brokenTool.execute(Map.of());
        assertFalse(result.success());
        assertTrue(result.message().contains("non disponibile o vuota"));
    }
}
