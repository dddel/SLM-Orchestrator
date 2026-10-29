package com.tirocinioaltrama.llmorchestrator.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.tirocinioaltrama.llmorchestrator.core.Orchestrator;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorResponse;

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
                .thenReturn(new OrchestratorResponse(com.tirocinioaltrama.llmorchestrator.core.DocumentType.EMAIL, "Fallback Euristico", "{\"mittente\": \"Mario Rossi\"}", 150));

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
                .thenReturn(new OrchestratorResponse(com.tirocinioaltrama.llmorchestrator.core.DocumentType.FATTURA, "CLIENT_SPECIFIED", "{\"numeroFattura\": \"123/2026\"}", 180));

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
                .thenReturn(new OrchestratorResponse(com.tirocinioaltrama.llmorchestrator.core.DocumentType.FATTURA, "Jev Decision Router (cloudflare/@cf/cloudflare/clef-flash)", "{\"numeroFattura\": \"99/2026\"}", 75));

        String requestBody = """
                {
                    "routerType": "JEV",
                    "payload": "Fattura 99/2026 emessa da Alpha Srl"
                }
                """;

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.label").value("FATTURA"))
                .andExpect(jsonPath("$.router").value("Jev Decision Router (cloudflare/@cf/cloudflare/clef-flash)"))
                .andExpect(jsonPath("$.latencyMs").value(75));
    }

    @Test
    void processGestisceRispostaDiFallimentoPipeline() throws Exception {
        when(orchestrator.process(any(OrchestratorRequest.class)))
                .thenReturn(OrchestratorResponse.failure(
                        com.tirocinioaltrama.llmorchestrator.core.DocumentType.ANAGRAFICA,
                        "Fallback Euristico",
                        java.util.List.of(new com.tirocinioaltrama.llmorchestrator.core.validation.FieldError("codiceFiscale", "codice_fiscale", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.CHECKSUM_INVALID, "Codice Fiscale non valido: checksum errato")),
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
                .thenThrow(new com.tirocinioaltrama.llmorchestrator.core.exception.ValidationException("Payload non conforme"));

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
                .thenThrow(new com.tirocinioaltrama.llmorchestrator.core.exception.RoutingException("Router irraggiungibile"));

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
                .thenThrow(new com.tirocinioaltrama.llmorchestrator.core.exception.PipelineExecutionException("Worker Ollama non risponde"));

        mockMvc.perform(post("/api/orchestrator/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": \"test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Worker Ollama non risponde"));
    }
}
