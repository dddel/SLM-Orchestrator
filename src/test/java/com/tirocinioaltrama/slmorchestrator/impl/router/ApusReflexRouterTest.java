package com.tirocinioaltrama.slmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.RoutingDecision;

class ApusReflexRouterTest {

    @Test
    @DisplayName("parseChoiceToken riconosce i token discreti A, B, C, D e prefissi")
    void testParseChoiceToken() {
        assertEquals(DocumentType.FATTURA, ApusReflexRouter.parseChoiceToken("A"));
        assertEquals(DocumentType.EMAIL, ApusReflexRouter.parseChoiceToken("B"));
        assertEquals(DocumentType.ANAGRAFICA, ApusReflexRouter.parseChoiceToken("C"));
        assertEquals(DocumentType.ALTRO, ApusReflexRouter.parseChoiceToken("D"));

        // Tolleranza a spazi e punteggiatura
        assertEquals(DocumentType.FATTURA, ApusReflexRouter.parseChoiceToken("  A. "));
        assertEquals(DocumentType.EMAIL, ApusReflexRouter.parseChoiceToken("Choice: B"));
        assertEquals(DocumentType.ANAGRAFICA, ApusReflexRouter.parseChoiceToken("C: ANAGRAFICA"));
        assertEquals(DocumentType.ALTRO, ApusReflexRouter.parseChoiceToken("Option D"));

        // Fallback per parole intere
        assertEquals(DocumentType.FATTURA, ApusReflexRouter.parseChoiceToken("FATTURA COMMERCIALE"));
        assertEquals(DocumentType.EMAIL, ApusReflexRouter.parseChoiceToken("EMAIL IN ARRIVO"));
        assertEquals(DocumentType.ANAGRAFICA, ApusReflexRouter.parseChoiceToken("DOCUMENTO D'IDENTITA"));
    }

    @Test
    @DisplayName("ApusReflexRouter esegue chiamata REST con successo e mappa single-token A in FATTURA")
    void testSuccessfulRouteFattura() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ApusReflexRouter router = new ApusReflexRouter(restClient, "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M");

        String jsonResponse = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "A"
                  },
                  "load_duration": 12000000
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"capabilities\": [\"thinking\"]}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        RoutingDecision decision = router.routeWithDecision("Fattura n. 123 del 2026, P.IVA 01234567890");
        assertEquals(DocumentType.FATTURA, decision.documentType());
        assertTrue(decision.routerName().contains("APUS Reflex"));
        server.verify();
    }

    @Test
    @DisplayName("ApusReflexRouter con single-token B restituisce EMAIL")
    void testSuccessfulRouteEmail() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ApusReflexRouter router = new ApusReflexRouter(restClient, "reflex-test");

        String jsonResponse = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "B"
                  }
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"capabilities\": [\"thinking\"]}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        DocumentType type = router.route("Da: info@azienda.it\nA: marco@azienda.it\nOggetto: Meeting");
        assertEquals(DocumentType.EMAIL, type);
        server.verify();
    }

    @Test
    @DisplayName("ApusReflexRouter attiva il fallback euristico su errore HTTP 500 del server Ollama")
    void testServerErrorFallback() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ApusReflexRouter router = new ApusReflexRouter(restClient, "reflex-model");

        server.expect(requestTo("http://localhost:11434/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"capabilities\": [\"thinking\"]}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        RoutingDecision decision = router.routeWithDecision("Carta d'identità n. CA12345AA, CF: RSSMRA80A01H501U");
        assertEquals(DocumentType.ANAGRAFICA, decision.documentType());
        assertTrue(decision.routerName().contains("Fallback Euristico"));
        assertEquals(1, router.getFallbackInvocationCount());
        server.verify();
    }

    @Test
    @DisplayName("Fase A2: ApusReflexRouter su token non valido 'The' in content dichiara fallback nel nome e conta token non valido")
    void testInvalidTokenInContentTriggersFallbackWithDeclaredNameAndMetrics() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ApusReflexRouter router = new ApusReflexRouter(restClient, "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M");

        String jsonResponse = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "The"
                  }
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"capabilities\": [\"thinking\"]}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        RoutingDecision decision = router.routeWithDecision("Carta d'identità n. CA12345AA, CF: RSSMRA80A01H501U");
        assertEquals(DocumentType.ANAGRAFICA, decision.documentType());
        assertTrue(decision.routerName().contains("Fallback Euristico per Token Non Valido: 'The'"));
        assertEquals(1, router.getInvalidTokenCount());
        assertEquals(1, router.getFallbackInvocationCount());
        server.verify();
    }

    @Test
    @DisplayName("Fase A2: ApusReflexRouter su token in thinking con content vuoto gestisce token non valido e attiva fallback")
    void testInvalidTokenInThinkingTriggersFallbackWithDeclaredName() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        ApusReflexRouter router = new ApusReflexRouter(restClient, "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M");

        String jsonResponse = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "thinking": "The"
                  }
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"capabilities\": [\"thinking\"]}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        RoutingDecision decision = router.routeWithDecision("Carta d'identità n. CA12345AA, CF: RSSMRA80A01H501U");
        assertEquals(DocumentType.ANAGRAFICA, decision.documentType());
        assertTrue(decision.routerName().contains("Fallback Euristico per Token Non Valido: 'The'"));
        assertEquals(1, router.getInvalidTokenCount());
        server.verify();
    }

    @Test
    @DisplayName("ApusReflexRouter con payload nullo o vuoto restituisce ALTRO con fallback euristico")
    void testEmptyPayloadReturnsAltro() {
        ApusReflexRouter router = new ApusReflexRouter("http://localhost:65432", "reflex", Duration.ofMillis(200));
        assertEquals(DocumentType.ALTRO, router.route(null));
        assertEquals(DocumentType.ALTRO, router.route("   "));
        RoutingDecision decision = router.routeWithDecision("");
        assertEquals(DocumentType.ALTRO, decision.documentType());
        assertEquals("Fallback Euristico", decision.routerName());
    }

    @Test
    @DisplayName("Verifica costruttori e getter di ApusReflexRouter")
    void testConstructorsAndGetters() {
        ApusReflexRouter r1 = new ApusReflexRouter("http://localhost:11434", "reflex-mod", Duration.ofSeconds(5));
        assertEquals("reflex", r1.getName());
        assertEquals("reflex-mod", r1.getModel());

        ApusReflexRouter r2 = new ApusReflexRouter("http://localhost:11434", null, null, "Custom template %s");
        assertEquals("hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M", r2.getModel());

        RestClient rc = RestClient.builder().build();
        ApusReflexRouter r3 = new ApusReflexRouter(rc, "reflex-rc");
        assertNotNull(r3);
    }
}
