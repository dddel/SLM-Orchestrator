package com.tirocinioaltrama.slmorchestrator.eval;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client monolitico per Ollama (Cella B): una sola chiamata per documento a qwen2.5:7b-instruct-q8_0
 * con schema vincolato a busta e parametri perfettamente allineati all'applicazione.
 */
public class OllamaMonolithicClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaMonolithicClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String model;
    private final int numCtx;
    private final int numPredict;
    private final double repeatPenalty;
    private final double temperature;
    private final int seed;
    private final String keepAlive;

    public record OllamaMetrics(
            long clientLatencyMs,
            long totalDurationMs,
            long loadDurationMs,
            long evalDurationMs,
            int promptEvalCount,
            int evalCount,
            boolean contextExceeded
    ) {}

    public record MonolithicResponse(
            String rawJson,
            OllamaMetrics metrics
    ) {}

    public OllamaMonolithicClient(
            ObjectMapper mapper,
            String baseUrl,
            String model,
            int numCtx,
            int numPredict,
            double repeatPenalty,
            double temperature,
            int seed,
            String keepAlive
    ) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.baseUrl = baseUrl != null ? baseUrl : "http://localhost:11434";
        this.model = model != null ? model : "qwen2.5:7b-instruct-q8_0";
        this.numCtx = numCtx > 0 ? numCtx : 2048;
        this.numPredict = numPredict > 0 ? numPredict : 400;
        this.repeatPenalty = repeatPenalty > 0 ? repeatPenalty : 1.0;
        this.temperature = temperature;
        this.seed = seed;
        this.keepAlive = keepAlive != null ? keepAlive : "60m";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static OllamaMonolithicClient defaultInstance(ObjectMapper mapper) {
        return new OllamaMonolithicClient(
                mapper,
                "http://localhost:11434",
                "qwen2.5:7b-instruct-q8_0",
                2048,
                400,
                1.0,
                0.0,
                42,
                "60m"
        );
    }

    public MonolithicResponse generate(String systemPrompt, String documentText, String jsonSchema) throws Exception {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", documentText)
        );

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("num_ctx", numCtx);
        options.put("num_predict", numPredict);
        options.put("repeat_penalty", repeatPenalty);
        options.put("temperature", temperature);
        options.put("seed", seed);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", false);
        body.put("keep_alive", keepAlive);
        body.put("options", options);
        if (jsonSchema != null && !jsonSchema.isBlank()) {
            body.put("format", mapper.readTree(jsonSchema));
        } else {
            body.put("format", "json");
        }

        String jsonBody = mapper.writeValueAsString(body);
        String url = baseUrl + "/api/chat";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        long clientLatencyMs = System.currentTimeMillis() - start;

        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Chiamata Ollama fallita (" + resp.statusCode() + "): " + resp.body());
        }

        OllamaChatResponseBody chatResp = mapper.readValue(resp.body(), OllamaChatResponseBody.class);
        String rawOutput = chatResp.message() != null ? chatResp.message().content() : "{}";

        long totalDur = chatResp.total_duration() != null ? chatResp.total_duration() / 1_000_000 : clientLatencyMs;
        long loadDur = chatResp.load_duration() != null ? chatResp.load_duration() / 1_000_000 : 0;
        long evalDur = chatResp.eval_duration() != null ? chatResp.eval_duration() / 1_000_000 : 0;
        int promptTokens = chatResp.prompt_eval_count() != null ? chatResp.prompt_eval_count() : 0;
        int evalTokens = chatResp.eval_count() != null ? chatResp.eval_count() : 0;

        boolean contextExceeded = promptTokens >= (numCtx - 50);
        if (contextExceeded) {
            log.warn("ATTENZIONE: Prompt eval count ({}) prossimo o superiore al limite di contesto num_ctx ({})!",
                    promptTokens, numCtx);
        }

        OllamaMetrics metrics = new OllamaMetrics(
                clientLatencyMs,
                totalDur,
                loadDur,
                evalDur,
                promptTokens,
                evalTokens,
                contextExceeded
        );

        return new MonolithicResponse(rawOutput, metrics);
    }

    public void stopOtherModels(String exceptModel) {
        try {
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/ps"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                var root = mapper.readTree(resp.body());
                var models = root.get("models");
                if (models != null && models.isArray()) {
                    for (var m : models) {
                        String name = m.path("name").asText();
                        if (!name.equalsIgnoreCase(exceptModel)) {
                            stopModel(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Impossibile verificare modelli attivi in ps: {}", e.getMessage());
        }
    }

    private void stopModel(String modelName) {
        try {
            log.info("Esecuzione 'ollama stop {}' per liberare VRAM...", modelName);
            Map<String, Object> req = Map.of("model", modelName, "keep_alive", 0);
            HttpRequest stopReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(req)))
                    .build();
            httpClient.send(stopReq, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Fallito stop del modello {}: {}", modelName, e.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChatResponseBody(
            OllamaMessage message,
            Long total_duration,
            Long load_duration,
            Long eval_duration,
            Integer prompt_eval_count,
            Integer eval_count,
            String done_reason
    ) {}

    record OllamaMessage(String role, String content) {}
}
