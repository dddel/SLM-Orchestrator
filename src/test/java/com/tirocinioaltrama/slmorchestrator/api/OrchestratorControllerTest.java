package com.tirocinioaltrama.slmorchestrator.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tirocinioaltrama.slmorchestrator.api.GlobalExceptionHandler;
import com.tirocinioaltrama.slmorchestrator.api.OrchestratorController;
import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineExecutionException;
import com.tirocinioaltrama.slmorchestrator.core.exception.RoutingException;
import com.tirocinioaltrama.slmorchestrator.core.exception.ValidationException;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.tirocinioaltrama.slmorchestrator.core.Orchestrator;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorResponse;

class OrchestratorControllerTest {

    private Orchestrator orchestrator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        orchestrator = mock(Orchestrator.class);
        OrchestratorController controller = new OrchestratorController(orchestrator);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void processInviaPayloadEmailDefaultAllOrchestratore() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenReturn(new OrchestratorResponse(DocumentType.EMAIL, "Fallback Euristico", "{\"mittente\": \"Mario Rossi\"}", 150));

        String requestBody = """
                {
                    "payload": "Testo email di prova per l'orchestratore"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("EMAIL"))
                .andExpect(jsonPath("$.router").value("Fallback Euristico"))
                .andExpect(jsonPath("$.result").value("{\"mittente\": \"Mario Rossi\"}"))
                .andExpect(jsonPath("$.latencyMs").value(150));
    }

    @Test
    void processInviaPayloadFatturaSpecificandoTipo() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenReturn(new OrchestratorResponse(DocumentType.FATTURA, "CLIENT_SPECIFIED", "{\"numeroFattura\": \"123/2026\"}", 180));

        String requestBody = """
                {
                    "type": "FATTURA",
                    "payload": "Fattura 123/2026 per servizi informatici"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.label").value("FATTURA"))
                .andExpect(jsonPath("$.router").value("CLIENT_SPECIFIED"))
                .andExpect(jsonPath("$.result").value("{\"numeroFattura\": \"123/2026\"}"))
                .andExpect(jsonPath("$.latencyMs").value(180));
    }

    @Test
    void processInviaPayloadConRouterSpecificato() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenReturn(new OrchestratorResponse(DocumentType.FATTURA, "Ollama Local (qwen2.5:3b)", "{\"numeroFattura\": \"99/2026\"}", 75));

        String requestBody = """
                {
                    "routerType": "LOCAL_OLLAMA",
                    "payload": "Fattura 99/2026 emessa da Alpha Srl"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.label").value("FATTURA"))
                .andExpect(jsonPath("$.router").value("Ollama Local (qwen2.5:3b)"))
                .andExpect(jsonPath("$.latencyMs").value(75));
    }

    @Test
    void processInviaPayloadConRouterKebabCaseLocalOllamaEReflex() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenReturn(new OrchestratorResponse(DocumentType.EMAIL, "Apus Reflex", "{}", 50));

        String requestBodyKebab = """
                {
                    "routerType": "local-ollama",
                    "payload": "Email di prova"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyKebab))
                .andExpect(status().isOk());

        String requestBodyReflex = """
                {
                    "routerType": "reflex",
                    "payload": "Email di prova"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBodyReflex))
                .andExpect(status().isOk());
    }

    @Test
    void processGestisceRispostaDiFallimentoPipeline() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenReturn(OrchestratorResponse.failure(
                        DocumentType.ANAGRAFICA,
                        "Fallback Euristico",
                        java.util.List.of(new FieldError("codiceFiscale", "codice_fiscale", ValidationErrorCode.CHECKSUM_INVALID, "Codice Fiscale non valido: checksum errato")),
                        220
                ));

        String requestBody = """
                {
                    "payload": "Mario Rossi CF non valido"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDETERMINATE"))
                .andExpect(jsonPath("$.label").value("ANAGRAFICA"))
                .andExpect(jsonPath("$.errors[0].path").value("codiceFiscale"))
                .andExpect(jsonPath("$.errors[0].code").value("CHECKSUM_INVALID"))
                .andExpect(jsonPath("$.errors[0].message").value("Codice Fiscale non valido: checksum errato"));
    }

    @Test
    void processRestituisce422SeValidationException() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenThrow(new ValidationException("Payload non conforme"));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"test errato\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"))
                .andExpect(jsonPath("$.message").value("Payload non conforme"))
                .andExpect(jsonPath("$.path").value("/api/orchestrator/process"));
    }

    @Test
    void processRestituisce502SeRoutingException() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenThrow(new RoutingException("Router irraggiungibile"));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"test\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value("Router irraggiungibile"));
    }

    @Test
    void processRestituisce503SePipelineExecutionException() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenThrow(new PipelineExecutionException("Worker Ollama non risponde"));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Worker Ollama non risponde"));
    }

    @Test
    void processRestituisce400SeIllegalArgumentException() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenThrow(new IllegalArgumentException("Argomento non valido"));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Argomento non valido"));
    }

    @Test
    void processRestituisce400SeBodyMalformato() throws Exception {
        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid_json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void processRestituisce422SePayloadSuperaBudgetFinestraDiContesto() throws Exception {
        com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult budgetRes =
                com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult.withFieldErrors(
                        java.util.List.of(new FieldError("$", "payload", ValidationErrorCode.CONTEXT_TRUNCATED,
                                "Il payload supera il budget della finestra di contesto (num_ctx=2048)")),
                        java.util.List.of()
                );
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenThrow(new ValidationException("Payload eccede il budget della finestra di contesto (num_ctx)", budgetRes));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"testo molto lungo oltre num_ctx\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value("Payload eccede il budget della finestra di contesto (num_ctx): Il payload supera il budget della finestra di contesto (num_ctx=2048)"))
                .andExpect(jsonPath("$.details[0]").value("Il payload supera il budget della finestra di contesto (num_ctx=2048)"));
    }

    @Test
    void processRestituisce500SeEccezioneGenerica() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenThrow(new RuntimeException("Crash imprevisto"));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"test\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Si è verificato un errore interno durante l'elaborazione della richiesta."));
    }
}
