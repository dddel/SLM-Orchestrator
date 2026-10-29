package com.tirocinioaltrama.llmorchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tirocinioaltrama.llmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.llmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.llmorchestrator.core.RouterType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("live")
public class CellLiveRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(CellLiveRunnerTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private OllamaProperties ollamaProperties;

    @Autowired
    private Environment environment;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ComparisonRunner runner;
    private String promptMonolitico;
    private JsonNode schemaBustaNode;
    private String schemaBustaText;

    private static final String WARMUP_DOC = "Gentile supporto, confermo la ricezione della fattura e procedo al saldo.";

    @BeforeEach
    void setUp() throws Exception {
        runner = new ComparisonRunner(mapper, Path.of("results/confronti"));

        Path promptPath = Path.of("src/test/resources/eval/prompt-monolitico.txt");
        promptMonolitico = Files.readString(promptPath);

        Path schemaPath = Path.of("src/test/resources/eval/schema-busta.json");
        schemaBustaText = Files.readString(schemaPath);
        schemaBustaNode = mapper.readTree(schemaBustaText);
    }

    private boolean isOllamaRunning() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaProperties.baseUrl() + "/api/tags"))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Fase 5: Prova di Impianto Smoke Test (1 Fattura, 1 Email, 1 Anagrafica) su Celle A, B, C, D, E")
    void runSmokePlantTrial() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile su " + ollamaProperties.baseUrl() + ". Salto prova di impianto.");

        String geminiApiKey = GeminiModelClient.resolveApiKey();
        assumeTrue(geminiApiKey != null && !geminiApiKey.isBlank(), "GEMINI_API_KEY non disponibile. Salto prova cloud.");

        log.info("=== AVVIO FASE 5: PROVA DI IMPIANTO (SMOKE TEST 3 DOCUMENTI DEV) ===");

        Path datasetPath = Path.of("data/dataset_60_v2.json");
        ComparisonRunner.verifyDatasetSha256(datasetPath);

        List<Map<String, Object>> allDocs = runner.loadDataset(datasetPath);
        Set<String> smokeIds = Set.of("FAT_01", "EML_01", "ANA_01");
        List<Map<String, Object>> smokeDocs = allDocs.stream()
                .filter(d -> smokeIds.contains(d.get("id")))
                .toList();

        assertEquals(3, smokeDocs.size(), "I 3 documenti di smoke test non sono stati tutti individuati!");

        Path smokeBaseDir = Path.of("results/confronti/smoke");
        ComparisonRunner smokeRunner = new ComparisonRunner(mapper, smokeBaseDir);

        executeCellA(smokeRunner, "smoke", smokeDocs);
        executeCellB(smokeRunner, "smoke", smokeDocs);
        smokeRunner.derivePostHocRun("B", "C", "smoke", smokeDocs);
        executeCellD(smokeRunner, "smoke", smokeDocs);
        smokeRunner.derivePostHocRun("D", "E", "smoke", smokeDocs);

        for (String cella : List.of("A", "B", "C", "D", "E")) {
            Path summaryPath = smokeBaseDir.resolve(cella).resolve("smoke/summary.json");
            assertTrue(Files.exists(summaryPath), "File summary.json mancante per cella " + cella);
        }
        log.info("=== PROVA DI IMPIANTO COMPLETATA CON SUCCESSO SULLE 5 CELLE ===");
    }

    @Test
    @DisplayName("Esecuzione Benchmark Ufficiale Completo sui 60 Documenti di Sviluppo (Celle A, B, C, D, E)")
    void runFullBenchmarkDevSet() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile. Salto run.");
        String geminiApiKey = GeminiModelClient.resolveApiKey();
        assumeTrue(geminiApiKey != null && !geminiApiKey.isBlank(), "GEMINI_API_KEY non disponibile.");

        Path datasetPath = Path.of("data/dataset_60_v2.json");
        ComparisonRunner.verifyDatasetSha256(datasetPath);
        List<Map<String, Object>> docs = runner.loadDataset(datasetPath);
        assertEquals(60, docs.size(), "Il dataset deve contenere esattamente 60 documenti!");

        log.info("==========================================================================");
        log.info("AVVIO ESECUZIONE BENCHMARK UFFICIALE SU 60 DOCUMENTI DEV (ORDINE NATURALE)");
        log.info("==========================================================================");

        // 1. Cella A (App 3B con Pipeline, Router e Smart Retry)
        log.info("\n--- Avvio Esecuzione Cella A (App 3B Neuro-Simbolica) ---");
        executeCellA(runner, "naturale", docs);

        // 2. Cella B (Locale 7B Q8_0 Monolitico)
        log.info("\n--- Avvio Esecuzione Cella B (Qwen 7B Q8_0 Monolitico) ---");
        executeCellB(runner, "naturale", docs);

        // 3. Cella C (Derivazione da B + Guardrail a posteriori)
        log.info("\n--- Avvio Derivazione Cella C (Qwen 7B + Guardrails) ---");
        runner.derivePostHocRun("B", "C", "naturale", docs);

        // 4. Cella D (Cloud Gemini - gemini-3-flash-preview)
        log.info("\n--- Avvio Esecuzione Cella D (Cloud Gemini) ---");
        executeCellD(runner, "naturale", docs);

        // 5. Cella E (Derivazione da D + Guardrail a posteriori)
        log.info("\n--- Avvio Derivazione Cella E (Cloud Gemini + Guardrails) ---");
        runner.derivePostHocRun("D", "E", "naturale", docs);

        // 6. Batteria Avversariale su tutte le celle
        log.info("\n--- Esecuzione Batteria Avversariale (data/adversarial_piva_set.json) ---");
        runAdversarialEvaluation();

        log.info("==========================================================================");
        log.info("BENCHMARK COMPARATIVO COMPLETATO SU TUTTE LE CELLE (A, B, C, D, E)");
        log.info("==========================================================================");
    }

    @Test
    @DisplayName("Esecuzione Valutazione Avversariale (A, B, C, D, E)")
    void testAdversarialRun() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile.");
        runAdversarialEvaluation();
    }

    private void runAdversarialEvaluation() throws Exception {
        Path advPath = Path.of("data/adversarial_piva_set.json");
        if (!Files.exists(advPath)) {
            log.warn("Dataset avversariale {} non trovato, salto.", advPath);
            return;
        }

        List<Map<String, Object>> advDocs = runner.loadDataset(advPath);
        log.info("Caricati {} documenti avversariali.", advDocs.size());

        Path advDir = Path.of("results/confronti/adversarial");
        ComparisonRunner advRunner = new ComparisonRunner(mapper, advDir);

        // Esegui Cella A su adv set
        log.info("Esecuzione Cella A su adv set...");
        executeCellA(advRunner, "adv", advDocs);

        // Esegui Cella B su adv set
        log.info("Esecuzione Cella B su adv set...");
        executeCellB(advRunner, "adv", advDocs);

        // Deriva Cella C
        log.info("Derivazione Cella C su adv set...");
        advRunner.derivePostHocRun("B", "C", "adv", advDocs);

        // Esegui Cella D ed E con protezione quota
        try {
            log.info("Esecuzione Cella D su adv set...");
            executeCellD(advRunner, "adv", advDocs);
            advRunner.derivePostHocRun("D", "E", "adv", advDocs);
        } catch (Exception e) {
            log.warn("Cella D/E non completata su adv set causa quota API Google: {}", e.getMessage());
        }

        // Raccogli e calcola il tasso di accettazione di codici invalidi
        Map<String, Object> advSummary = new LinkedHashMap<>();
        for (String cella : List.of("A", "B", "C", "D", "E")) {
            Path sumFile = advDir.resolve(cella).resolve("adv/summary.json");
            if (Files.exists(sumFile)) {
                JsonNode sn = mapper.readTree(sumFile.toFile());
                int okCount = sn.path("classification_counts").path("C1").asInt(0)
                        + sn.path("classification_counts").path("C2").asInt(0);
                int total = sn.path("metadata").path("total_documents").asInt(advDocs.size());
                double rate = ((double) okCount / total) * 100.0;
                String wilson = WilsonScore.format(okCount, total);

                advSummary.put(cella, Map.of(
                        "total_docs", total,
                        "accepted_false_codes", okCount,
                        "acceptance_rate_pct", rate,
                        "acceptance_wilson", wilson
                ));
            }
        }

        Path summaryOut = advDir.resolve("adversarial_summary.json");
        mapper.writeValue(summaryOut.toFile(), advSummary);
        log.info("Riepilogo avversariale salvato in {}", summaryOut.toAbsolutePath());
    }

    public void executeCellA(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
        assertEquals(2048, ollamaProperties.numCtx());
        assertEquals(400, ollamaProperties.numPredict());
        assertEquals("60m", ollamaProperties.keepAlive());
        assertEquals(0.0, ollamaProperties.temperature());
        assertEquals(42, ollamaProperties.seed());
        assertEquals(1.0, ollamaProperties.repeatPenalty());

        String routerType = environment.getProperty("router.type", "local-ollama");
        boolean guardsEnabled = environment.getProperty("orchestrator.guards.enabled", Boolean.class, true);
        boolean coherenceEnabled = environment.getProperty("orchestrator.coherence.enabled", Boolean.class, true);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", "A");
        metadata.put("ordine", ordine);
        metadata.put("model_id", ollamaProperties.baseModel());
        metadata.put("endpoint", "http://localhost:" + port + "/api/orchestrator/process");
        metadata.put("git_commit", ComparisonRunner.getGitHeadCommit());
        metadata.put("evaluated_at", Instant.now().toString());
        metadata.put("parameters", Map.of(
                "num_ctx", ollamaProperties.numCtx(),
                "num_predict", ollamaProperties.numPredict(),
                "keep_alive", ollamaProperties.keepAlive(),
                "temperature", ollamaProperties.temperature(),
                "seed", ollamaProperties.seed(),
                "repeat_penalty", ollamaProperties.repeatPenalty(),
                "router_type", routerType,
                "guards_enabled", guardsEnabled,
                "coherence_enabled", coherenceEnabled
        ));

        // Warmup fuori dal dataset
        sendOrchestratorRequest(WARMUP_DOC);

        List<Map<String, Object>> evaluatedDocs = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> doc : docs) {
            i++;
            String docId = (String) doc.get("id");
            String payload = (String) doc.get("payload");
            String trueType = (String) doc.get("type");
            Map<String, Object> groundTruth = (Map<String, Object>) doc.get("ground_truth");

            long start = System.currentTimeMillis();
            HttpResponse<String> resp = sendOrchestratorRequest(payload);
            long clientLatency = System.currentTimeMillis() - start;

            JsonNode respNode = mapper.readTree(resp.body());
            String status = respNode.path("status").asText("INDETERMINATE");
            String resultJson = respNode.path("result").asText(respNode.path("unvalidatedResult").asText("{}"));

            JsonNode extractedNode = mapper.readTree(resultJson);
            Map<String, Object> flatExtracted = GroundTruthComparator.flattenJson(extractedNode);

            int inferenceSteps = 0;
            JsonNode steps = respNode.path("metrics").path("steps");
            if (steps.isArray()) {
                for (JsonNode s : steps) {
                    if ("MODEL_INFERENCE".equalsIgnoreCase(s.path("type").asText())) {
                        inferenceSteps++;
                    }
                }
            }
            if (inferenceSteps == 0) inferenceSteps = 1;

            Map<String, Object> evalDoc = sRunner.evaluateDocument(
                    docId, trueType, (String) doc.get("template"), "dev", status,
                    flatExtracted, groundTruth, respNode, inferenceSteps
            );
            evalDoc.put("client_latency_ms", clientLatency);
            evaluatedDocs.add(evalDoc);

            if (i % 10 == 0 || i == docs.size()) {
                log.info("Cella A [{}] completati {}/{} documenti (ultimo doc: {}, latenza: {} ms, status: {})",
                        ordine, i, docs.size(), docId, clientLatency, status);
            }
        }

        sRunner.saveRunResults("A", ordine, metadata, evaluatedDocs);
    }

    public void executeCellB(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
        OllamaMonolithicClient bClient = OllamaMonolithicClient.defaultInstance(mapper);
        bClient.stopOtherModels("qwen2.5:7b-instruct-q8_0");

        // Warmup fuori dal dataset
        bClient.generate(promptMonolitico, WARMUP_DOC, schemaBustaText);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", "B");
        metadata.put("ordine", ordine);
        metadata.put("model_id", "qwen2.5:7b-instruct-q8_0");
        metadata.put("git_commit", ComparisonRunner.getGitHeadCommit());
        metadata.put("evaluated_at", Instant.now().toString());
        metadata.put("parameters", Map.of(
                "num_ctx", 2048,
                "num_predict", 400,
                "keep_alive", "60m",
                "temperature", 0.0,
                "seed", 42,
                "repeat_penalty", 1.0
        ));

        List<Map<String, Object>> evaluatedDocs = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> doc : docs) {
            i++;
            String docId = (String) doc.get("id");
            String payload = (String) doc.get("payload");
            String trueType = (String) doc.get("type");
            Map<String, Object> groundTruth = (Map<String, Object>) doc.get("ground_truth");

            var bResp = bClient.generate(promptMonolitico, payload, schemaBustaText);

            JsonNode rawNode = mapper.readTree(bResp.rawJson());
            String classifiedType = rawNode.path("tipoDocumento").asText("ALTRO").toUpperCase(Locale.ROOT);
            JsonNode subNode = rawNode.get(classifiedType.toLowerCase(Locale.ROOT));

            Map<String, Object> flatExtracted = Collections.emptyMap();
            if (classifiedType.equalsIgnoreCase(trueType) && subNode != null && subNode.isObject()) {
                flatExtracted = GroundTruthComparator.flattenJson(subNode);
            }

            Map<String, Object> rawRespMap = new LinkedHashMap<>();
            rawRespMap.put("raw_output", bResp.rawJson());
            rawRespMap.put("result", bResp.rawJson());
            rawRespMap.put("metrics", bResp.metrics());
            rawRespMap.put("classified_type", classifiedType);

            Map<String, Object> evalDoc = sRunner.evaluateDocument(
                    docId, trueType, (String) doc.get("template"), "dev", "OK",
                    flatExtracted, groundTruth, rawRespMap, 1
            );
            evalDoc.put("client_latency_ms", bResp.metrics().clientLatencyMs());
            evaluatedDocs.add(evalDoc);

            if (i % 10 == 0 || i == docs.size()) {
                log.info("Cella B [{}] completati {}/{} documenti (ultimo doc: {}, latenza: {} ms)",
                        ordine, i, docs.size(), docId, bResp.metrics().clientLatencyMs());
            }
        }

        sRunner.saveRunResults("B", ordine, metadata, evaluatedDocs);
    }

    public void executeCellD(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
        String cloudModelId = System.getProperty("eval.gemini.model", "gemini-3-flash-preview");
        int rpm = Integer.getInteger("eval.gemini.rpm", 4);
        GeminiModelClient dClient = new GeminiModelClient(mapper, cloudModelId, rpm);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", "D");
        metadata.put("ordine", ordine);
        metadata.put("model_id", cloudModelId);
        metadata.put("git_commit", ComparisonRunner.getGitHeadCommit());
        metadata.put("evaluated_at", Instant.now().toString());
        metadata.put("parameters", Map.of(
                "temperature", 0.0,
                "thinking_budget", 0,
                "rpm_limit", rpm
        ));

        List<Map<String, Object>> evaluatedDocs = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> doc : docs) {
            i++;
            String docId = (String) doc.get("id");
            String payload = (String) doc.get("payload");
            String trueType = (String) doc.get("type");
            Map<String, Object> groundTruth = (Map<String, Object>) doc.get("ground_truth");

            var dResp = dClient.generate(promptMonolitico, payload, schemaBustaNode);

            JsonNode rawNode = mapper.readTree(dResp.text());
            String classifiedType = rawNode.path("tipoDocumento").asText("ALTRO").toUpperCase(Locale.ROOT);
            JsonNode subNode = rawNode.get(classifiedType.toLowerCase(Locale.ROOT));

            Map<String, Object> flatExtracted = Collections.emptyMap();
            if (classifiedType.equalsIgnoreCase(trueType) && subNode != null && subNode.isObject()) {
                flatExtracted = GroundTruthComparator.flattenJson(subNode);
            }

            Map<String, Object> rawRespMap = new LinkedHashMap<>();
            rawRespMap.put("raw_output", dResp.text());
            rawRespMap.put("result", dResp.text());
            rawRespMap.put("model_version", dResp.modelVersion());
            rawRespMap.put("usage", dResp.usage());
            rawRespMap.put("latency_ms", dResp.latencyMs());
            rawRespMap.put("backoff_retries", dResp.backoffRetries());

            Map<String, Object> evalDoc = sRunner.evaluateDocument(
                    docId, trueType, (String) doc.get("template"), "dev", "OK",
                    flatExtracted, groundTruth, rawRespMap, 1
            );
            evalDoc.put("client_latency_ms", dResp.latencyMs());
            evaluatedDocs.add(evalDoc);

            if (i % 10 == 0 || i == docs.size()) {
                log.info("Cella D [{}] completati {}/{} documenti (ultimo doc: {}, latenza: {} ms)",
                        ordine, i, docs.size(), docId, dResp.latencyMs());
            }
        }

        metadata.put("model_version", evaluatedDocs.isEmpty() ? cloudModelId :
                ((Map<?, ?>) evaluatedDocs.get(0).get("raw_response")).get("model_version"));
        metadata.put("total_backoff_429_count", dClient.getBackoff429Count());

        sRunner.saveRunResults("D", ordine, metadata, evaluatedDocs);
    }

    private HttpResponse<String> sendOrchestratorRequest(String text) throws Exception {
        OrchestratorRequest request = new OrchestratorRequest(text, RouterType.LOCAL_OLLAMA);
        String body = mapper.writeValueAsString(request);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/orchestrator/process"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
