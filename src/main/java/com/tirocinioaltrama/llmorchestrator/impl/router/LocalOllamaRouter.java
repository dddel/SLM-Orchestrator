package com.tirocinioaltrama.llmorchestrator.impl.router;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.Router;
import com.tirocinioaltrama.llmorchestrator.core.RoutingDecision;

/**
 * Router locale basato su SLM (es. Ollama con Modelfile.router / qwen2.5-router).
 * Condivide esattamente lo stesso formato di input (<payload>), opzioni e parsing di output di GeminiRouter.
 */
public class LocalOllamaRouter implements Router {

    private static final Logger log = LoggerFactory.getLogger(LocalOllamaRouter.class);
    private static final String CHAT_PATH = "/api/chat";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    public static final String ROUTER_NAME_PREFIX = "Ollama Local (";
    public static final String ROUTER_NAME_FALLBACK = "Fallback Euristico";
    private static final String DEFAULT_ROUTER_SYSTEM_PROMPT_RESOURCE = "prompts/router-system.txt";

    private final RestClient restClient;
    private final String model;
    private final String systemPrompt;
    private final com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions runtimeOptions;

    public LocalOllamaRouter(String baseUrl, String model, Duration timeout) {
        this(baseUrl, model, timeout, null, null);
    }

    public LocalOllamaRouter(String baseUrl, String model, Duration timeout, String systemPrompt) {
        this(baseUrl, model, timeout, systemPrompt, null);
    }

    public LocalOllamaRouter(String baseUrl, String model, Duration timeout, String systemPrompt,
            com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions runtimeOptions) {
        this.model = (model != null && !model.isBlank()) ? model : "qwen2.5-router:latest";
        this.systemPrompt = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt.strip()
                : loadDefaultSystemPrompt();
        this.runtimeOptions = runtimeOptions != null
                ? runtimeOptions
                : com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions.defaults();
        Duration effectiveTimeout = timeout != null ? timeout : Duration.ofSeconds(10);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(effectiveTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(effectiveTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null ? baseUrl : "http://localhost:11434")
                .requestFactory(factory)
                .build();
    }

    public LocalOllamaRouter(RestClient restClient, String model) {
        this(restClient, model, null, null);
    }

    public LocalOllamaRouter(RestClient restClient, String model, String systemPrompt) {
        this(restClient, model, systemPrompt, null);
    }

    public LocalOllamaRouter(RestClient restClient, String model, String systemPrompt,
            com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions runtimeOptions) {
        this.restClient = restClient;
        this.model = (model != null && !model.isBlank()) ? model : "qwen2.5-router:latest";
        this.systemPrompt = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt.strip()
                : loadDefaultSystemPrompt();
        this.runtimeOptions = runtimeOptions != null
                ? runtimeOptions
                : com.tirocinioaltrama.llmorchestrator.impl.model.OllamaRuntimeOptions.defaults();
    }

    private static String loadDefaultSystemPrompt() {
        try (var is = LocalOllamaRouter.class.getClassLoader().getResourceAsStream(DEFAULT_ROUTER_SYSTEM_PROMPT_RESOURCE)) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
            }
        } catch (Exception ignored) {
        }
        return """
                Sei un router deterministico. Analizza il seguente testo ed estrai esclusivamente l'etichetta della categoria a cui appartiene tra queste quattro opzioni:
                - EMAIL (comunicazioni, corrispondenza, email con mittente/destinatario/oggetto)
                - FATTURA (fatture commerciali, parcelle, importi con IVA/IBAN)
                - ANAGRAFICA (documenti di identità, dati anagrafici, patenti, passaporti)
                - ALTRO (qualsiasi testo che non rientri chiaramente in EMAIL, FATTURA o ANAGRAFICA)

                Rispondi tassativamente con una sola parola (EMAIL, FATTURA, ANAGRAFICA oppure ALTRO) senza punteggiatura, spazi extra o markdown.
                """.strip();
    }

    public String getModel() {
        return model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public DocumentType route(String payload) {
        return routeWithDecision(payload).documentType();
    }

    @Override
    public RoutingDecision routeWithDecision(String payload) {
        if (payload == null || payload.isBlank()) {
            return new RoutingDecision(DocumentType.ALTRO, ROUTER_NAME_FALLBACK);
        }

        log.info("Invocazione router locale Ollama ('{}') per classificazione payload...", model);

        String userContent = """
                <payload>
                %s
                </payload>
                """.formatted(payload);

        java.util.List<Message> messages = new java.util.ArrayList<>(2);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message(ROLE_SYSTEM, systemPrompt));
        }
        messages.add(new Message(ROLE_USER, userContent));

        java.util.Map<String, Object> options = new java.util.HashMap<>();
        options.put("repeat_penalty", runtimeOptions.repeatPenalty());
        if (runtimeOptions.numCtx() != null) {
            options.put("num_ctx", runtimeOptions.numCtx());
        }
        if (runtimeOptions.numPredict() != null) {
            options.put("num_predict", runtimeOptions.numPredict());
        }
        if (runtimeOptions.numThread() != null) {
            options.put("num_thread", runtimeOptions.numThread());
        }
        if (runtimeOptions.temperature() != null) {
            options.put("temperature", runtimeOptions.temperature());
        }
        if (runtimeOptions.seed() != null) {
            options.put("seed", runtimeOptions.seed());
        }

        OllamaChatRequest requestBody = new OllamaChatRequest(
                model,
                messages,
                false,
                options,
                runtimeOptions.keepAlive()
        );

        try {
            OllamaChatResponse response = restClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response != null && response.message() != null && response.message().content() != null) {
                long loadMs = response.load_duration() != null ? response.load_duration() / 1_000_000 : 0;
                String answer = response.message().content();
                DocumentType detected = parseAnswer(answer);
                String routerName = ROUTER_NAME_PREFIX + model + ")";
                log.info("Local Ollama Router ha classificato il payload come: '{}' tramite {} (load: {} ms)", detected, routerName, loadMs);
                return new RoutingDecision(detected, routerName);
            }
        } catch (Exception e) {
            log.error("Errore durante l'invocazione di Ollama Router ({}): {}. Attivazione fallback euristico.",
                    model, e.getMessage());
        }

        return new RoutingDecision(fallbackHeuristic(payload), ROUTER_NAME_FALLBACK);
    }

    private DocumentType parseAnswer(String answer) {
        if (answer == null) {
            return DocumentType.ALTRO;
        }
        String clean = answer.strip().toUpperCase(Locale.ROOT);
        if (clean.contains("FATTURA")) {
            return DocumentType.FATTURA;
        }
        if (clean.contains("ANAGRAFICA") || clean.contains("IDENTITA") || clean.contains("PASSAPORTO") || clean.contains("CIE")) {
            return DocumentType.ANAGRAFICA;
        }
        if (clean.contains("EMAIL") || clean.contains("POSTA")) {
            return DocumentType.EMAIL;
        }
        return DocumentType.ALTRO;
    }

    private DocumentType fallbackHeuristic(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("fattura") || lower.contains("p.iva") || lower.contains("iban") || lower.contains("imponibile")) {
            return DocumentType.FATTURA;
        }
        if (lower.contains("carta d'identità") || lower.contains("passaporto") || lower.contains("codice fiscale") || lower.contains("patente") || lower.contains("cie ")) {
            return DocumentType.ANAGRAFICA;
        }
        if (lower.contains("da:") || lower.contains("a:") || lower.contains("oggetto:") || lower.contains("gentil") || lower.contains("cordiali saluti") || lower.contains("@")) {
            return DocumentType.EMAIL;
        }
        return DocumentType.ALTRO;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OllamaChatRequest(
            String model,
            List<Message> messages,
            boolean stream,
            java.util.Map<String, Object> options,
            String keep_alive) {}

    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChatResponse(Message message, Long load_duration) {}
}
