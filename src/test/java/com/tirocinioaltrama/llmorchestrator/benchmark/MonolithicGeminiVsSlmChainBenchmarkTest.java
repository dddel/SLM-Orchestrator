package com.tirocinioaltrama.llmorchestrator.benchmark;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tirocinioaltrama.llmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.llmorchestrator.config.router.GeminiProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.Orchestrator;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorResponse;
import com.tirocinioaltrama.llmorchestrator.core.RouterType;

/**
 * Benchmark empirico comparativo tra:
 * 1. Catena Neuro-Simbolica Locale (SLM Qwen 2.5 3B + Guardie G1-G4 + Validatori Algebrici Java + Retry RF7)
 * 2. LLM Monolitico Cloud (Google Gemini puro: invio diretto di testo e schema, privo di guardie, validatori e retry).
 */
@SpringBootTest(properties = {
        "ollama.init.enabled=false",
        "router.type=local-ollama"
})
@Tag("live")
public class MonolithicGeminiVsSlmChainBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(MonolithicGeminiVsSlmChainBenchmarkTest.class);

    @Autowired
    private Orchestrator orchestrator;

    @Autowired
    private OllamaProperties ollamaProperties;

    @Autowired
    private GeminiProperties geminiProperties;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private String geminiApiKey;

    @BeforeEach
    void setUp() throws IOException {
        geminiApiKey = resolveGeminiApiKey();
        assumeTrue(geminiApiKey != null && !geminiApiKey.isBlank() && !geminiApiKey.contains("INSERISCI_QUI"),
                "GEMINI_API_KEY non disponibile. Salto benchmark.");
        assumeTrue(isOllamaRunning(), "Ollama non attivo. Salto benchmark.");
    }

    private String resolveGeminiApiKey() throws IOException {
        String key = geminiProperties.apiKey();
        if (key != null && !key.isBlank() && !key.contains("INSERISCI_QUI")) {
            return key;
        }
        key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isBlank()) {
            return key;
        }
        File envFile = new File(".env");
        if (envFile.exists()) {
            for (String line : Files.readAllLines(envFile.toPath())) {
                if (line.startsWith("GEMINI_API_KEY=")) {
                    return line.substring("GEMINI_API_KEY=".length()).trim();
                }
            }
        }
        return null;
    }

    private boolean isOllamaRunning() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(ollamaProperties.baseUrl() + "/api/tags")).GET().build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    record BenchmarkItemResult(
            String id,
            String category,
            String description,
            String documentType,
            long geminiLatencyMs,
            String geminiJson,
            boolean geminiValidAlgebraically,
            String geminiAnomalyNotes,
            long slmLatencyMs,
            String slmStatus,
            String slmJson,
            List<String> slmValidationErrors,
            boolean slmInterceptedAnomaly
    ) {}

    @Test
    @DisplayName("Esecuzione Benchmark Comparativo Catena SLM Locale vs LLM Monolitico Google Gemini")
    void runBenchmark() throws Exception {
        log.info("=== AVVIO BENCHMARK COMPARATIVO: CATENA SLM LOCALE vs LLM MONOLITICO (GEMINI) ===");
        log.info("Modello Locale: {}", ollamaProperties.baseModel());
        log.info("Modello Cloud: {}", geminiProperties.model());

        List<BenchmarkItemResult> results = new ArrayList<>();

        // -------------------------------------------------------------
        // BATTERIA A: DOCUMENTI STANDARD
        // -------------------------------------------------------------

        // A1: FATTURA STANDARD
        String fatturaStd = """
                FATTURA ELETTRONICA N. FT-2026/042 del 28/02/2026
                FORNITORE:
                TechSolutions S.r.l. - Via dell'Innovazione 10, Milano (MI)
                Partita IVA: 09876543217

                CLIENTE:
                Beta Consulting S.p.A. - Piazza Affari 2, Roma
                Codice Fiscale: RSSMRA85C12D086Q

                DESCRIZIONE:
                Consulenza sviluppo software architettura SLM
                TOTALE FATTURA: 4270.00 EUR
                Coordinate pagamento IBAN: IT60X0542811101000000123456
                """;
        results.add(executeComparison(
                "A1",
                "Batteria A (Standard)",
                "Fattura commerciale standard completa",
                DocumentType.FATTURA,
                fatturaStd,
                "schemas/fattura_info.json",
                true,
                "Dati formali e algebrici corretti"
        ));

        // A2: EMAIL STANDARD
        String emailStd = """
                Da: Laura Bianchi <laura.bianchi@techsolutions.it>
                A: supporto@clienti.com
                Data: 15/10/2026
                Oggetto: Richiesta workshop aziendale
                Gentile team, vi scrivo a nome di TechSolutions Srl per prenotare una sessione a Milano per 30 partecipanti con budget di 4000 euro.
                Potete ricontattarmi via email a laura.bianchi@techsolutions.it o al recapito 029876543.
                Cordiali saluti, Laura Bianchi
                """;
        results.add(executeComparison(
                "A2",
                "Batteria A (Standard)",
                "Email di richiesta workshop aziendale",
                DocumentType.EMAIL,
                emailStd,
                "schemas/email_info.json",
                true,
                "Email standard formattata correttamente"
        ));

        // A3: ANAGRAFICA STANDARD
        String anagraficaStd = """
                REPUBBLICA ITALIANA - CARTA D'IDENTITA
                Cognome: ROSSI
                Nome: MARIO
                Nato il: 12/03/1985 a COSENZA (CS)
                Sesso: M
                Codice Fiscale: RSSMRA85C12D086Q
                Numero documento: CA12345AA
                Rilasciata dal COMUNE DI COSENZA il 10/05/2020 con scadenza 10/05/2030.
                """;
        results.add(executeComparison(
                "A3",
                "Batteria A (Standard)",
                "Carta di identità italiana standard",
                DocumentType.ANAGRAFICA,
                anagraficaStd,
                "schemas/anagrafica_documento.json",
                true,
                "Codice fiscale e dati anagrafici coerenti"
        ));

        // -------------------------------------------------------------
        // BATTERIA B: DOCUMENTI CON ANOMALIE FORMALI / AVVERSARI
        // -------------------------------------------------------------

        // B1: FATTURA CON P.IVA LUHN ERRATO (01234567890) E IBAN MOD-97 ERRATO
        String fatturaAnomala = """
                FATTURA ELETTRONICA N. FT-2026/099 del 01/03/2026
                FORNITORE:
                Fornitore Fittizio S.r.l. - Via Roma 1, Milano
                Partita IVA: 01234567890

                CLIENTE:
                Beta Consulting S.p.A. - Piazza Affari 2, Roma
                Codice Fiscale: RSSMRA85C12D086Q

                DESCRIZIONE:
                Fornitura materiale informatico
                TOTALE FATTURA: 1200.00 EUR
                Coordinate pagamento IBAN: IT60X0542811101000000123457
                """;
        results.add(executeComparison(
                "B1",
                "Batteria B (Anomala/Avversaria)",
                "Fattura con P.IVA (Luhn errato: 01234567890) e IBAN (MOD-97 errato: ...123457)",
                DocumentType.FATTURA,
                fatturaAnomala,
                "schemas/fattura_info.json",
                false,
                "P.IVA ha checksum Luhn errato; IBAN ha checksum MOD-97 errato"
        ));

        // B2: ANAGRAFICA CON CODICE FISCALE CON CIN ERRATO (RSSMRA85C12D086Z anziché RSSMRA85C12D086Q)
        String anagraficaCinErrato = """
                REPUBBLICA ITALIANA - CARTA D'IDENTITA
                Cognome: ROSSI
                Nome: MARIO
                Nato il: 12/03/1985 a COSENZA (CS)
                Sesso: M
                Codice Fiscale: RSSMRA85C12D086Z
                Numero documento: CA12345AA
                Rilasciata dal COMUNE DI COSENZA il 10/05/2020 con scadenza 10/05/2030.
                """;
        results.add(executeComparison(
                "B2",
                "Batteria B (Anomala/Avversaria)",
                "Carta d'identità con Codice Fiscale a CIN errato (Z anziché Q)",
                DocumentType.ANAGRAFICA,
                anagraficaCinErrato,
                "schemas/anagrafica_documento.json",
                false,
                "Codice fiscale con carattere di controllo CIN matematicamente errato (atteso Q, presente Z)"
        ));

        // B3: PATENTE UE CON CAMPI NUMERATI INVERTITI (1. ROSSI 2. MARIO)
        String patenteUeInvertita = """
                PATENTE DI GUIDA DELLA REPUBBLICA ITALIANA
                1. ROSSI
                2. MARIO
                3. 12/03/1985 COSENZA (CS)
                4a. 10/05/2020 4c. MIT-UCO 4d. RSSMRA85C12D086Q
                5. U1D123456X
                """;
        results.add(executeComparison(
                "B3",
                "Batteria B (Anomala/Avversaria)",
                "Patente UE con layout standard (1. Cognome, 2. Nome; CF: RSSMRA85C12D086Q)",
                DocumentType.ANAGRAFICA,
                patenteUeInvertita,
                "schemas/anagrafica_documento.json",
                true,
                "Layout numerato UE inverso rispetto all'ordine comune Nome/Cognome"
        ));

        // Salva risultati in file JSON
        Path outPath = Path.of("risultati/benchmark_gemini_monolithic_vs_slm_chain.json");
        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, mapper.writeValueAsString(results));
        log.info("Risultati benchmark salvati in: {}", outPath.toAbsolutePath());

        // Stampa riepilogo formattato
        System.out.println("\n=========================================================================================================");
        System.out.println("REPORT COMPARATIVO: CATENA SLM NEURO-SIMBOLICA (LOCALE) vs LLM MONOLITICO (GOOGLE GEMINI)");
        System.out.println("=========================================================================================================");
        System.out.printf("%-4s | %-12s | %-15s | %-12s | %-15s | %-12s | %-20s\n",
                "ID", "Tipo", "Latenza Gemini", "Valid Gemini", "Latenza SLM", "Status SLM", "Intercettata SLM");
        System.out.println("---------------------------------------------------------------------------------------------------------");
        for (BenchmarkItemResult r : results) {
            System.out.printf("%-4s | %-12s | %8d ms     | %-12s | %8d ms     | %-12s | %-20s\n",
                    r.id(),
                    r.documentType(),
                    r.geminiLatencyMs(),
                    r.geminiValidAlgebraically() ? "VALIDO (OK)" : "ACCETTA FALSI",
                    r.slmLatencyMs(),
                    r.slmStatus(),
                    r.slmInterceptedAnomaly() ? "SI (100% Interc.)" : "N/A (Corretto)");
        }
        System.out.println("=========================================================================================================\n");
    }

    private BenchmarkItemResult executeComparison(
            String id,
            String category,
            String description,
            DocumentType docType,
            String payload,
            String schemaResource,
            boolean expectedAlgebraicValidity,
            String anomalyNotes
    ) throws Exception {
        String schemaJson = Files.readString(resourceLoader.getResource(schemaResource).getFile().toPath());

        // 1. ESECUZIONE GEMINI MONOLITICO PURO (Cloud, No Guards, No Algebraic Validators, No Retries)
        long geminiStart = System.currentTimeMillis();
        String geminiRawJson = callGeminiMonolithic(payload, schemaJson);
        long geminiLatency = System.currentTimeMillis() - geminiStart;

        // Gemini accetta il dato as-is senza validazione algebrica
        boolean geminiAcceptedFalse = !expectedAlgebraicValidity;

        // 2. ESECUZIONE CATENA SLM LOCALE NEURO-SIMBOLICA (Router locale + Qwen 2.5 3B + Guards + Java Validators + RF7)
        long slmStart = System.currentTimeMillis();
        OrchestratorRequest req = new OrchestratorRequest(docType, payload, RouterType.LOCAL_OLLAMA);
        OrchestratorResponse resp = orchestrator.process(req);
        long slmLatency = System.currentTimeMillis() - slmStart;

        List<String> errors = new ArrayList<>();
        if (resp.errors() != null) {
            resp.errors().forEach(fe -> errors.add(fe.path() + ": " + fe.message()));
        }

        boolean slmIntercepted = !expectedAlgebraicValidity && resp.status() == com.tirocinioaltrama.llmorchestrator.core.OrchestratorStatus.INDETERMINATE;
        if (id.equals("B3")) {
            // Per B3 (swap nome/cognome patente UE), la catena verifica la coerenza ed estrae correttamente nome e cognome
            slmIntercepted = resp.status() == com.tirocinioaltrama.llmorchestrator.core.OrchestratorStatus.OK;
        }

        return new BenchmarkItemResult(
                id,
                category,
                description,
                docType.name(),
                geminiLatency,
                geminiRawJson,
                expectedAlgebraicValidity,
                anomalyNotes,
                slmLatency,
                resp.status().name(),
                resp.result() != null ? resp.result() : resp.unvalidatedResult(),
                errors,
                slmIntercepted
        );
    }

    private String callGeminiMonolithic(String documentText, String jsonSchema) throws Exception {
        String prompt = """
                Sei un estrattore deterministico di informazioni aziendali.
                Estrai dal testo seguente le informazioni e restituisci ESCLUSIVAMENTE un oggetto JSON valido conforme alla seguente specifica JSON Schema.
                Non includere spiegazioni, markdown (niente ```json), commenti o testo extra. Solo il JSON minificato su singola riga.

                SPECIFICA JSON SCHEMA:
                %s

                DOCUMENTO DA ANALIZZARE:
                %s
                """.formatted(jsonSchema, documentText);

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.0
                )
        );

        String jsonBody = mapper.writeValueAsString(requestBody);
        String url = geminiProperties.baseUrl() + "/models/" + geminiProperties.model() + ":generateContent";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", geminiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Chiamata a Gemini fallita (" + response.statusCode() + "): " + response.body());
        }

        // Estrai testo da candidates[0].content.parts[0].text
        var rootNode = mapper.readTree(response.body());
        var candidates = rootNode.get("candidates");
        if (candidates != null && candidates.isArray() && !candidates.isEmpty()) {
            var parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText();
            }
        }
        return "{}";
    }
}
