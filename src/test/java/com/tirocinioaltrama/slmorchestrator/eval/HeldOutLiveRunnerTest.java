package com.tirocinioaltrama.slmorchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.tirocinioaltrama.slmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.RouterType;
import com.tirocinioaltrama.slmorchestrator.core.RoutingDecision;
import com.tirocinioaltrama.slmorchestrator.impl.router.LocalOllamaRouter;

/**
 * Runner sperimentale per il Passo 5 (Valutazione Cieca Out-of-Sample sul Dataset Held-Out):
 * - Batteria 5.1: Estrazione Principale su 42 documenti held-out (Celle A, B, C)
 * - Batteria 5.2: Robustezza Avversariale Multi-Dominio su 6 documenti (Celle A, B, C: P.IVA, CF, IBAN invalidi)
 * - Batteria 5.3: Accuratezza Router Locale a 4 Classi su 50 documenti (42 held-out + 8 ALTRO)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("live")
public class HeldOutLiveRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(HeldOutLiveRunnerTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private OllamaProperties ollamaProperties;

    @Autowired
    private Environment environment;

    @Autowired(required = false)
    private LocalOllamaRouter localOllamaRouter;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ComparisonRunner runner;
    private String promptMonolitico;
    private String schemaBustaText;

    private static final String WARMUP_DOC = "Gentile supporto, confermo la ricezione della fattura e procedo al saldo.";

    @BeforeEach
    void setUp() throws Exception {
        runner = new ComparisonRunner(mapper, Path.of("results/heldout"));

        Path promptPath = Path.of("src/test/resources/eval/prompt-monolitico.txt");
        promptMonolitico = Files.readString(promptPath);

        Path schemaPath = Path.of("src/test/resources/eval/schema-busta.json");
        schemaBustaText = Files.readString(schemaPath);
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
    @DisplayName("Passo 5 - Batterie 5.1 e 5.2: Estrazione Held-Out (42 doc) e Avversariale (6 doc) per Celle A, B e C")
    void runHeldOutExtractionAndAdversarial() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile su " + ollamaProperties.baseUrl());

        // Verifica rigorosa SHA-256 dei dataset Held-Out congelati
        Path heldoutPath = Path.of("data/heldout_dataset_v1.json");
        ComparisonRunner.verifyDatasetSha256(heldoutPath, ComparisonRunner.EXPECTED_HELDOUT_DATASET_SHA256);

        Path advPath = Path.of("data/heldout_adversarial_v1.json");
        ComparisonRunner.verifyDatasetSha256(advPath, ComparisonRunner.EXPECTED_HELDOUT_ADVERSARIAL_SHA256);

        List<Map<String, Object>> heldoutDocs = runner.loadDataset(heldoutPath);
        assertEquals(42, heldoutDocs.size(), "Il dataset held-out deve contenere esattamente 42 documenti!");

        List<Map<String, Object>> advDocs = runner.loadDataset(advPath);
        assertEquals(6, advDocs.size(), "Il dataset avversariale deve contenere esattamente 6 documenti!");

        log.info("==========================================================================");
        log.info("AVVIO PASSO 5: VALUTAZIONE CIECA OUT-OF-SAMPLE (CELLE A, B, C)");
        log.info("Dataset: {} (42 doc, 392 campi)", heldoutPath);
        log.info("Avversariale: {} (6 doc: 2 P.IVA, 2 CF, 2 IBAN invalidi)", advPath);
        log.info("==========================================================================");

        // -----------------------------------------------------------------------
        // 1. Cella A (App 3B Neuro-Simbolica)
        // -----------------------------------------------------------------------
        log.info("\n--- Avvio Cella A: App 3B Neuro-Simbolica su 42 documenti Held-Out ---");
        OllamaMonolithicClient.defaultInstance(mapper).stopOtherModels(ollamaProperties.baseModel());
        executeCellA(runner, "naturale", heldoutDocs);

        log.info("\n--- Avvio Cella A: App 3B Neuro-Simbolica su 6 documenti Avversariali ---");
        Path advDir = Path.of("results/heldout/adversarial");
        ComparisonRunner advRunner = new ComparisonRunner(mapper, advDir);
        executeCellA(advRunner, "adv", advDocs);

        // -----------------------------------------------------------------------
        // 2. Cella B (Monolitico 7B Q8_0 no-guards)
        // -----------------------------------------------------------------------
        log.info("\n--- Avvio Cella B: Monolitico 7B Q8_0 su 42 documenti Held-Out ---");
        executeCellB(runner, "naturale", heldoutDocs);

        log.info("\n--- Avvio Cella B: Monolitico 7B Q8_0 su 6 documenti Avversariali ---");
        executeCellB(advRunner, "adv", advDocs);

        // -----------------------------------------------------------------------
        // 3. Cella C (Monolitico 7B Q8_0 + Guardrail post-hoc derivati da B)
        // -----------------------------------------------------------------------
        log.info("\n--- Avvio Derivazione Cella C su 42 documenti Held-Out ---");
        runner.derivePostHocRun("B", "C", "naturale", heldoutDocs);

        log.info("\n--- Avvio Derivazione Cella C su 6 documenti Avversariali ---");
        advRunner.derivePostHocRun("B", "C", "adv", advDocs);

        // Genera il summary avversariale parziale per A, B, C
        generateAdversarialSummary(advDir, advDocs, List.of("A", "B", "C"));

        // Verifiche file generati
        for (String cella : List.of("A", "B", "C")) {
            Path summaryPath = Path.of("results/heldout").resolve(cella).resolve("naturale/summary.json");
            assertTrue(Files.exists(summaryPath), "File summary.json mancante per Cella " + cella + ": " + summaryPath);

            Path advSummaryPath = advDir.resolve(cella).resolve("adv/summary.json");
            assertTrue(Files.exists(advSummaryPath), "File summary.json avversariale mancante per Cella " + cella + ": " + advSummaryPath);
        }

        log.info("==========================================================================");
        log.info("BATTERIE 5.1 E 5.2 COMPLETATE CON SUCCESSO PER CELLE A, B, C");
        log.info("==========================================================================");
    }

    @Test
    @DisplayName("Passo 5 - Batteria 5.3: Accuratezza Router Locale a 4 Classi su 50 documenti Out-of-Sample")
    void runHeldOutRouterEvaluation() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile.");

        Path heldoutPath = Path.of("data/heldout_dataset_v1.json");
        ComparisonRunner.verifyDatasetSha256(heldoutPath, ComparisonRunner.EXPECTED_HELDOUT_DATASET_SHA256);

        Path altroPath = Path.of("data/heldout_router_altro_v1.json");
        ComparisonRunner.verifyDatasetSha256(altroPath, ComparisonRunner.EXPECTED_HELDOUT_ROUTER_ALTRO_SHA256);

        List<Map<String, Object>> heldoutDocs = runner.loadDataset(heldoutPath);
        List<Map<String, Object>> altroDocs = runner.loadDataset(altroPath);
        assertEquals(42, heldoutDocs.size());
        assertEquals(8, altroDocs.size());

        List<Map<String, Object>> allRouterDocs = new ArrayList<>(50);
        allRouterDocs.addAll(heldoutDocs);
        allRouterDocs.addAll(altroDocs);
        assertEquals(50, allRouterDocs.size(), "Il set combinato per la valutazione del router deve contenere esattamente 50 documenti!");

        log.info("==========================================================================");
        log.info("AVVIO BATTERIA 5.3: VALUTAZIONE ROUTER LOCALE A 4 CLASSI (50 DOCUMENTI)");
        log.info("14 FATTURA, 14 ANAGRAFICA, 14 EMAIL, 8 ALTRO");
        log.info("==========================================================================");

        LocalOllamaRouter routerToTest = this.localOllamaRouter;
        if (routerToTest == null) {
            routerToTest = new LocalOllamaRouter(
                    ollamaProperties.baseUrl(),
                    ollamaProperties.routerModel(),
                    ollamaProperties.connectTimeout()
            );
        }

        // Warmup fuori dal dataset
        routerToTest.routeWithDecision(WARMUP_DOC);

        List<String> categories = List.of("FATTURA", "EMAIL", "ANAGRAFICA", "ALTRO");
        Map<String, Map<String, Integer>> confusionMatrix = new LinkedHashMap<>();
        for (String actual : categories) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String pred : categories) {
                row.put(pred, 0);
            }
            confusionMatrix.put(actual, row);
        }

        int correctCount = 0;
        List<Long> latenciesMs = new ArrayList<>();
        List<Map<String, Object>> itemDetails = new ArrayList<>();

        int i = 0;
        for (Map<String, Object> doc : allRouterDocs) {
            i++;
            String docId = (String) doc.get("id");
            String payload = (String) doc.get("payload");
            String expectedType = ((String) doc.get("type")).toUpperCase(Locale.ROOT);

            long start = System.currentTimeMillis();
            RoutingDecision decision = routerToTest.routeWithDecision(payload);
            long latency = System.currentTimeMillis() - start;
            latenciesMs.add(latency);

            String predictedType = decision.documentType().name();
            boolean match = expectedType.equalsIgnoreCase(predictedType);
            if (match) {
                correctCount++;
            }

            // Aggiorna matrice di confusione
            if (confusionMatrix.containsKey(expectedType) && confusionMatrix.get(expectedType).containsKey(predictedType)) {
                int curr = confusionMatrix.get(expectedType).get(predictedType);
                confusionMatrix.get(expectedType).put(predictedType, curr + 1);
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", docId);
            detail.put("expected", expectedType);
            detail.put("predicted", predictedType);
            detail.put("match", match);
            detail.put("router_name", decision.routerName());
            detail.put("latency_ms", latency);
            itemDetails.add(detail);

            log.info("Router doc {}/50 [{}]: atteso={}, predetto={}, match={} ({} ms)",
                    i, docId, expectedType, predictedType, match, latency);
        }

        // Statistiche di latenza
        Collections.sort(latenciesMs);
        double meanLatency = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long medianLatency = latenciesMs.get(latenciesMs.size() / 2);
        int p95Idx = (int) Math.ceil(0.95 * latenciesMs.size()) - 1;
        long p95Latency = latenciesMs.get(Math.max(0, Math.min(p95Idx, latenciesMs.size() - 1)));

        Map<String, Object> latencyStats = new LinkedHashMap<>();
        latencyStats.put("median_ms", medianLatency);
        latencyStats.put("mean_ms", Math.round(meanLatency * 10.0) / 10.0);
        latencyStats.put("p95_ms", p95Latency);

        double accuracyPct = ((double) correctCount / allRouterDocs.size()) * 100.0;
        String wilson = WilsonScore.format(correctCount, allRouterDocs.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("metadata", Map.of(
                "total_documents", allRouterDocs.size(),
                "model", routerToTest.getModel(),
                "evaluated_at", Instant.now().toString(),
                "git_commit", ComparisonRunner.getGitHeadCommit()
        ));
        summary.put("summary", Map.of(
                "total", allRouterDocs.size(),
                "correct", correctCount,
                "accuracy_pct", Math.round(accuracyPct * 10.0) / 10.0,
                "accuracy_wilson", wilson
        ));
        summary.put("confusion_matrix", confusionMatrix);
        summary.put("latency_stats", latencyStats);
        summary.put("documents", itemDetails);

        Path routerDir = Path.of("results/heldout/router");
        Files.createDirectories(routerDir);
        Path routerFile = routerDir.resolve("router_heldout_results.json");
        mapper.writeValue(routerFile.toFile(), summary);

        log.info("==========================================================================");
        log.info("BATTERIA 5.3 COMPLETATA CON SUCCESSO");
        log.info("Accuratezza Router: {}/{} ({}), Mediana: {} ms",
                correctCount, allRouterDocs.size(), wilson, medianLatency);
        log.info("Risultati salvati in: {}", routerFile.toAbsolutePath());
        log.info("==========================================================================");

        assertTrue(correctCount >= 45, "L'accuratezza del router fuori campione deve essere almeno 90% (>= 45/50)");
    }

    private void executeCellA(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", "A");
        metadata.put("split", "heldout");
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
                "router_type", environment.getProperty("router.type", "local-ollama"),
                "guards_enabled", environment.getProperty("orchestrator.guards.enabled", Boolean.class, true),
                "coherence_enabled", environment.getProperty("orchestrator.coherence.enabled", Boolean.class, true)
        ));

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
                    docId, trueType, (String) doc.get("template"), "heldout", status,
                    flatExtracted, groundTruth, respNode, inferenceSteps
            );
            evalDoc.put("client_latency_ms", clientLatency);
            evaluatedDocs.add(evalDoc);

            if (i % 5 == 0 || i == docs.size()) {
                log.info("Cella A [{}] {}/{} (doc: {}, latenza: {} ms, status: {}, match: {}/{})",
                        ordine, i, docs.size(), docId, clientLatency, status,
                        evalDoc.get("correct_fields"), evalDoc.get("total_fields"));
            }
        }

        sRunner.saveRunResults("A", ordine, metadata, evaluatedDocs);
    }

    private void executeCellB(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
        OllamaMonolithicClient bClient = OllamaMonolithicClient.defaultInstance(mapper);
        bClient.stopOtherModels("qwen2.5:7b-instruct-q8_0");

        bClient.generate(promptMonolitico, WARMUP_DOC, schemaBustaText);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", "B");
        metadata.put("split", "heldout");
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
                    docId, trueType, (String) doc.get("template"), "heldout", "OK",
                    flatExtracted, groundTruth, rawRespMap, 1
            );
            evalDoc.put("client_latency_ms", bResp.metrics().clientLatencyMs());
            evaluatedDocs.add(evalDoc);

            if (i % 5 == 0 || i == docs.size()) {
                log.info("Cella B [{}] {}/{} (doc: {}, latenza: {} ms, match: {}/{})",
                        ordine, i, docs.size(), docId, bResp.metrics().clientLatencyMs(),
                        evalDoc.get("correct_fields"), evalDoc.get("total_fields"));
            }
        }

        sRunner.saveRunResults("B", ordine, metadata, evaluatedDocs);
    }

    public static void generateAdversarialSummary(Path advDir, List<Map<String, Object>> advDocs, List<String> celle) throws Exception {
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Map<String, Object> advSummary = new LinkedHashMap<>();

        for (String cella : celle) {
            Path sumFile = advDir.resolve(cella).resolve("adv/summary.json");
            if (Files.exists(sumFile)) {
                JsonNode sn = om.readTree(sumFile.toFile());
                int c1 = sn.path("classification_counts").path("C1").asInt(0);
                int c2 = sn.path("classification_counts").path("C2").asInt(0);
                int c3 = sn.path("classification_counts").path("C3").asInt(0);
                int c4 = sn.path("classification_counts").path("C4").asInt(0);
                int c5 = sn.path("classification_counts").path("C5").asInt(0);

                int acceptedFalseCodes = c1 + c2;
                int rejectedFalseCodes = c3 + c4;
                int total = sn.path("metadata").path("total_documents").asInt(advDocs.size());
                double rate = total > 0 ? ((double) acceptedFalseCodes / total) * 100.0 : 0.0;
                String wilson = WilsonScore.format(acceptedFalseCodes, total);

                int correctFields = sn.path("fields_summary").path("correct_fields").asInt(0);
                int totalFields = sn.path("fields_summary").path("total_fields").asInt(0);
                String fieldsWilson = WilsonScore.format(correctFields, totalFields);

                advSummary.put(cella, Map.of(
                        "total_docs", total,
                        "accepted_false_codes (OK)", acceptedFalseCodes,
                        "rejected_false_codes (INDETERMINATE)", rejectedFalseCodes,
                        "acceptance_rate_pct", rate,
                        "acceptance_wilson", wilson,
                        "counts", Map.of("C1", c1, "C2", c2, "C3", c3, "C4", c4, "C5", c5),
                        "correct_fields", correctFields,
                        "total_fields", totalFields,
                        "accuracy_wilson", fieldsWilson
                ));
            }
        }

        Path summaryOut = advDir.resolve("adversarial_summary.json");
        om.writeValue(summaryOut.toFile(), advSummary);
        log.info("Riepilogo avversariale salvato in {}", summaryOut.toAbsolutePath());
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
