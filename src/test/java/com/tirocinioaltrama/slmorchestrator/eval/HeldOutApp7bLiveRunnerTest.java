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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tirocinioaltrama.slmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.RouterType;

/**
 * Runner sperimentale per Cella A_7B sul dataset Held-Out:
 * Valutazione della medesima architettura neuro-simbolica dell'applicazione
 * equipaggiata con il modello 'qwen2.5:7b-instruct-q8_0' su:
 * - 42 documenti Held-Out (Batteria 5.1)
 * - 6 documenti Avversariali Held-Out (Batteria 5.2)
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ollama.base-model=qwen2.5:7b-instruct-q8_0"
        }
)
@Tag("live")
@Tag("live-model")
public class HeldOutApp7bLiveRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(HeldOutApp7bLiveRunnerTest.class);

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
    private static final String WARMUP_DOC = "Gentile supporto, confermo la ricezione della fattura e procedo al saldo.";

    @BeforeEach
    void setUp() {
        runner = new ComparisonRunner(mapper, Path.of("results/heldout"));
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
    @DisplayName("Passo 5 - Cella A_7B: Benchmark Out-of-Sample su Held-Out (42 doc) e Avversariale (6 doc)")
    void runHeldOutApp7b() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile su " + ollamaProperties.baseUrl());
        assertEquals("qwen2.5:7b-instruct-q8_0", ollamaProperties.baseModel(),
                "Il modello base configurato deve essere qwen2.5:7b-instruct-q8_0");

        // Libera VRAM da altri modelli
        OllamaMonolithicClient.defaultInstance(mapper).stopOtherModels("qwen2.5:7b-instruct-q8_0");

        Path heldoutPath = Path.of("data/heldout_dataset_v1.json");
        ComparisonRunner.verifyDatasetSha256(heldoutPath, ComparisonRunner.EXPECTED_HELDOUT_DATASET_SHA256);

        Path advPath = Path.of("data/heldout_adversarial_v1.json");
        ComparisonRunner.verifyDatasetSha256(advPath, ComparisonRunner.EXPECTED_HELDOUT_ADVERSARIAL_SHA256);

        List<Map<String, Object>> heldoutDocs = runner.loadDataset(heldoutPath);
        assertEquals(42, heldoutDocs.size());

        List<Map<String, Object>> advDocs = runner.loadDataset(advPath);
        assertEquals(6, advDocs.size());

        log.info("==========================================================================");
        log.info("AVVIO PASSO 5: CELLA A_7B (APP NEURO-SIMBOLICA SU QWEN 7B Q8_0 HELD-OUT)");
        log.info("==========================================================================");

        // 1. Esegui Cella A_7B sui 42 documenti Held-Out
        log.info("\n--- Avvio Cella A_7B sui 42 documenti Held-Out ---");
        executeCellA7b(runner, "naturale", heldoutDocs);

        // 2. Esegui Cella A_7B sui 6 documenti avversariali
        log.info("\n--- Avvio Cella A_7B sui 6 documenti Avversariali ---");
        Path advDir = Path.of("results/heldout/adversarial");
        ComparisonRunner advRunner = new ComparisonRunner(mapper, advDir);
        executeCellA7b(advRunner, "adv", advDocs);

        // 3. Rigenera il riepilogo avversariale unificato per tutte le 4 celle (A, A_7B, B, C)
        HeldOutLiveRunnerTest.generateAdversarialSummary(advDir, advDocs, List.of("A", "A_7B", "B", "C"));

        // Verifiche
        Path sumPath = Path.of("results/heldout/A_7B/naturale/summary.json");
        assertTrue(Files.exists(sumPath), "summary.json mancante per Cella A_7B");

        Path advSumPath = advDir.resolve("A_7B/adv/summary.json");
        assertTrue(Files.exists(advSumPath), "summary.json avversariale mancante per Cella A_7B");

        log.info("==========================================================================");
        log.info("CELLA A_7B COMPLETATA CON SUCCESSO SULL'HELD-OUT");
        log.info("==========================================================================");
    }

    private void executeCellA7b(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", "A_7B");
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
                    docId, trueType, (String) doc.get("template"), "heldout", status,
                    flatExtracted, groundTruth, respNode, inferenceSteps
            );
            evalDoc.put("client_latency_ms", clientLatency);
            evaluatedDocs.add(evalDoc);

            if (i % 5 == 0 || i == docs.size()) {
                log.info("Cella A_7B [{}] {}/{} (doc: {}, latenza: {} ms, status: {}, match: {}/{})",
                        ordine, i, docs.size(), docId, clientLatency, status,
                        evalDoc.get("correct_fields"), evalDoc.get("total_fields"));
            }
        }

        sRunner.saveRunResults("A_7B", ordine, metadata, evaluatedDocs);
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
