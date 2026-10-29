package com.tirocinioaltrama.llmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.RoutingDecision;

class LocalOllamaRouterTest {

    private LocalOllamaRouter router;

    @BeforeEach
    void setUp() {
        // Router configurato con endpoint fittizio: userà il fallback euristico senza eccezioni
        router = new LocalOllamaRouter("http://localhost:65432", "qwen2.5:3b", Duration.ofMillis(500));
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
    }
}
