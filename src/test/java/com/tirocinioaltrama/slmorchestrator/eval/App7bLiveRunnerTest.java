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
 * Runner sperimentale per Cella A_7B:
 * Valutazione della medesima architettura neuro-simbolica dell'applicazione (Router locale,
 * pipeline specializzate S1, guardie G1-G4, coerenza anagrafica, validatori formali, smart retry RF7)
 * ma equipaggiata con il modello più grande 'qwen2.5:7b-instruct-q8_0' al posto di 'qwen2.5:3b-instruct-q8_0'.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ollama.base-model=qwen2.5:7b-instruct-q8_0"
        }
)
@Tag("live")
@Tag("live-model")
public class App7bLiveRunnerTest {

    private static final Logger log = LoggerFactory.getLogger(App7bLiveRunnerTest.class);

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
        runner = new ComparisonRunner(mapper, Path.of("results/confronti"));
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
    @DisplayName("Benchmark Completo App con Modello 7B Q8_0 (Dev Set 60 doc + Set Avversariale)")
    void runFullBenchmarkApp7b() throws Exception {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile su " + ollamaProperties.baseUrl());
        assertEquals("qwen2.5:7b-instruct-q8_0", ollamaProperties.baseModel(),
                "Il modello base configurato deve essere qwen2.5:7b-instruct-q8_0");

        // Libera VRAM da altri modelli
        OllamaMonolithicClient.defaultInstance(mapper).stopOtherModels("qwen2.5:7b-instruct-q8_0");

        Path datasetPath = Path.of("data/dataset_60_v2.json");
        ComparisonRunner.verifyDatasetSha256(datasetPath);
        List<Map<String, Object>> docs = runner.loadDataset(datasetPath);
        assertEquals(60, docs.size());

        log.info("==========================================================================");
        log.info("AVVIO BENCHMARK CELLA A_7B (APP NEURO-SIMBOLICA SU QWEN 7B Q8_0)");
        log.info("==========================================================================");

        // 1. Esegui Cella A_7B sui 60 documenti dev
        executeCellA7b(runner, "naturale", docs);

        // 2. Esegui Cella A_7B sul set avversariale
        Path advPath = Path.of("data/adversarial_piva_set.json");
        if (Files.exists(advPath)) {
            List<Map<String, Object>> advDocs = runner.loadDataset(advPath);
            Path advDir = Path.of("results/confronti/adversarial");
            ComparisonRunner advRunner = new ComparisonRunner(mapper, advDir);
            executeCellA7b(advRunner, "adv", advDocs);
        }

        // Verifica finale
        Path sumPath = Path.of("results/confronti/A_7B/naturale/summary.json");
        assertTrue(Files.exists(sumPath), "summary.json mancante per Cella A_7B");
        log.info("==========================================================================");
        log.info("BENCHMARK CELLA A_7B COMPLETATO CON SUCCESSO");
        log.info("==========================================================================");
    }

    private void executeCellA7b(ComparisonRunner sRunner, String ordine, List<Map<String, Object>> docs) throws Exception {
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
        metadata.put("cella", "A_7B");
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

            if (i % 5 == 0 || i == docs.size()) {
                log.info("Cella A_7B [{}] completati {}/{} documenti (ultimo doc: {}, latenza: {} ms, status: {})",
                        ordine, i, docs.size(), docId, clientLatency, status);
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
