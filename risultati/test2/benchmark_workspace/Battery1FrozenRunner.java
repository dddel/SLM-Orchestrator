package com.tirocinioaltrama.slmorchestrator.eval;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.RouterType;

/**
 * Runner puro in Java che invoca senza alcuna modifica le classi congelate
 * {@link ComparisonRunner}, {@link GroundTruthComparator}, {@link WilsonScore} e {@link OllamaMonolithicClient}.
 * Supporta salvataggio incrementale e ripresa automatica (resume) per singolo documento (docs/<id>.json)
 * senza mai alterare o falsare i dati già valutati.
 */
public class Battery1FrozenRunner {

    private static final String WARMUP_DOC = "Gentile supporto, confermo la ricezione della fattura e procedo al saldo.";

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Uso: Battery1FrozenRunner <mode:orchestrator|monolithic> <cellaName> <splitName> <datasetPath> <outputBaseDir> [portOrModel] [guardsEnabled] [coherenceEnabled] [modelId]");
            System.exit(1);
        }

        String mode = args[0];
        String cellaName = args[1];
        String splitName = args[2];
        Path datasetPath = Path.of(args[3]);
        Path outputBaseDir = Path.of(args[4]);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ComparisonRunner runner = new ComparisonRunner(mapper, outputBaseDir);

        // Verifica SHA-256 per i dataset ufficiali
        String fileName = datasetPath.getFileName().toString();
        if (fileName.equals("dataset_60_v2.json")) {
            ComparisonRunner.verifyDatasetSha256(datasetPath, ComparisonRunner.EXPECTED_DEV_DATASET_SHA256);
        } else if (fileName.equals("heldout_dataset_v1.json")) {
            ComparisonRunner.verifyDatasetSha256(datasetPath, ComparisonRunner.EXPECTED_HELDOUT_DATASET_SHA256);
        } else if (fileName.equals("heldout_adversarial_v1.json")) {
            ComparisonRunner.verifyDatasetSha256(datasetPath, ComparisonRunner.EXPECTED_HELDOUT_ADVERSARIAL_SHA256);
        }

        List<Map<String, Object>> docs = runner.loadDataset(datasetPath);
        System.out.printf("=== AVVIO RUN [%s | %s] su %s (%d documenti) ===%n", cellaName, splitName, datasetPath, docs.size());

        if ("orchestrator".equalsIgnoreCase(mode)) {
            int port = Integer.parseInt(args[5]);
            boolean guardsEnabled = Boolean.parseBoolean(args[6]);
            boolean coherenceEnabled = Boolean.parseBoolean(args[7]);
            String modelId = args.length > 8 ? args[8] : "qwen2.5:3b-instruct-q8_0";
            runOrchestratorCell(mapper, runner, outputBaseDir, cellaName, splitName, docs, port, guardsEnabled, coherenceEnabled, modelId);
        } else if ("monolithic".equalsIgnoreCase(mode)) {
            String modelId = args[5];
            runMonolithicCell(mapper, runner, outputBaseDir, cellaName, splitName, docs, modelId);
        } else {
            throw new IllegalArgumentException("Modalità non supportata: " + mode);
        }
    }

    @SuppressWarnings("unchecked")
    private static void runOrchestratorCell(
            ObjectMapper mapper,
            ComparisonRunner sRunner,
            Path outputBaseDir,
            String cella,
            String splitName,
            List<Map<String, Object>> docs,
            int port,
            boolean guardsEnabled,
            boolean coherenceEnabled,
            String modelId
    ) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Path docsDir = outputBaseDir.resolve(cella).resolve(splitName).resolve("docs");
        Files.createDirectories(docsDir);

        OllamaMonolithicClient.defaultInstance(mapper).stopOtherModels(modelId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", cella);
        metadata.put("split", splitName);
        metadata.put("ordine", "naturale");
        metadata.put("model_id", modelId);
        metadata.put("endpoint", "http://localhost:" + port + "/api/orchestrator/process");
        metadata.put("git_commit", ComparisonRunner.getGitHeadCommit());
        metadata.put("evaluated_at", Instant.now().toString());
        metadata.put("parameters", Map.of(
                "num_ctx", 2048,
                "num_predict", 400,
                "keep_alive", "60m",
                "temperature", 0.0,
                "seed", 42,
                "repeat_penalty", 1.0,
                "router_type", "local-ollama",
                "guards_enabled", guardsEnabled,
                "coherence_enabled", coherenceEnabled
        ));

        sendOrchestratorRequest(httpClient, mapper, port, WARMUP_DOC);

        List<Map<String, Object>> evaluatedDocs = new ArrayList<>();
        int i = 0;
        for (Map<String, Object> doc : docs) {
            i++;
            String docId = (String) doc.get("id");
            Path docFile = docsDir.resolve(docId + ".json");
            if (Files.exists(docFile) && Files.size(docFile) > 50) {
                Map<String, Object> cached = mapper.readValue(docFile.toFile(), Map.class);
                evaluatedDocs.add(cached);
                System.out.printf("  [%s | %s] %d/%d (%s): [RESUME DA DISCO] status=%s, cat=%s, campi=%s/%s%n",
                        cella, splitName, i, docs.size(), docId, cached.get("status"), cached.get("category"),
                        cached.get("correct_fields"), cached.get("total_fields"));
                continue;
            }

            String payload = (String) doc.get("payload");
            String trueType = (String) doc.get("type");
            Map<String, Object> groundTruth = (Map<String, Object>) doc.get("ground_truth");

            long start = System.currentTimeMillis();
            HttpResponse<String> resp = sendOrchestratorRequest(httpClient, mapper, port, payload);
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
                    docId, trueType, (String) doc.get("template"), splitName, status,
                    flatExtracted, groundTruth, respNode, inferenceSteps
            );
            evalDoc.put("client_latency_ms", clientLatency);
            // Salva immediatamente su disco per consentire interruzione/ripresa sicura
            mapper.writeValue(docFile.toFile(), evalDoc);
            evaluatedDocs.add(evalDoc);

            System.out.printf("  [%s | %s] %d/%d (%s): status=%s, cat=%s, campi=%s/%s, steps=%d, lat=%d ms%n",
                    cella, splitName, i, docs.size(), docId, status, evalDoc.get("category"),
                    evalDoc.get("correct_fields"), evalDoc.get("total_fields"), inferenceSteps, clientLatency);
        }

        sRunner.saveRunResults(cella, splitName, metadata, evaluatedDocs);
    }

    @SuppressWarnings("unchecked")
    private static void runMonolithicCell(
            ObjectMapper mapper,
            ComparisonRunner sRunner,
            Path outputBaseDir,
            String cella,
            String splitName,
            List<Map<String, Object>> docs,
            String modelId
    ) throws Exception {
        String promptMonolitico = Files.readString(Path.of("src/test/resources/eval/prompt-monolitico.txt"));
        String schemaBustaText = Files.readString(Path.of("src/test/resources/eval/schema-busta.json"));

        Path docsDir = outputBaseDir.resolve(cella).resolve(splitName).resolve("docs");
        Files.createDirectories(docsDir);

        OllamaMonolithicClient bClient = new OllamaMonolithicClient(
                mapper, "http://localhost:11434", modelId,
                2048, 400, 1.0, 0.0, 42, "60m"
        );
        bClient.stopOtherModels(modelId);
        bClient.generate(promptMonolitico, WARMUP_DOC, schemaBustaText);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("cella", cella);
        metadata.put("split", splitName);
        metadata.put("ordine", "naturale");
        metadata.put("model_id", modelId);
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
            Path docFile = docsDir.resolve(docId + ".json");
            if (Files.exists(docFile) && Files.size(docFile) > 50) {
                Map<String, Object> cached = mapper.readValue(docFile.toFile(), Map.class);
                evaluatedDocs.add(cached);
                System.out.printf("  [%s | %s] %d/%d (%s): [RESUME DA DISCO] cat=%s, campi=%s/%s%n",
                        cella, splitName, i, docs.size(), docId, cached.get("category"),
                        cached.get("correct_fields"), cached.get("total_fields"));
                continue;
            }

            String payload = (String) doc.get("payload");
            String trueType = (String) doc.get("type");
            Map<String, Object> groundTruth = (Map<String, Object>) doc.get("ground_truth");

            OllamaMonolithicClient.MonolithicResponse bResp = null;
            int attempts = 0;
            while (attempts < 3) {
                attempts++;
                try {
                    bResp = bClient.generate(promptMonolitico, payload, schemaBustaText);
                    break;
                } catch (Exception e) {
                    System.err.printf("  [WARN] Errore chiamata Ollama su %s (tentativo %d/3): %s%n", docId, attempts, e.getMessage());
                    if (attempts >= 3) throw e;
                    bClient.stopOtherModels(modelId);
                    Thread.sleep(2000);
                }
            }

            String classifiedType = "ALTRO";
            Map<String, Object> flatExtracted = Collections.emptyMap();
            try {
                JsonNode rawNode = mapper.readTree(bResp.rawJson());
                classifiedType = rawNode.path("tipoDocumento").asText("ALTRO").toUpperCase(Locale.ROOT);
                JsonNode subNode = rawNode.get(classifiedType.toLowerCase(Locale.ROOT));
                if (classifiedType.equalsIgnoreCase(trueType) && subNode != null && subNode.isObject()) {
                    flatExtracted = GroundTruthComparator.flattenJson(subNode);
                }
            } catch (Exception e) {
                System.err.printf("  [WARN] Output JSON non valido sintatticamente per %s: %s%n", docId, e.getMessage());
            }

            Map<String, Object> rawRespMap = new LinkedHashMap<>();
            rawRespMap.put("raw_output", bResp.rawJson());
            rawRespMap.put("result", bResp.rawJson());
            rawRespMap.put("metrics", bResp.metrics());
            rawRespMap.put("classified_type", classifiedType);

            Map<String, Object> evalDoc = sRunner.evaluateDocument(
                    docId, trueType, (String) doc.get("template"), splitName, "OK",
                    flatExtracted, groundTruth, rawRespMap, 1
            );
            evalDoc.put("client_latency_ms", bResp.metrics().clientLatencyMs());
            // Salva immediatamente su disco per consentire interruzione/ripresa sicura
            mapper.writeValue(docFile.toFile(), evalDoc);
            evaluatedDocs.add(evalDoc);

            System.out.printf("  [%s | %s] %d/%d (%s): type=%s (atteso=%s), cat=%s, campi=%s/%s, lat=%d ms%n",
                    cella, splitName, i, docs.size(), docId, classifiedType, trueType, evalDoc.get("category"),
                    evalDoc.get("correct_fields"), evalDoc.get("total_fields"), bResp.metrics().clientLatencyMs());
        }

        sRunner.saveRunResults(cella, splitName, metadata, evaluatedDocs);
    }

    private static HttpResponse<String> sendOrchestratorRequest(
            HttpClient httpClient,
            ObjectMapper mapper,
            int port,
            String text
    ) throws Exception {
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
