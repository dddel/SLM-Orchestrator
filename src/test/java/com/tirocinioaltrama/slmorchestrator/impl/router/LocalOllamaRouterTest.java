package com.tirocinioaltrama.slmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import com.tirocinioaltrama.slmorchestrator.impl.router.LocalOllamaRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.RoutingDecision;
import com.tirocinioaltrama.slmorchestrator.impl.model.OllamaRuntimeOptions;

class LocalOllamaRouterTest {

    private LocalOllamaRouter router;

    @BeforeEach
    void setUp() {
        // Router configurato con endpoint fittizio: userà il fallback euristico senza eccezioni
        router = new LocalOllamaRouter("http://localhost:65432", "qwen2.5:3b", Duration.ofMillis(500));
    }

    @Test
    @DisplayName("LocalOllamaRouter esegue chiamata HTTP con successo e classifica EMAIL")
    void testApiSuccessfulClassification() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        LocalOllamaRouter apiRouter = new LocalOllamaRouter(restClient, "qwen2.5:3b", "System Prompt", OllamaRuntimeOptions.defaults());

        String jsonResponse = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "EMAIL"
                  }
                }
                """;

        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        RoutingDecision decision = apiRouter.routeWithDecision("Da: test@test.it");
        assertEquals(DocumentType.EMAIL, decision.documentType());
        server.verify();
    }

    @Test
    @DisplayName("LocalOllamaRouter con costruttore a 2 e 3 parametri")
    void testConstructors() {
        RestClient restClient = RestClient.builder().build();
        assertNotNull(new LocalOllamaRouter(restClient, "m1"));
        assertNotNull(new LocalOllamaRouter(restClient, "m1", "p1"));
    }

    @Test
    @DisplayName("Inizializzazione carica il System Prompt di routing predefinito o iniettato")
    void inizializzazioneCaricaSystemPrompt() {
        assertNotNull(router.getSystemPrompt());
        assertTrue(router.getSystemPrompt().contains("Sei un router deterministico"));
        assertTrue(router.getSystemPrompt().contains("EMAIL"));
        assertTrue(router.getSystemPrompt().contains("FATTURA"));
        assertEquals("qwen2.5:3b", router.getModel());
    }

    @Test
    @DisplayName("LocalOllamaRouter con system prompt personalizzato preserva il prompt passato")
    void costruttoreConSystemPromptPersonalizzato() {
        String customPrompt = "Istruzioni speciali di routing deterministico.";
        LocalOllamaRouter customRouter = new LocalOllamaRouter("http://localhost:65432", "qwen2.5:7b", Duration.ofSeconds(1), customPrompt);
        assertEquals(customPrompt, customRouter.getSystemPrompt());
        assertEquals("qwen2.5:7b", customRouter.getModel());
    }

    @Test
    @DisplayName("Fallback euristico riconosce Fattura da parole chiave contabili")
    void fallbackRiconosceFattura() {
        RoutingDecision decision = router.routeWithDecision("Fattura commerciale n. 45 con P.IVA 12345678903 e coordinate IBAN IT60X...");
        assertEquals(DocumentType.FATTURA, decision.documentType());
        assertEquals(LocalOllamaRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    @DisplayName("Fallback euristico riconosce Anagrafica da parole chiave identificative")
    void fallbackRiconosceAnagrafica() {
        RoutingDecision decision = router.routeWithDecision("Carta d'identità elettronica CA12345AA con codice fiscale RSSMRA85C12D086Q");
        assertEquals(DocumentType.ANAGRAFICA, decision.documentType());
        assertEquals(LocalOllamaRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    @DisplayName("Fallback euristico riconosce Email da parole chiave o intestazioni")
    void fallbackRiconosceEmail() {
        RoutingDecision decision = router.routeWithDecision("Da: mario@acme.it\nA: supporto@fornitore.com\nOggetto: Domanda");
        assertEquals(DocumentType.EMAIL, decision.documentType());
        assertEquals(LocalOllamaRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    @DisplayName("Fallback euristico riconosce Altro per testo non classificabile")
    void fallbackRiconosceAltro() {
        RoutingDecision decision = router.routeWithDecision("Un testo generico che descrive un paesaggio montano innevato.");
        assertEquals(DocumentType.ALTRO, decision.documentType());
        assertEquals(LocalOllamaRouter.ROUTER_NAME_FALLBACK, decision.routerName());
        assertTrue(decision.isHeuristicFallback());
    }

    @Test
    @DisplayName("T2.2: Fallback euristico non confonde 'Data:' con l'intestazione email 'A:'")
    void fallbackNonConfondeDataConIntestazioneEmail() {
        DocumentType detected = LocalOllamaRouter.fallbackHeuristic("Data: 12/03/2026\nVerbale riunione interna di progetto.");
        assertEquals(DocumentType.ALTRO, detected, "'Data:' non deve essere interpretato come intestazione 'A:'");
    }

    @Test
    @DisplayName("T2.1: Con fallback euristico disabilitato (configurazione o per-request) solleva RoutingException se Ollama è irraggiungibile")
    void fallbackDisabilitatoSollevaRoutingException() {
        // 1. Disabilitato da configurazione del router
        LocalOllamaRouter strictRouter = new LocalOllamaRouter(
                "http://localhost:65432", "qwen2.5:3b", Duration.ofMillis(200), null, null, false
        );
        org.junit.jupiter.api.Assertions.assertThrows(
                com.tirocinioaltrama.slmorchestrator.core.exception.RoutingException.class,
                () -> strictRouter.routeWithDecision("Fattura commerciale n. 45")
        );

        // 2. Disabilitato per-request su router con fallback attivo di default
        org.junit.jupiter.api.Assertions.assertThrows(
                com.tirocinioaltrama.slmorchestrator.core.exception.RoutingException.class,
                () -> router.routeWithDecision("Fattura commerciale n. 45", false)
        );
    }
}
