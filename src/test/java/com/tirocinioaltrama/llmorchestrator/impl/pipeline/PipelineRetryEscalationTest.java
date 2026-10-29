package com.tirocinioaltrama.llmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorResponse;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorStatus;
import com.tirocinioaltrama.llmorchestrator.core.Router;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineRegistry;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.llmorchestrator.impl.DefaultOrchestrator;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.PipelineDefinition;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.StepDefinition;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.StepDefinition.FieldValidatorDefinition;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.YamlPipelineLoader;

/**
 * Suite di test per il flusso completo di recupero RF7:
 * 1. Successo al primo colpo (nessun retry, sampling 0.0 standard).
 * 2. Successo al primo retry con feedback iniettato e sampling modificato.
 * 3. Recupero da JSON sintatticamente non valido / troncato al primo tentativo.
 * 4. Escalation: retry SLM esauriti, successo sul modello escalato.
 * 5. Indeterminate: retry SLM ed escalation esauriti -> status INDETERMINATE, result null, unvalidatedResult popolato.
 * 6. Whitelist anti-injection: payload con caratteri malevoli nel valore non iniettato nel feedback.
 * 7. Isolamento tag <feedback> da <payload>.
 */
class PipelineRetryEscalationTest {

    private ModelClient modelClient;
    private Router router;
    private ValidatorRegistry validatorRegistry;
    private PipelineConfigValidator configValidator;
    private MockEnvironment env;

    @BeforeEach
    void setUp() {
        modelClient = mock(ModelClient.class);
        router = mock(Router.class);
        validatorRegistry = new ValidatorRegistry();
        configValidator = new PipelineConfigValidator(validatorRegistry, new com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry(), new DefaultResourceLoader());
        env = new MockEnvironment();
        env.setProperty("ollama.model", "generic-slm");
        env.setProperty("ollama.email-model", "email-slm");
        env.setProperty("ollama.invoice-model", "invoice-slm");
        env.setProperty("ollama.anagrafica-model", "anagrafica-slm");
    }

    private DefaultOrchestrator createOrchestratorWithPipeline(PipelineDefinition def) {
        YamlPipelineLoader loader = new YamlPipelineLoader(env, validatorRegistry, configValidator) {
            @Override
            public List<PipelineDefinition> loadDefinitions() {
                return List.of(def);
            }
        };
        PipelineRegistry registry = new PipelineRegistry(loader.loadPipelines(modelClient));
        return new DefaultOrchestrator(router, registry, new com.tirocinioaltrama.llmorchestrator.impl.tool.ActionExecutor(new com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry()));
    }

    @Test
    @DisplayName("RF7: Successo al primo colpo senza retry")
    void testSuccessoAlPrimoTentativo() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 2, "feedback", true));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"mittente\":\"Mario\",\"email\":\"mario@example.com\"}", 50));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contattami a mario@example.com"));

        assertEquals(OrchestratorStatus.OK, resp.status());
        assertNotNull(resp.result());
        assertNull(resp.unvalidatedResult());
        verify(modelClient, times(1)).generate(any(ModelRequest.class));
    }

    @Test
    @DisplayName("RF7: Successo al primo retry dopo errore checksum su fattura")
    void testSuccessoAlPrimoRetryConFeedback() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("fattura_pipe");
        def.setDocumentType("FATTURA");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("invoice-slm");
        inf.setSchema("schemas/fattura_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(
                new FieldValidatorDefinition("fornitore.partitaIva", "partita_iva")
        ));
        val.setUnvalidated(List.of("cliente.codiceFiscale", "iban"));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 2, "feedback", true));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // 1° tentativo: P.IVA con checksum errato (12345678901 ha check digit non corrispondente)
        // 2° tentativo: P.IVA corretta (06363391001)
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"numeroFattura\":\"F-1\",\"dataFattura\":null,\"importoTotale\":100.0,\"fornitore\":{\"denominazione\":\"Tech Srl\",\"partitaIva\":\"12345678901\"},\"cliente\":null,\"iban\":null}", 50))
                .thenReturn(new ModelResponse("{\"numeroFattura\":\"F-1\",\"dataFattura\":null,\"importoTotale\":100.0,\"fornitore\":{\"denominazione\":\"Tech Srl\",\"partitaIva\":\"06363391001\"},\"cliente\":null,\"iban\":null}", 50));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.FATTURA, "Fattura Tech Srl"));

        assertEquals(OrchestratorStatus.OK, resp.status());
        assertNotNull(resp.result());
        assertTrue(resp.result().contains("06363391001"));

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelClient, times(2)).generate(captor.capture());

        List<ModelRequest> requests = captor.getAllValues();
        ModelRequest firstReq = requests.get(0);
        ModelRequest retryReq = requests.get(1);

        // Primo tentativo: nessun tag <feedback>
        assertFalse(firstReq.userPrompt().contains("<feedback>"));
        assertNull(firstReq.options());

        // Retry: feedback presente con messaggio su fornitore.partitaIva senza valore rifiutato
        assertTrue(retryReq.userPrompt().contains("<feedback>"));
        assertTrue(retryReq.userPrompt().contains("fornitore.partitaIva"));
        assertTrue(retryReq.userPrompt().contains("</feedback>"));
        assertTrue(retryReq.userPrompt().contains("<payload>"));

        // Sampling variato al primo retry: temperature 0.2
        assertNotNull(retryReq.options());
        assertEquals(0.2, (Double) retryReq.options().get("temperature"), 0.001);
    }

    @Test
    @DisplayName("RF7: Recupero da JSON non valido / troncato al primo tentativo")
    void testRecuperoDaJsonNonValido() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 1, "feedback", true));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // 1° tentativo: JSON troncato
        // 2° tentativo: JSON valido
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse("{\"mittente\":\"Mario\", \"email\":", 40))
                .thenReturn(new ModelResponse("{\"mittente\":\"Mario\",\"email\":\"mario@test.it\"}", 45));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Test"));

        assertEquals(OrchestratorStatus.OK, resp.status());
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelClient, times(2)).generate(captor.capture());

        // Il feedback deve contenere indicazione del fallimento sintattico JSON
        assertTrue(captor.getAllValues().get(1).userPrompt().contains("l'output non è un JSON valido o risulta troncato"));
    }

    @Test
    @DisplayName("RF7: Escalation su modello dedicato dopo esaurimento dei retry SLM")
    void testEscalationDopoEsaurimentoRetry() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("anagrafica_pipe");
        def.setDocumentType("ANAGRAFICA");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("anagrafica-slm");
        inf.setSchema("schemas/anagrafica_documento.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("codiceFiscale", "codice_fiscale")));
        val.setOnFailure(Map.of(
                "retry_step", "inf",
                "max_retries", 1, // 1 tentativo iniziale + 1 retry = 2 su anagrafica-slm
                "feedback", true,
                "escalate_to", "qwen2.5:7b" // 1 tentativo escalation
        ));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // 1°: CF con carattere errato (anagrafica-slm)
        // 2°: CF ancora errato (anagrafica-slm retry)
        // 3°: CF corretto (qwen2.5:7b escalation)
        String template = "{\"tipoDocumento\":\"CARTA_IDENTITA\",\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"codiceFiscale\":\"%s\",\"dataNascita\":null,\"luogoNascita\":null,\"sesso\":null,\"numeroDocumento\":null,\"dataRilascio\":null,\"dataScadenza\":null,\"enteRilascio\":null}";
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(template.formatted("RSSMRA85C12D086A"), 50))
                .thenReturn(new ModelResponse(template.formatted("RSSMRA85C12D086A"), 50))
                .thenReturn(new ModelResponse(template.formatted("RSSMRA85C12D086Q"), 150));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.ANAGRAFICA, "Mario Rossi CIE"));

        assertEquals(OrchestratorStatus.OK, resp.status());
        assertNotNull(resp.result());

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(modelClient, times(3)).generate(captor.capture());

        List<ModelRequest> requests = captor.getAllValues();
        assertEquals("anagrafica-slm", requests.get(0).model());
        assertEquals("anagrafica-slm", requests.get(1).model());
        assertEquals("qwen2.5:7b", requests.get(2).model()); // Invocato modello escalato
    }

    @Test
    @DisplayName("RF7: Transizione a INDETERMINATE quando falliscono sia retry che escalation")
    void testTransizioneAIndeterminate() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of(
                "retry_step", "inf",
                "max_retries", 1,
                "escalate_to", "qwen2.5:7b"
        ));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // Tutti e 3 i tentativi restituiscono un'email non valida
        String invalidOutput = "{\"mittente\":\"Mario\",\"email\":\"email_non_valida_senza_chiocciola\"}";
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenReturn(new ModelResponse(invalidOutput, 30));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contatto"));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNull(resp.result());
        assertEquals(invalidOutput, resp.unvalidatedResult()); // Preservato output per revisione operatore
        assertNotNull(resp.errors());
        assertFalse(resp.errors().isEmpty());
        assertTrue(resp.errors().get(0).message().toLowerCase().contains("email")
                || resp.errors().get(0).path().contains("email"));
    }

    @Test
    @DisplayName("RF7: Nessun valore rifiutato nel feedback (zero superficie di injection)")
    void testZeroSuperficieInjectionNelFeedback() {
        com.tirocinioaltrama.llmorchestrator.core.validation.FieldError err =
                new com.tirocinioaltrama.llmorchestrator.core.validation.FieldError(
                        "iban",
                        "iban",
                        com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.CHECKSUM_INVALID,
                        "Checksum non valido"
                );

        String feedback = ValidationFeedbackBuilder.buildFeedbackMessage(List.of(err));
        assertTrue(feedback.contains("Campo 'iban'"));
        assertTrue(feedback.contains("checksum"));
        // Nessun valore numerico o frammento è presente
        assertFalse(feedback.contains("Valore ricevuto"));
        assertFalse(feedback.contains("Valore rifiutato"));
    }

    @Test
    @DisplayName("D1 (a): Fallimento dell'escalation dopo retry con errori di validazione: preserva errori e aggiunge INFERENCE_ERROR")
    void testFallimentoEscalationPreservaErroriValidazionePrecedenti() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of(
                "retry_step", "inf",
                "max_retries", 1,
                "escalate_to", "qwen2.5:7b"
        ));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        String invalidOutput = "{\"mittente\":\"Mario\",\"email\":\"senza_chiocciola\"}";
        // Tentativo 1 (SLM): output non valido -> errore di validazione FORMAT_INVALID
        // Tentativo 2 (Retry SLM): output non valido -> errore di validazione FORMAT_INVALID
        // Tentativo 3 (Escalation a qwen2.5:7b): eccezione (modello non trovato)
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenThrow(new RuntimeException("Modello 'qwen2.5:7b' non trovato nel demone Ollama"));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contatto"));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNull(resp.result());
        assertEquals(invalidOutput, resp.unvalidatedResult());
        assertNotNull(resp.errors());

        // Gli errori devono essere esattamente 2: prima FORMAT_INVALID, poi INFERENCE_ERROR
        assertEquals(2, resp.errors().size(), "Gli errori devono contenere sia la validazione precedente sia INFERENCE_ERROR: " + resp.errors());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, resp.errors().get(0).code());
        assertEquals("email", resp.errors().get(0).path());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, resp.errors().get(1).code());
    }

    @Test
    @DisplayName("D1 (b): Fallimento durante un retry con errori di validazione: preserva errori e aggiunge INFERENCE_ERROR")
    void testFallimentoDuranteRetryPreservaErroriValidazionePrecedenti() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of(
                "retry_step", "inf",
                "max_retries", 1
        ));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        String invalidOutput = "{\"mittente\":\"Mario\",\"email\":\"senza_chiocciola\"}";
        // Tentativo 1 (SLM): output non valido -> errore FORMAT_INVALID
        // Tentativo 2 (Retry SLM): fallimento inferenza (es. timeout / errore demone)
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenThrow(new RuntimeException("Demone Ollama non raggiungibile"));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contatto"));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNull(resp.result());
        assertEquals(invalidOutput, resp.unvalidatedResult());
        assertNotNull(resp.errors());

        assertEquals(2, resp.errors().size(), "Gli errori devono contenere sia FORMAT_INVALID sia INFERENCE_ERROR: " + resp.errors());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, resp.errors().get(0).code());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, resp.errors().get(1).code());
    }

    @Test
    @DisplayName("D1 (c): Fallimento al primo tentativo: resta errore infrastrutturale (nessun output)")
    void testFallimentoAlPrimoTentativoInfrastrutturale() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 1));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // Fallimento secco al primo colpo
        when(modelClient.generate(any(ModelRequest.class)))
                .thenThrow(new RuntimeException("Connessione rifiutata"));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contatto"));

        // Primo tentativo senza output -> status INDETERMINATE con 1 solo errore (infrastrutturale/INFERENCE_ERROR)
        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNull(resp.result());
        assertNull(resp.unvalidatedResult());
        assertEquals(1, resp.errors().size());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, resp.errors().get(0).code());
    }

    @Test
    @DisplayName("D1 (d): Scadenza del budget temporale dopo un tentativo non valido: preserva errori e aggiunge errore timeout")
    void testScadenzaBudgetPreservaErroriValidazionePrecedenti() {
        env.setProperty("orchestrator.pipeline.max-duration-ms", "10"); // budget bassissimo

        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 2));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        String invalidOutput = "{\"mittente\":\"Mario\",\"email\":\"senza_chiocciola\"}";
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        when(modelClient.generate(any(ModelRequest.class)))
                .thenAnswer(inv -> {
                    if (callCount.getAndIncrement() == 0) {
                        return new ModelResponse(invalidOutput, 2);
                    }
                    Thread.sleep(30); // secondo tentativo supera il budget di 10ms
                    return new ModelResponse(invalidOutput, 30);
                });

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contatto"));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNull(resp.result());
        assertEquals(invalidOutput, resp.unvalidatedResult());
        assertEquals(2, resp.errors().size(), "Deve contenere errore di validazione e errore di timeout: " + resp.errors());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, resp.errors().get(0).code());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, resp.errors().get(1).code());
        assertTrue(resp.errors().get(1).message().contains("Tempo massimo"));
    }

    @Test
    @DisplayName("D1 (6): Tool in on_indeterminate riceve in $errors la lista completa degli errori (validazione + inferenza)")
    void testOnIndeterminateToolRiceveErroriCompletiInCasoDiGuastoEscalation() {
        com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry tr = new com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry();
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<List<com.tirocinioaltrama.llmorchestrator.core.validation.FieldError>> capturedErrors =
                new java.util.concurrent.atomic.AtomicReference<>();

        tr.register(new com.tirocinioaltrama.llmorchestrator.core.tool.Tool() {
            @Override
            public String getName() {
                return "contact_operator";
            }

            @Override
            public com.tirocinioaltrama.llmorchestrator.core.tool.ToolResult execute(Map<String, Object> args) {
                Object motivo = args.get("motivo");
                if (motivo instanceof List<?> list) {
                    capturedErrors.set((List<com.tirocinioaltrama.llmorchestrator.core.validation.FieldError>) list);
                }
                return com.tirocinioaltrama.llmorchestrator.core.tool.ToolResult.ok("Operatore contattato");
            }
        });

        PipelineDefinition def = new PipelineDefinition();
        def.setId("email_pipe");
        def.setDocumentType("EMAIL");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("email-slm");
        inf.setSchema("schemas/email_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(new FieldValidatorDefinition("email", "email")));
        val.setOnFailure(Map.of(
                "retry_step", "inf",
                "max_retries", 1,
                "escalate_to", "qwen2.5:7b"
        ));

        com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionsGroupDefinition actions =
                new com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionsGroupDefinition();
        actions.setOnIndeterminate(List.of(
                new com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionDefinition("contact_operator", Map.of("motivo", "$errors"))
        ));
        def.setActions(actions);
        def.setSteps(List.of(inf, val));

        YamlPipelineLoader loader = new YamlPipelineLoader(env, validatorRegistry, configValidator) {
            @Override
            public List<PipelineDefinition> loadDefinitions() {
                return List.of(def);
            }
        };
        PipelineRegistry registry = new PipelineRegistry(loader.loadPipelines(modelClient));
        DefaultOrchestrator orchestrator = new DefaultOrchestrator(router, registry, new com.tirocinioaltrama.llmorchestrator.impl.tool.ActionExecutor(tr));

        String invalidOutput = "{\"mittente\":\"Mario\",\"email\":\"senza_chiocciola\"}";
        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenReturn(new ModelResponse(invalidOutput, 30))
                .thenThrow(new RuntimeException("Modello escalation offline"));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.EMAIL, "Contatto"));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNotNull(capturedErrors.get(), "Il tool contact_operator deve essere stato invocato con gli errori");
        assertEquals(2, capturedErrors.get().size(), "Il tool deve aver ricevuto sia FORMAT_INVALID sia INFERENCE_ERROR");
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, capturedErrors.get().get(0).code());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, capturedErrors.get().get(1).code());
    }

    @Test
    @DisplayName("Smart Retry: Cortocircuito immediato (1 sola inferenza) se il checksum invalido è verbatim nel sorgente")
    void testCortocircuitoRetrySuDatoSorgenteAlgebricamenteInvalido() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("fattura_pipe");
        def.setDocumentType("FATTURA");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("invoice-slm");
        inf.setSchema("schemas/fattura_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(
                new FieldValidatorDefinition("fornitore.partitaIva", "partita_iva")
        ));
        val.setUnvalidated(List.of("cliente.codiceFiscale", "iban"));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 2, "feedback", true));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // P.IVA 10000005489 presente identica nel payload originale ma matematicamente invalida (Luhn errato)
        String payload = "Fattura emessa da Acme Srl con Partita IVA 10000005489 per importo di 500 euro.";
        String outputConPivaInvalida = """
                {
                    "numeroFattura": "FT-01",
                    "dataFattura": "2026-02-28",
                    "fornitore": {
                        "denominazione": "Acme Srl",
                        "partitaIva": "10000005489"
                    },
                    "cliente": {
                        "denominazione": "Beta Spa",
                        "codiceFiscale": "01234567890"
                    },
                    "importoTotale": 500.0,
                    "iban": "IT60X0542811101000000123456"
                }
                """;

        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(outputConPivaInvalida, 40));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.FATTURA, payload));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        assertNotNull(resp.errors());
        assertFalse(resp.errors().isEmpty());
        assertEquals(com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.SOURCE_CHECKSUM_INVALID, resp.errors().get(0).code());
        assertTrue(resp.errors().get(0).message().contains("[Dato invalido presente letteralmente nel testo sorgente: retry omesso]"));

        // Verifica fondamentale: esattamente 1 inferenza (nessun retry sprecato)
        verify(modelClient, times(1)).generate(any(ModelRequest.class));
    }

    @Test
    @DisplayName("Smart Retry: Preservazione dei retry se il modello commette un refuso non presente nel testo sorgente")
    void testPreservazioneRetrySuRefusoModelloNonPresenteNelTesto() {
        PipelineDefinition def = new PipelineDefinition();
        def.setId("fattura_pipe");
        def.setDocumentType("FATTURA");

        StepDefinition inf = new StepDefinition();
        inf.setId("inf");
        inf.setType("model_inference");
        inf.setModel("invoice-slm");
        inf.setSchema("schemas/fattura_info.json");

        StepDefinition val = new StepDefinition();
        val.setId("val");
        val.setType("field_validation");
        val.setValidators(List.of(
                new FieldValidatorDefinition("fornitore.partitaIva", "partita_iva")
        ));
        val.setUnvalidated(List.of("cliente.codiceFiscale", "iban"));
        val.setOnFailure(Map.of("retry_step", "inf", "max_retries", 2, "feedback", true));

        def.setSteps(List.of(inf, val));
        DefaultOrchestrator orchestrator = createOrchestratorWithPipeline(def);

        // Nel testo c'è una P.IVA valida 12345678903, ma il modello genera per refuso 12345678904 (non presente nel testo)
        String payload = "Fattura emessa da Acme Srl con Partita IVA 12345678903 per importo di 500 euro.";
        String outputConRefuso = """
                {
                    "numeroFattura": "FT-01",
                    "dataFattura": "2026-02-28",
                    "fornitore": {
                        "denominazione": "Acme Srl",
                        "partitaIva": "12345678904"
                    },
                    "cliente": {
                        "denominazione": "Beta Spa",
                        "codiceFiscale": "01234567890"
                    },
                    "importoTotale": 500.0,
                    "iban": "IT60X0542811101000000123456"
                }
                """;

        when(modelClient.generate(any(ModelRequest.class)))
                .thenReturn(new ModelResponse(outputConRefuso, 40));

        OrchestratorResponse resp = orchestrator.process(new OrchestratorRequest(DocumentType.FATTURA, payload));

        assertEquals(OrchestratorStatus.INDETERMINATE, resp.status());
        // Non essendo verbatim nel sorgente, il cortocircuito NON scatta e vengono tentati tutti i 3 tentativi (1 base + 2 retry)
        verify(modelClient, times(3)).generate(any(ModelRequest.class));
    }
}
