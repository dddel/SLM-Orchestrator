package com.tirocinioaltrama.llmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.Tag;

import com.tirocinioaltrama.llmorchestrator.config.router.JevProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.RoutingDecision;

@Tag("live")
class RealCloudflareJevRouterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(RealCloudflareJevRouterLiveTest.class);

    private static JevRouter router;
    private static String apiToken;
    private static String accountId;

    @BeforeAll
    static void init() {
        Map<String, String> env = loadEnvFile();
        apiToken = System.getenv("CLOUDFLARE_API_TOKEN");
        if (apiToken == null || apiToken.isBlank()) {
            apiToken = env.get("CLOUDFLARE_API_TOKEN");
        }

        accountId = System.getenv("CLOUDFLARE_ACCOUNT_ID");
        if (accountId == null || accountId.isBlank()) {
            accountId = env.get("CLOUDFLARE_ACCOUNT_ID");
        }

        if (apiToken != null && !apiToken.isBlank() && accountId != null && !accountId.isBlank()) {
            JevProperties props = new JevProperties(
                    "cloudflare",
                    apiToken,
                    accountId,
                    "@cf/cloudflare/clef-flash",
                    null,
                    Duration.ofSeconds(10)
            );
            router = new JevRouter(props);
        }
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> map = new HashMap<>();
        Path p = Path.of(".env");
        if (Files.exists(p)) {
            try (BufferedReader r = Files.newBufferedReader(p)) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.startsWith("#") && line.contains("=")) {
                        int eq = line.indexOf('=');
                        String k = line.substring(0, eq).trim();
                        String v = line.substring(eq + 1).trim();
                        map.put(k, v);
                    }
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    @Test
    @DisplayName("Cloudflare Workers AI: Test routing live con @cf/cloudflare/clef-flash")
    void testLiveCloudflareRouting() {
        assumeTrue(router != null, "Credenziali Cloudflare mancanti, salto test live.");

        long start = System.currentTimeMillis();
        RoutingDecision decision = router.routeWithDecision("""
                Fattura Elettronica n. 2026/044 emessa da Studio Tecnico Alpha Srl.
                P.IVA: 12345678901 - Codice Destinatario: SUBM70N
                Totale documento: EUR 2.450,00 comprensivo di IVA 22%.
                Coordinate pagamento: IBAN IT60X0542811101000000123456
                """);
        long latency = System.currentTimeMillis() - start;

        log.info("Risultato Cloudflare Clef-flash: {} via '{}' in {} ms",
                decision.documentType(), decision.routerName(), latency);

        assertNotEquals(JevRouter.ROUTER_NAME_FALLBACK, decision.routerName(),
                "Non deve usare il fallback euristico su chiamata reale!");
        assertEquals(DocumentType.FATTURA, decision.documentType());
    }

    @Test
    @DisplayName("Cloudflare Workers AI: Test routing email")
    void testLiveCloudflareEmailRouting() {
        assumeTrue(router != null, "Credenziali Cloudflare mancanti, salto test live.");

        long start = System.currentTimeMillis();
        RoutingDecision decision = router.routeWithDecision("""
                Da: coordinamento@azienda.it
                A: personale@azienda.it
                Oggetto: Convocazione riunione straordinaria di dipartimento
                
                Gentili colleghi, vi comunico che venerdì alle 15:00 si terrà l'allineamento sui progetti in corso.
                Cordiali saluti,
                La Direzione
                """);
        long latency = System.currentTimeMillis() - start;

        log.info("Risultato Cloudflare Clef-flash (email): {} via '{}' in {} ms",
                decision.documentType(), decision.routerName(), latency);

        assertNotEquals(JevRouter.ROUTER_NAME_FALLBACK, decision.routerName(),
                "Non deve usare il fallback euristico su chiamata reale!");
        assertEquals(DocumentType.EMAIL, decision.documentType());
    }
}
