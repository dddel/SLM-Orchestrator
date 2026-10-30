package com.tirocinioaltrama.slmorchestrator.impl.model;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelMetrics;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;

/**
 * Implementazione modulare di {@link ModelClient} per un server Ollama locale o remoto.
 * Consente di specificare o sovrascrivere il modello dinamicamente tramite stringa nella richiesta,
 * oppure di ripiegare su un modello predefinito configurato.
 * <p>
 * Architettura "Single Base Model + Dynamic System Prompt": ogni richiesta a {@code /api/chat}
 * invia esplicitamente un messaggio {@code system} (prompt della pipeline/router) seguito dal
 * messaggio {@code user} (eventuale feedback + payload). I parametri runtime (keep_alive, num_ctx,
 * repeat_penalty, num_thread) sostituiscono i PARAMETER dei Modelfile dedicati.
 * Una {@link PromptCacheGuard} impedisce lo stato di "Cache Piena" su richieste consecutive identiche.
 */
public class OllamaModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelClient.class);
    private static final String CHAT_PATH = "/api/chat";
    static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";

    private final RestClient restClient;
    private final String defaultModel;
    private final boolean includeSensitiveValues;
    private final OllamaRuntimeOptions runtimeOptions;
    private final PromptCacheGuard cacheGuard;
    private final java.util.Map<String, Boolean> thinkingCapabilityCache = new java.util.concurrent.ConcurrentHashMap<>();

    public OllamaModelClient(RestClient restClient) {
        this(restClient, null, false);
    }

    public OllamaModelClient(RestClient restClient, String defaultModel) {
        this(restClient, defaultModel, false);
    }

    public OllamaModelClient(RestClient restClient, String defaultModel, boolean includeSensitiveValues) {
        this(restClient, defaultModel, includeSensitiveValues, OllamaRuntimeOptions.defaults());
    }

    public OllamaModelClient(RestClient restClient, String defaultModel, boolean includeSensitiveValues,
            OllamaRuntimeOptions runtimeOptions) {
        this.restClient = restClient;
        this.defaultModel = defaultModel;
        this.includeSensitiveValues = includeSensitiveValues;
        this.runtimeOptions = runtimeOptions != null ? runtimeOptions : OllamaRuntimeOptions.defaults();
        this.cacheGuard = new PromptCacheGuard();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        OllamaChatRequest body = toOllamaRequest(request);

        log.info("Invio richiesta di generazione al modello '{}' [schema: {}]",
                body.model(),
                body.format() != null);
        if (includeSensitiveValues) {
            log.debug("Payload utente ({} caratteri): {}", 
                    request.userPrompt() != null ? request.userPrompt().length() : 0, 
                    request.userPrompt());
        } else {
            log.debug("Payload utente ({} caratteri)", 
                    request.userPrompt() != null ? request.userPrompt().length() : 0);
        }

        long start = System.nanoTime();
        OllamaChatResponse response;
        try {
            response = restClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OllamaChatResponse.class);
        } catch (Exception e) {
            log.error("Errore durante la chiamata HTTP a Ollama ('{}'): {}", CHAT_PATH, e.getMessage(), e);
            throw e;
        }

        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        if (response == null || response.message() == null) {
            log.error("Risposta vuota o non conforme ricevuta da Ollama per il modello '{}'", body.model());
            throw new IllegalStateException("Risposta non valida o vuota ricevuta da Ollama.");
        }

        String content = response.message().content();
        String thinking = response.message().thinking();
        if ((content == null || content.isBlank()) && (thinking != null && !thinking.isBlank())) {
            log.warn("ATTENZIONE: Risposta ricevuta dal modello '{}' con 'content' vuoto ma 'thinking' presente ('{}').",
                    body.model(), thinking.strip());
        }
        if (content == null) {
            log.error("Risposta senza campo content ricevuta da Ollama per il modello '{}'", body.model());
            throw new IllegalStateException("Risposta non valida o vuota ricevuta da Ollama.");
        }
        
        int promptTokens = response.prompt_eval_count() != null ? response.prompt_eval_count() : 0;
        int completionTokens = response.eval_count() != null ? response.eval_count() : 0;
        long loadDurationMs = response.load_duration() != null ? response.load_duration() / 1_000_000 : 0;
        long evalDurationMs = response.eval_duration() != null ? response.eval_duration() / 1_000_000 : 0;
        long totalDurationMs = response.total_duration() != null ? response.total_duration() / 1_000_000 : latencyMs;
        String doneReason = response.done_reason();

        ModelMetrics metrics = new ModelMetrics(promptTokens, completionTokens, evalDurationMs, totalDurationMs, loadDurationMs, doneReason);

        log.info("Risposta completata con successo dal modello '{}' in {} ms (load: {} ms, tokens: in={}, out={})",
                body.model(), latencyMs, loadDurationMs, promptTokens, completionTokens);
        if (includeSensitiveValues) {
            log.debug("Contenuto risposta:\n{}", content);
        } else {
            log.debug("Contenuto risposta generato: {} caratteri in {} ms", content.length(), latencyMs);
        }

        return new ModelResponse(content, latencyMs, metrics);
    }

    private OllamaChatRequest toOllamaRequest(ModelRequest request) {
        String effectiveModel = !isBlank(request.model()) ? request.model().strip() : defaultModel;
        if (isBlank(effectiveModel)) {
            log.error("Tentativo di invocazione senza specificare un modello (nessun default configurato).");
            throw new IllegalArgumentException(
                    "Nome del modello non specificato nella richiesta né predefinito nella configurazione.");
        }

        String systemPrompt = isBlank(request.systemPrompt()) ? null : request.systemPrompt();

        // Guardia anti-"Cache Piena": su richieste consecutive identiche invalida solo la cache del payload
        String userContent = cacheGuard.apply(effectiveModel, systemPrompt, request.userPrompt());

        List<Message> messages = new ArrayList<>(2);
        if (systemPrompt != null) {
            messages.add(new Message(ROLE_SYSTEM, systemPrompt));
        }
        messages.add(new Message(ROLE_USER, userContent));

        String format = null;
        if (!isBlank(request.jsonSchema())) {
            String trimmed = request.jsonSchema().strip();
            if (trimmed.equalsIgnoreCase("json")) {
                format = "\"json\"";
            } else {
                format = trimmed;
            }
        }

        java.util.Map<String, Object> effectiveOptions = new java.util.HashMap<>();
        effectiveOptions.put("repeat_penalty", runtimeOptions.repeatPenalty());
        if (runtimeOptions.numCtx() != null) {
            effectiveOptions.put("num_ctx", runtimeOptions.numCtx());
        }
        if (runtimeOptions.numPredict() != null) {
            effectiveOptions.put("num_predict", runtimeOptions.numPredict());
        }
        if (runtimeOptions.numThread() != null) {
            effectiveOptions.put("num_thread", runtimeOptions.numThread());
        }
        if (runtimeOptions.temperature() != null) {
            effectiveOptions.put("temperature", runtimeOptions.temperature());
        }
        if (runtimeOptions.seed() != null) {
            effectiveOptions.put("seed", runtimeOptions.seed());
        }
        // Le opzioni dinamiche della richiesta (es. temperature nei retry) hanno la precedenza
        if (request.options() != null) {
            effectiveOptions.putAll(request.options());
        }

        Boolean requestedThink = request.think();
        if (requestedThink == null && request.options() != null && request.options().containsKey("think")) {
            Object val = request.options().get("think");
            if (val instanceof Boolean b) {
                requestedThink = b;
            } else if (val != null) {
                requestedThink = Boolean.parseBoolean(val.toString());
            }
        }
        if (requestedThink == null) {
            requestedThink = runtimeOptions.think();
        }

        Boolean effectiveThink = null;
        if (requestedThink != null) {
            // Invia "think" solo se le capability del modello (da /api/show) includono thinking
            if (supportsThinking(effectiveModel)) {
                effectiveThink = requestedThink;
            } else {
                log.debug("Parametro 'think: {}' ignorato per il modello '{}': il modello non include la capability 'thinking'.",
                        requestedThink, effectiveModel);
            }
        }

        return new OllamaChatRequest(
                effectiveModel,
                List.copyOf(messages),
                false,
                format,
                effectiveOptions,
                runtimeOptions.keepAlive(),
                effectiveThink);
    }

    public boolean supportsThinking(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        Boolean cached = thinkingCapabilityCache.get(model);
        if (cached != null) {
            return cached;
        }
        try {
            ShowResponse resp = restClient.post()
                    .uri("/api/show")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("name", model))
                    .retrieve()
                    .body(ShowResponse.class);
            if (resp != null && resp.capabilities() != null) {
                boolean supports = resp.capabilities().contains("thinking");
                thinkingCapabilityCache.put(model, supports);
                return supports;
            }
            return false;
        } catch (Exception e) {
            log.debug("Verifica capability 'thinking' per '{}' non disponibile tramite /api/show ({}). Default temporaneo: false (non memorizzato in cache).", model, e.getMessage());
            return false;
        }
    }

    public void setThinkingCapability(String model, boolean supportsThinking) {
        if (model != null && !model.isBlank()) {
            thinkingCapabilityCache.put(model, supportsThinking);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OllamaChatRequest(
            String model,
            List<Message> messages,
            boolean stream,
            @JsonRawValue String format,
            java.util.Map<String, Object> options,
            String keep_alive,
            Boolean think) {
    }

    record Message(String role, String content, String thinking) {
        Message(String role, String content) {
            this(role, content, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShowResponse(List<String> capabilities) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChatResponse(
            Message message,
            String done_reason,
            Integer prompt_eval_count,
            Integer eval_count,
            Long load_duration,
            Long eval_duration,
            Long total_duration) {
    }
}