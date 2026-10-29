package com.tirocinioaltrama.llmorchestrator.eval;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Client REST per Google Gemini (Cella D) con rate limiter, backoff su HTTP 429,
 * conversione dello schema a busta nel formato OpenAPI 3.0 / Gemini subset,
 * disattivazione thinking (budget 0) e tracciamento usageMetadata / modelVersion.
 */
public class GeminiModelClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelClient.class);
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;
    private final int rpm;
    private final long minIntervalMs;
    private long lastCallTimeMs = 0;

    private final AtomicInteger backoff429Count = new AtomicInteger(0);

    public record GeminiUsage(
            int promptTokenCount,
            int candidatesTokenCount,
            int totalTokenCount,
            int reasoningTokenCount
    ) {}

    public record GeminiResponse(
            String text,
            String modelVersion,
            long latencyMs,
            GeminiUsage usage,
            int backoffRetries,
            String rawJson
    ) {}

    public GeminiModelClient(ObjectMapper mapper, String model, int rpm) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.model = model != null ? model : "gemini-3-flash-preview";
        int configuredRpm = Integer.getInteger("eval.gemini.rpm", rpm > 0 ? rpm : 4);
        this.rpm = configuredRpm;
        this.minIntervalMs = (long) Math.ceil(60_000.0 / this.rpm);
        this.apiKey = resolveApiKey();
        this.restClient = RestClient.builder()
                .baseUrl(DEFAULT_BASE_URL)
                .build();
    }

    public static String resolveApiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isBlank()) {
            return key.strip();
        }
        File envFile = new File(".env");
        if (envFile.exists()) {
            try {
                for (String line : Files.readAllLines(envFile.toPath())) {
                    if (line.startsWith("GEMINI_API_KEY=")) {
                        return line.substring("GEMINI_API_KEY=".length()).trim();
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }

    public int getBackoff429Count() {
        return backoff429Count.get();
    }

    public GeminiResponse generate(String prompt, String documentText, JsonNode envelopeSchema) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY non disponibile nell'ambiente.");
        }

        applyRateLimiter();

        JsonNode geminiSchema = convertToGeminiSchema(envelopeSchema);

        String fullPrompt = prompt + "\n\nDOCUMENTO DA ANALIZZARE:\n" + documentText;

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", fullPrompt)))
        ));

        Map<String, Object> genConfig = new LinkedHashMap<>();
        genConfig.put("responseMimeType", "application/json");
        genConfig.put("temperature", 0.0);
        genConfig.put("thinkingConfig", Map.of("thinkingBudget", 0));
        if (geminiSchema != null) {
            genConfig.put("responseSchema", geminiSchema);
        }
        reqBody.put("generationConfig", genConfig);

        String requestJson = mapper.writeValueAsString(reqBody);

        int maxRetries = 5;
        long backoffDelayMs = 35000L;
        int localRetries = 0;

        while (true) {
            long start = System.currentTimeMillis();
            try {
                String responseBody = restClient.post()
                        .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestJson)
                        .retrieve()
                        .body(String.class);

                long latency = System.currentTimeMillis() - start;
                return parseResponse(responseBody, latency, localRetries);
            } catch (HttpClientErrorException.TooManyRequests e) {
                backoff429Count.incrementAndGet();
                localRetries++;
                if (localRetries > maxRetries) {
                    throw new IllegalStateException("Superato limite tentativi su HTTP 429 da Gemini: " + e.getMessage(), e);
                }
                long sleepTime = backoffDelayMs;
                String errBody = e.getResponseBodyAsString();
                if (errBody != null && errBody.contains("retryDelay")) {
                    Matcher m = Pattern.compile("retryDelay\":\\s*\"(\\d+)s").matcher(errBody);
                    if (m.find()) {
                        sleepTime = (Long.parseLong(m.group(1)) + 2) * 1000L;
                    }
                }
                if (sleepTime > 120_000L) {
                    throw new IllegalStateException("Quota giornaliera (RPD) Google Gemini esaurita: " + errBody);
                }
                log.warn("Ricevuto HTTP 429 da Gemini. Attesa per ripristino quota: {} ms (tentativo {}/{})...",
                        sleepTime, localRetries, maxRetries);
                Thread.sleep(sleepTime);
                backoffDelayMs = Math.min(backoffDelayMs * 2, 65000L);
            }
        }
    }

    private synchronized void applyRateLimiter() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallTimeMs;
        if (elapsed < minIntervalMs) {
            long waitTime = minIntervalMs - elapsed;
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallTimeMs = System.currentTimeMillis();
    }

    private GeminiResponse parseResponse(String responseJson, long latencyMs, int retries) throws Exception {
        JsonNode root = mapper.readTree(responseJson);

        String modelVersion = root.path("modelVersion").asText(model);

        JsonNode usageNode = root.path("usageMetadata");
        int promptTokens = usageNode.path("promptTokenCount").asInt(0);
        int candTokens = usageNode.path("candidatesTokenCount").asInt(0);
        int totalTokens = usageNode.path("totalTokenCount").asInt(0);
        int reasoningTokens = 0;

        JsonNode candDetails = usageNode.path("candidatesTokenDetails");
        if (candDetails.isArray() && !candDetails.isEmpty()) {
            reasoningTokens = candDetails.get(0).path("reasoningTokenCount").asInt(0);
        }

        GeminiUsage usage = new GeminiUsage(promptTokens, candTokens, totalTokens, reasoningTokens);

        String text = "{}";
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                text = parts.get(0).path("text").asText("{}");
            }
        }

        return new GeminiResponse(text, modelVersion, latencyMs, usage, retries, responseJson);
    }

    public JsonNode convertToGeminiSchema(JsonNode source) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return null;
        }
        if (!source.isObject()) {
            return source;
        }

        ObjectNode out = mapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            var f = fields.next();
            String key = f.getKey();
            JsonNode val = f.getValue();

            if (key.equals("$schema") || key.equals("title") || key.equals("additionalProperties")) {
                continue;
            }

            if (key.equals("type")) {
                if (val.isArray()) {
                    List<String> types = new ArrayList<>();
                    boolean hasNull = false;
                    for (JsonNode t : val) {
                        String ts = t.asText();
                        if ("null".equalsIgnoreCase(ts)) {
                            hasNull = true;
                        } else {
                            types.add(ts);
                        }
                    }
                    String primaryType = types.isEmpty() ? "string" : types.get(0);
                    out.put("type", primaryType);
                    if (hasNull) {
                        out.put("nullable", true);
                    }
                } else {
                    out.put("type", val.asText());
                }
            } else if (key.equals("properties") && val.isObject()) {
                ObjectNode propsNode = mapper.createObjectNode();
                Iterator<Map.Entry<String, JsonNode>> pIter = val.fields();
                while (pIter.hasNext()) {
                    var pf = pIter.next();
                    propsNode.set(pf.getKey(), convertToGeminiSchema(pf.getValue()));
                }
                out.set("properties", propsNode);
            } else if (key.equals("items")) {
                out.set("items", convertToGeminiSchema(val));
            } else if (key.equals("enum") && val.isArray()) {
                ArrayNode enumNode = mapper.createArrayNode();
                for (JsonNode ev : val) {
                    if (!ev.isNull()) {
                        enumNode.add(ev.asText());
                    }
                }
                out.set("enum", enumNode);
            } else {
                out.set(key, val);
            }
        }
        return out;
    }
}
