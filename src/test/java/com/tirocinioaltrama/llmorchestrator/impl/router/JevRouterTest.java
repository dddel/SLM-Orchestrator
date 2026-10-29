package com.tirocinioaltrama.llmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.llmorchestrator.config.router.JevProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.RoutingDecision;

class JevRouterTest {

    private JevRouter router;

    @BeforeEach
    void setUp() {
        // Con chiave segnaposto, il router userà il fallback euristico senza fallire o fare chiamate di rete esterne
        JevProperties properties = new JevProperties(
                "typesafe",
                "INSERISCI_QUI_LA_TUA_CHIAVE",
                "",
                "jev-latest",
                "https://api.typesafe.ai/v1/systemone",
                Duration.ofSeconds(5)
        );
        router = new JevRouter(properties);
    }

    @Test
    void fallbackRiconosceFatturaDaKeywords() {
        RoutingDecision decision = router.routeWithDecision("Fattura n. 123 con P.IVA 12345678901 e IBAN IT60X...");
        assertEquals(DocumentType.FATTURA, decision.documentType());
        assertEquals(JevRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackRiconosceAnagraficaDaKeywords() {
        RoutingDecision decision = router.routeWithDecision("Carta d'identità di Mario Rossi, codice fiscale RSSMRA...");
        assertEquals(DocumentType.ANAGRAFICA, decision.documentType());
        assertEquals(JevRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackRiconosceEmailSePatternEmailPresente() {
        RoutingDecision decision = router.routeWithDecision("Da: mario@test.it\nOggetto: Domanda");
        assertEquals(DocumentType.EMAIL, decision.documentType());
        assertEquals(JevRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackRiconosceAltroSeNessunaKeywordCorrisponde() {
        RoutingDecision decision = router.routeWithDecision("Questo è un racconto fantastico su pianeti lontani.");
        assertEquals(DocumentType.ALTRO, decision.documentType());
        assertEquals(JevRouter.ROUTER_NAME_FALLBACK, decision.routerName());
    }

    @Test
    void fallbackSuPayloadVuotoONullo() {
        RoutingDecision nullDec = router.routeWithDecision(null);
        assertEquals(DocumentType.ALTRO, nullDec.documentType());
        assertEquals(JevRouter.ROUTER_NAME_FALLBACK, nullDec.routerName());

        RoutingDecision blankDec = router.routeWithDecision("   ");
        assertEquals(DocumentType.ALTRO, blankDec.documentType());
        assertEquals(JevRouter.ROUTER_NAME_FALLBACK, blankDec.routerName());
    }
}
