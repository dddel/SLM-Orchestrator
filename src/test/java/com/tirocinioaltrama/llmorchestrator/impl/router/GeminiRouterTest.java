package com.tirocinioaltrama.llmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.llmorchestrator.config.router.GeminiProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;

class GeminiRouterTest {

    private GeminiRouter router;

    @BeforeEach
    void setUp() {
        // Con chiave segnaposto, il router userà il fallback euristico senza fallire o fare chiamate di rete esterne
        GeminiProperties properties = new GeminiProperties(
                "INSERISCI_QUI_LA_TUA_CHIAVE_GOOGLE_AI_STUDIO",
                "gemini-3.5-flash-lite",
                "https://generativelanguage.googleapis.com/v1beta",
                Duration.ofSeconds(5)
        );
        router = new GeminiRouter(properties);
    }

    @Test
    void fallbackRiconosceFatturaDaKeywords() {
        com.tirocinioaltrama.llmorchestrator.core.RoutingDecision decision = router.routeWithDecision("Fattura n. 123 con P.IVA 12345678901 e IBAN IT60X...");
        assertEquals(DocumentType.FATTURA, decision.documentType());
        assertEquals(GeminiRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackRiconosceAnagraficaDaKeywords() {
        com.tirocinioaltrama.llmorchestrator.core.RoutingDecision decision = router.routeWithDecision("Carta d'identità di Mario Rossi, codice fiscale RSSMRA...");
        assertEquals(DocumentType.ANAGRAFICA, decision.documentType());
        assertEquals(GeminiRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackRiconosceEmailSePatternEmailPresente() {
        com.tirocinioaltrama.llmorchestrator.core.RoutingDecision decision = router.routeWithDecision("Da: mario@test.it\nOggetto: Domanda");
        assertEquals(DocumentType.EMAIL, decision.documentType());
        assertEquals(GeminiRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackRiconosceAltroSeNessunaKeywordCorrisponde() {
        com.tirocinioaltrama.llmorchestrator.core.RoutingDecision decision = router.routeWithDecision("Questo è un racconto fantastico su pianeti lontani.");
        assertEquals(DocumentType.ALTRO, decision.documentType());
        assertEquals(GeminiRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }
}
