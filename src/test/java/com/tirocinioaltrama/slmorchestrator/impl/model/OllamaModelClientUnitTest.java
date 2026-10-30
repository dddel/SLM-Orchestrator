package com.tirocinioaltrama.slmorchestrator.impl.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaModelClientUnitTest {

    private MockRestServiceServer server;
    private OllamaModelClient client;
    private OllamaModelClient clientWithDefaults;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        client = new OllamaModelClient(restClient, "qwen2.5:3b", true, OllamaRuntimeOptions.defaults());
        clientWithDefaults = new OllamaModelClient(restClient);
    }

    @Test
    @DisplayName("generate restituisce ModelResponse con contenuto e metriche conformi")
    void testGenerateSuccess() {
        String jsonResponse = """
                {
                  "model": "qwen2.5:3b",
                  "message": {
                    "role": "assistant",
                    "content": "{\\"mittente\\": \\"Luca\\", \\"email\\": \\"luca@test.it\\"}"
                  },
                  "prompt_eval_count": 45,
                  "eval_count": 30,
                  "done_reason": "stop"
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ModelRequest request = new ModelRequest(
                "qwen2.5:3b",
                "Estrai dati da: Sono Luca luca@test.it",
                "{\"type\": \"object\"}",
                Map.of("temperature", 0.1),
                "Sei un assistente di estrazione dati."
        );

        ModelResponse response = client.generate(request);

        assertNotNull(response);
        assertEquals("{\"mittente\": \"Luca\", \"email\": \"luca@test.it\"}", response.content());
        assertNotNull(response.metrics());
        assertEquals(45, response.metrics().promptTokens());
        assertEquals(30, response.metrics().completionTokens());
        assertEquals("stop", response.metrics().doneReason());
        server.verify();
    }

    @Test
    @DisplayName("generate usa il defaultModel se request.model() è nullo o vuoto")
    void testGenerateWithDefaultModel() {
        String jsonResponse = """
                {
                  "model": "qwen2.5:3b",
                  "message": {
                    "role": "assistant",
                    "content": "risultato"
                  }
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ModelRequest request = new ModelRequest(null, "Test user prompt", null);
        ModelResponse response = client.generate(request);

        assertEquals("risultato", response.content());
        server.verify();
    }

    @Test
    @DisplayName("generate solleva IllegalStateException se Ollama risponde con messaggio vuoto")
    void testGenerateWithEmptyMessage() {
        String jsonResponse = """
                {
                  "model": "qwen2.5:3b",
                  "message": null
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        ModelRequest request = new ModelRequest("qwen2.5:3b", "Ciao", null);
        assertThrows(IllegalStateException.class, () -> client.generate(request));
        server.verify();
    }

    @Test
    @DisplayName("generate propaga eccezioni HTTP sollevate dal server Ollama")
    void testGenerateHttpError() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ModelRequest request = new ModelRequest("qwen2.5:3b", "Ciao", null);
        assertThrows(Exception.class, () -> client.generate(request));
        server.verify();
    }

    @Test
    @DisplayName("Costruttori sovraccaricati di OllamaModelClient")
    void testConstructors() {
        RestClient restClient = RestClient.builder().build();
        assertNotNull(new OllamaModelClient(restClient));
        assertNotNull(new OllamaModelClient(restClient, "m1"));
        assertNotNull(new OllamaModelClient(restClient, "m1", false));
        assertNotNull(new OllamaModelClient(restClient, "m1", true, OllamaRuntimeOptions.defaults()));
    }
}
