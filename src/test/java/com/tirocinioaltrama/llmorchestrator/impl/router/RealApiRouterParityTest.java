package com.tirocinioaltrama.llmorchestrator.impl.router;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.llmorchestrator.config.router.GeminiProperties;
import com.tirocinioaltrama.llmorchestrator.config.router.JevProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.RoutingDecision;

/**
 * Suite di test che verifica e confronta la precisione di tutti i Router reali:
 * 1. Ollama Locale (modello SLM dedicato 'qwen2.5-router') via vera API locale (/api/chat)
 * 2. Google Gemini (Google AI Studio) via vera API remota REST (generateContent)
 * 3. Jev / Cloudflare Workers AI ('@cf/cloudflare/clef-flash') via API REST decisionale System One
 *
 * Il test NON ammette fallback euristico per i router testati: fallisce o salta se un'API reale non è operativa.
 * Valuta l'accuratezza di routing (input -> label/pipeline) su 10 prompt diversificati.
 */
@Tag("live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealApiRouterParityTest {

    private static final Logger log = LoggerFactory.getLogger(RealApiRouterParityTest.class);

    private static GeminiRouter geminiRouter;
    private static LocalOllamaRouter localOllamaRouter;
    private static JevRouter jevRouter;

    private static String geminiApiKey;
    private static String cloudflareApiToken;
    private static String cloudflareAccountId;

    public record PromptBenchmark(
            int index,
            String id,
            DocumentType expectedPipeline,
            String payload,
            String description
    ) {}

    /**
     * 10 Prompt realistici e variegati per testare la precisione di classificazione input -> pipeline:
     * - Fatture (standard, parcella, nota di credito con acconto)
     * - Email (assistenza tecnica formale, convocazione riunione tra colleghi, notifica devops)
     * - Anagrafica (carta d'identità, patente europea con codici UE, autocertificazione stato civile)
     * - Altro (testo generico non strutturato/letterario)
     */
    public static final List<PromptBenchmark> BENCHMARK_10_PROMPTS = List.of(
            // --- FATTURA ---
            new PromptBenchmark(
                    1, "FAT-01", DocumentType.FATTURA,
                    """
                    FATTURA ELETTRONICA N. 2026/089
                    Fornitore: Acme Forniture Industriali S.r.l. - P.IVA: 01234567897
                    Cliente: Gamma Logistica Spa - C.F. 09876543217
                    Imponibile: EUR 1.500,00 - IVA 22%: EUR 330,00 - Totale Documento: EUR 1.830,00
                    Condizioni di pagamento: Bonifico bancario su IBAN IT60X0542811101000000123456
                    """,
                    "Fattura elettronica commerciale con imponibile, IVA, P.IVA e IBAN"
            ),
            new PromptBenchmark(
                    2, "FAT-02", DocumentType.FATTURA,
                    """
                    PARCELLA PROFESSIONALE N. 14/2026
                    Dott. Ing. Roberto Moretti - Ordine Ingegneri n. 4512
                    C.F. MRT RBR 74A01 H501 V
                    Onorario professionale per collaudo statico: € 3.200,00
                    Contributo integrativo Inarcassa 4%: € 128,00
                    Ritenuta d'acconto 20%: € 640,00
                    Netto a pagare: € 2.688,00 entro 30 gg.
                    """,
                    "Parcella professionale con cassa previdenza e ritenuta d'acconto"
            ),
            new PromptBenchmark(
                    3, "FAT-03", DocumentType.FATTURA,
                    """
                    NOTA DI CREDITO N. NC-2026-05
                    Riferimento a nostra fattura n. 2026/012 del 10/01/2026.
                    Storno parziale per merce resa danneggiata.
                    Totale a vostro accredito: 450,00 EUR (IVA 22% inclusa).
                    La somma verra' compensata sulla prossima scadenza contabile.
                    """,
                    "Nota di credito contabile con storno e imponibile IVA"
            ),

            // --- EMAIL ---
            new PromptBenchmark(
                    4, "EML-01", DocumentType.EMAIL,
                    """
                    Da: mario.bianchi@azienda.it
                    A: helpdesk@servizi-it.com
                    Data: 18 Ottobre 2026 14:15
                    Oggetto: Impossibilita' di accesso alla VPN aziendale
                    
                    Gentile Supporto,
                    da questa mattina riscontro un errore di autenticazione continuo tentando
                    il login al gateway VPN. Potreste verificare se l'utenza e' scaduta?
                    In allegato invio lo screenshot dell'errore.
                    Grazie per la collaborazione,
                    Mario Bianchi
                    """,
                    "Email aziendale di supporto con header espliciti e formula di saluto"
            ),
            new PromptBenchmark(
                    5, "EML-02", DocumentType.EMAIL,
                    """
                    Ciao Chiara,
                    ti scrivo per ricordarti l'appuntamento per il punto settimanale domani mattina alle 9:30.
                    Ci colleghiamo direttamente sulla stanza virtuale consueta.
                    A domani e buona serata!
                    Davide
                    """,
                    "Email informale di promemoria interno senza campi di header canonici"
            ),
            new PromptBenchmark(
                    6, "EML-03", DocumentType.EMAIL,
                    """
                    From: alerts@cloudmonitoring.io
                    To: oncall-team@system.net
                    Subject: CRITICAL: Database replica lag above 60s
                    
                    Monitoring check detected high replication latency on cluster node pg-slave-02.
                    Current lag: 74 seconds.
                    Runbook reference: https://wiki.internal/ops/postgres-lag
                    Automated alert triggered at 2026-10-08T08:00:00Z.
                    """,
                    "Notifica email in lingua inglese di allerta infrastrutturale"
            ),

            // --- ANAGRAFICA ---
            new PromptBenchmark(
                    7, "ANA-01", DocumentType.ANAGRAFICA,
                    """
                    REPUBBLICA ITALIANA - MINISTERO DELL'INTERNO
                    CARTA DI IDENTITÀ ELETTRONICA N. CA77218ZZ
                    Cognome: VERDI
                    Nome: STEFANO
                    Luogo e data di nascita: FIRENZE (FI) 22/04/1984
                    Sesso: M - Statura: 180 - Cittadinanza: ITA
                    Codice Fiscale: VRD SFN 84D22 D612 J
                    Residenza: Via de' Tornabuoni 15, Firenze
                    Scadenza: 22/04/2034
                    """,
                    "Carta d'identità elettronica (CIE) ministeriale con dati anagrafici e CF"
            ),
            new PromptBenchmark(
                    8, "ANA-02", DocumentType.ANAGRAFICA,
                    """
                    PATENTE DI GUIDA DELLA REPUBBLICA ITALIANA (MODELLO UE)
                    1. RINALDI
                    2. ELENA
                    3. 11/11/1992 BOLOGNA (BO)
                    4a. 05/06/2022  4b. 11/11/2032  4c. MIT-UCO
                    5. U1839201F
                    9. B, A2
                    Codice Fiscale: RNL LNE 92S51 A944 E
                    """,
                    "Patente di guida con numerazione standard UE e categorie di abilitazione"
            ),
            new PromptBenchmark(
                    9, "ANA-03", DocumentType.ANAGRAFICA,
                    """
                    DICHIARAZIONE SOSTITUTIVA DI CERTIFICAZIONE ANAGRAFICA
                    (Art. 46 D.P.R. 28 dicembre 2000, n. 445)
                    Il sottoscritto Luca Ferri, nato a Roma (RM) il 03/09/1979,
                    codice fiscale FRRLCU79P03H501A, residente a Milano in Corso Buenos Aires 44,
                    consapevole delle sanzioni penali previste in caso di dichiarazioni mendaci,
                    dichiara di essere celibe e di risiedere all'indirizzo sopra indicato.
                    """,
                    "Autocertificazione/dichiarazione sostitutiva anagrafica con CF e residenza"
            ),

            // --- ALTRO ---
            new PromptBenchmark(
                    10, "ALT-01", DocumentType.ALTRO,
                    """
                    Il telescopio spaziale James Webb ha osservato una delle galassie piu' remote
                    dell'universo conosciuto, la cui luce e' stata emessa appena 300 milioni di anni
                    dopo il Big Bang. Lo strumento NIRCam ha permesso di rilevare tracce di gas ionizzato
                    e popolazioni stellari di prima generazione.
                    """,
                    "Articolo divulgativo scientifico astronomico privo di fatture, email o documenti"
            )
    );

    @BeforeAll
    static void setUpRealRouters() {
        Map<String, String> env = loadEnvFile();

        // 1. Google Gemini Key
        geminiApiKey = System.getenv("GEMINI_API_KEY");
        if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.contains("INSERISCI_QUI")) {
            geminiApiKey = env.get("GEMINI_API_KEY");
        }

        GeminiProperties geminiProps = new GeminiProperties(
                geminiApiKey != null ? geminiApiKey : "",
                "gemini-3.5-flash-lite",
                "https://generativelanguage.googleapis.com/v1beta",
                Duration.ofSeconds(15)
        );
        geminiRouter = new GeminiRouter(geminiProps);

        // 2. Ollama Locale
        localOllamaRouter = new LocalOllamaRouter(
                "http://localhost:11434",
                "qwen2.5-router:latest",
                Duration.ofSeconds(20)
        );

        // 3. Jev / Cloudflare Workers AI
        cloudflareApiToken = System.getenv("CLOUDFLARE_API_TOKEN");
        if (cloudflareApiToken == null || cloudflareApiToken.isBlank() || cloudflareApiToken.contains("INSERISCI_QUI")) {
            cloudflareApiToken = env.get("CLOUDFLARE_API_TOKEN");
        }
        cloudflareAccountId = System.getenv("CLOUDFLARE_ACCOUNT_ID");
        if (cloudflareAccountId == null || cloudflareAccountId.isBlank() || cloudflareAccountId.contains("INSERISCI_QUI")) {
            cloudflareAccountId = env.get("CLOUDFLARE_ACCOUNT_ID");
        }

        if (cloudflareApiToken != null && !cloudflareApiToken.isBlank()
                && cloudflareAccountId != null && !cloudflareAccountId.isBlank()) {
            JevProperties jevProps = new JevProperties(
                    "cloudflare",
                    cloudflareApiToken,
                    cloudflareAccountId,
                    "@cf/cloudflare/clef-flash",
                    null,
                    Duration.ofSeconds(10)
            );
            jevRouter = new JevRouter(jevProps);
        }
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> map = new HashMap<>();
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try (BufferedReader reader = Files.newBufferedReader(envFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
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

    private static boolean isOllamaLive() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://localhost:11434/api/tags")).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isGeminiLive() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            return false;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=" + geminiApiKey))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isJevLive() {
        return jevRouter != null;
    }

    @Test
    @Order(1)
    @DisplayName("Pre-requisito: Verifica disponibilità delle VERE API (Ollama, Gemini, Cloudflare Jev)")
    void testRealApisAvailability() {
        boolean ollamaOk = isOllamaLive();
        boolean geminiOk = isGeminiLive();
        boolean jevOk = isJevLive();

        log.info("Verifica API reali: Ollama Locale={}| Google Gemini AI Studio={}| Cloudflare Jev/Clef={}",
                ollamaOk ? "ONLINE" : "OFFLINE",
                geminiOk ? "ONLINE" : "OFFLINE",
                jevOk ? "ONLINE" : "OFFLINE");

        assumeTrue(ollamaOk, "Ollama non è attivo su http://localhost:11434. Assicurarsi di aver avviato 'ollama serve'.");
        assumeTrue(geminiOk, "Google Gemini API Key non valida o servizio non raggiungibile.");
        assumeTrue(jevOk, "Credenziali Cloudflare Jev non configurate in .env.");
    }

    @Test
    @Order(2)
    @DisplayName("Suite di 10 Prompt: Valutazione Precisione Reale a 3 Vie (Ollama Locale vs Google Gemini vs Cloudflare Jev)")
    void testTenPromptsAccuracyThreeWayComparison() {
        assumeTrue(isOllamaLive(), "Ollama offline, salto test live.");
        assumeTrue(isGeminiLive(), "Gemini API offline o key mancante, salto test live.");
        assumeTrue(isJevLive(), "Jev/Cloudflare router non configurato, salto test live.");

        int totalPrompts = BENCHMARK_10_PROMPTS.size();
        int localCorrect = 0;
        int geminiCorrect = 0;
        int jevCorrect = 0;
        int allThreeAgreed = 0;

        List<ThreeWayRecord> records = new ArrayList<>();

        for (PromptBenchmark testCase : BENCHMARK_10_PROMPTS) {
            // 1. Esecuzione Ollama Locale
            long startLocal = System.currentTimeMillis();
            RoutingDecision localDecision = localOllamaRouter.routeWithDecision(testCase.payload());
            long latLocal = System.currentTimeMillis() - startLocal;
            assertNotEquals(LocalOllamaRouter.ROUTER_NAME_FALLBACK, localDecision.routerName(),
                    "Ollama ha utilizzato il fallback euristico su prompt " + testCase.id() + "!");

            // 2. Esecuzione Google Gemini
            long startGemini = System.currentTimeMillis();
            RoutingDecision geminiDecision = geminiRouter.routeWithDecision(testCase.payload());
            long latGemini = System.currentTimeMillis() - startGemini;
            assertNotEquals(GeminiRouter.ROUTER_NAME_FALLBACK, geminiDecision.routerName(),
                    "Gemini ha utilizzato il fallback euristico su prompt " + testCase.id() + "!");

            // 3. Esecuzione Jev / Cloudflare Workers AI
            long startJev = System.currentTimeMillis();
            RoutingDecision jevDecision = jevRouter.routeWithDecision(testCase.payload());
            long latJev = System.currentTimeMillis() - startJev;
            assertNotEquals(JevRouter.ROUTER_NAME_FALLBACK, jevDecision.routerName(),
                    "Jev/Cloudflare ha utilizzato il fallback euristico su prompt " + testCase.id() + "!");

            boolean locOk = localDecision.documentType() == testCase.expectedPipeline();
            boolean gemOk = geminiDecision.documentType() == testCase.expectedPipeline();
            boolean jevOk = jevDecision.documentType() == testCase.expectedPipeline();
            boolean agreeAll = (localDecision.documentType() == geminiDecision.documentType())
                    && (geminiDecision.documentType() == jevDecision.documentType());

            if (locOk) localCorrect++;
            if (gemOk) geminiCorrect++;
            if (jevOk) jevCorrect++;
            if (agreeAll) allThreeAgreed++;

            records.add(new ThreeWayRecord(
                    testCase.index(),
                    testCase.id(),
                    testCase.expectedPipeline(),
                    localDecision.documentType(),
                    geminiDecision.documentType(),
                    jevDecision.documentType(),
                    locOk,
                    gemOk,
                    jevOk,
                    agreeAll,
                    latLocal,
                    latGemini,
                    latJev,
                    testCase.description()
            ));
        }

        // Costruzione del report dettagliato a tabella
        StringBuilder sb = new StringBuilder();
        sb.append("\n=========================================================================================================================\n");
        sb.append(String.format("%-4s | %-7s | %-12s | %-14s | %-14s | %-14s | %-7s | %-8s | %-8s | %-8s\n",
                "#", "ID", "Atteso", "Ollama Locale", "Google Gemini", "Cloudflare Jev", "Accordo", "Lat.Loc", "Lat.Gem", "Lat.Jev"));
        sb.append("-------------------------------------------------------------------------------------------------------------------------\n");

        for (ThreeWayRecord r : records) {
            sb.append(String.format("%-4d | %-7s | %-12s | %-14s | %-14s | %-14s | %-7s | %6dms | %6dms | %6dms | %s\n",
                    r.index(),
                    r.id(),
                    r.expected(),
                    r.localActual() + (r.localOk() ? " [✓]" : " [✗]"),
                    r.geminiActual() + (r.geminiOk() ? " [✓]" : " [✗]"),
                    r.jevActual() + (r.jevOk() ? " [✓]" : " [✗]"),
                    r.agreeAll() ? "SI" : "NO",
                    r.latLocalMs(),
                    r.latGeminiMs(),
                    r.latJevMs(),
                    r.description()
            ));
        }
        sb.append("=========================================================================================================================\n");

        double localAccuracy = (localCorrect * 100.0) / totalPrompts;
        double geminiAccuracy = (geminiCorrect * 100.0) / totalPrompts;
        double jevAccuracy = (jevCorrect * 100.0) / totalPrompts;
        double agreementRate = (allThreeAgreed * 100.0) / totalPrompts;

        long avgLatLoc = records.stream().mapToLong(ThreeWayRecord::latLocalMs).sum() / totalPrompts;
        long avgLatGem = records.stream().mapToLong(ThreeWayRecord::latGeminiMs).sum() / totalPrompts;
        long avgLatJev = records.stream().mapToLong(ThreeWayRecord::latJevMs).sum() / totalPrompts;

        sb.append(String.format("STATISTICHE FINALI (Totale prompt: %d):\n", totalPrompts));
        sb.append(String.format(" - Precisione Ollama Locale (qwen2.5-router) : %d/%d (%.1f%%) | Latenza media: %d ms\n", localCorrect, totalPrompts, localAccuracy, avgLatLoc));
        sb.append(String.format(" - Precisione Google Gemini (gemini-3.5-lite) : %d/%d (%.1f%%) | Latenza media: %d ms\n", geminiCorrect, totalPrompts, geminiAccuracy, avgLatGem));
        sb.append(String.format(" - Precisione Cloudflare Jev (clef-flash)    : %d/%d (%.1f%%) | Latenza media: %d ms\n", jevCorrect, totalPrompts, jevAccuracy, avgLatJev));
        sb.append(String.format(" - Concordanza Totale a 3 Vie                : %d/%d (%.1f%%)\n", allThreeAgreed, totalPrompts, agreementRate));
        sb.append("=========================================================================================================================\n");

        System.out.println(sb);
        log.info("{}", sb);

        // Asserzioni di qualità per la suite a 3 vie
        assertTrue(geminiAccuracy >= 90.0, "Google Gemini deve avere una precisione di classificazione >= 90%");
        assertTrue(localAccuracy >= 80.0, "Ollama Locale deve avere una precisione di classificazione >= 80%");
        assertTrue(jevAccuracy >= 90.0, "Cloudflare Jev deve avere una precisione di classificazione >= 90%");
    }

    private record ThreeWayRecord(
            int index,
            String id,
            DocumentType expected,
            DocumentType localActual,
            DocumentType geminiActual,
            DocumentType jevActual,
            boolean localOk,
            boolean geminiOk,
            boolean jevOk,
            boolean agreeAll,
            long latLocalMs,
            long latGeminiMs,
            long latJevMs,
            String description
    ) {}
}
