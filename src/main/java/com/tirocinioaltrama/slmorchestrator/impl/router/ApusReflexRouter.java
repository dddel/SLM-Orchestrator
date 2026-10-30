package com.tirocinioaltrama.slmorchestrator.impl.router;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.Router;
import com.tirocinioaltrama.slmorchestrator.core.RoutingDecision;

/**
 * Router deterministico basato su modello decisionale locale APUS Reflex (APUS-Reflex-4B).
 * Utilizza la categorizzazione single-token su candidati discreti A..D a bassissima latenza (num_predict: 1, temp: 0.0)
 * per instradare il payload verso la pipeline specializzata (FATTURA, EMAIL, ANAGRAFICA, ALTRO).
 * Include fallback euristico trasparente in caso di anomalie di connessione o risposte non conformi.
 */
public class ApusReflexRouter implements Router {

    private static final Logger log = LoggerFactory.getLogger(ApusReflexRouter.class);

    public static final String ROUTER_NAME = "reflex";
    public static final String DEFAULT_REFLEX_MODEL = "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M";
    public static final String ROUTER_NAME_PREFIX = "APUS Reflex (";
    public static final String ROUTER_NAME_FALLBACK = "Fallback Euristico";
    public static final String CHAT_PATH = "/api/chat";
    private static final String ROLE_USER = "user";

    public record RouterCategoryCandidate(DocumentType type, String label, String description) {}

    public static final List<RouterCategoryCandidate> DEFAULT_CANDIDATES = List.of(
            new RouterCategoryCandidate(DocumentType.FATTURA, "FATTURA", "commercial invoices, tax receipts, VAT/IVA numbers, IBAN, amounts"),
            new RouterCategoryCandidate(DocumentType.EMAIL, "EMAIL", "correspondence, letters, communications with sender/recipient/subject, formal emails"),
            new RouterCategoryCandidate(DocumentType.ANAGRAFICA, "ANAGRAFICA", "identity cards, driver licenses, passports, Italian codice fiscale, personal documents"),
            new RouterCategoryCandidate(DocumentType.ALTRO, "ALTRO", "any document that does not clearly belong to FATTURA, EMAIL, or ANAGRAFICA")
    );

    public static final String DEFAULT_PROMPT_TEMPLATE = """
            Given the following document text, classify it into exactly one of the four categories below:
            A: FATTURA (commercial invoices, tax receipts, VAT/IVA numbers, IBAN, amounts)
            B: EMAIL (correspondence, letters, communications with sender/recipient/subject, formal emails)
            C: ANAGRAFICA (identity cards, driver licenses, passports, Italian codice fiscale, personal documents)
            D: ALTRO (any document that does not clearly belong to FATTURA, EMAIL, or ANAGRAFICA)

            Document:
            <payload>
            %s
            </payload>

            Respond ONLY with the single uppercase letter of your choice (A, B, C, or D).
            Choice:
            """.strip();

    private final RestClient restClient;
    private final String model;
    private final String promptTemplate;
    private final boolean randomizeOptions;
    private final java.util.Random random;
    private final java.util.concurrent.atomic.AtomicLong invalidTokenCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong fallbackInvocationCount = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.Map<String, Boolean> thinkingCapabilityCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ApusReflexRouter(String baseUrl, String model, Duration timeout) {
        this(baseUrl, model, timeout, null, false, null);
    }

    public ApusReflexRouter(String baseUrl, String model, Duration timeout, String promptTemplate) {
        this(baseUrl, model, timeout, promptTemplate, false, null);
    }

    public ApusReflexRouter(String baseUrl, String model, Duration timeout, String promptTemplate, boolean randomizeOptions, java.util.Random random) {
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_REFLEX_MODEL;
        this.promptTemplate = (promptTemplate != null && !promptTemplate.isBlank()) ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.randomizeOptions = randomizeOptions;
        this.random = random != null ? random : new java.util.Random();
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

    public ApusReflexRouter(RestClient restClient, String model) {
        this(restClient, model, null, false, null);
    }

    public ApusReflexRouter(RestClient restClient, String model, String promptTemplate) {
        this(restClient, model, promptTemplate, false, null);
    }

    public ApusReflexRouter(RestClient restClient, String model, String promptTemplate, boolean randomizeOptions, java.util.Random random) {
        this.restClient = restClient;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_REFLEX_MODEL;
        this.promptTemplate = (promptTemplate != null && !promptTemplate.isBlank()) ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.randomizeOptions = randomizeOptions;
        this.random = random != null ? random : new java.util.Random();
    }

    @Override
    public String getName() {
        return ROUTER_NAME;
    }

    public String getModel() {
        return model;
    }

    public long getInvalidTokenCount() {
        return invalidTokenCount.get();
    }

    public long getFallbackInvocationCount() {
        return fallbackInvocationCount.get();
    }

    public boolean isThinkingModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("reflex") || lower.contains("think") || lower.contains("deepseek-r1")) {
            return true;
        }
        if (lower.contains("qwen2.5") || lower.contains("llama3") || lower.contains("mistral")) {
            return false;
        }
        return thinkingCapabilityCache.computeIfAbsent(modelName, m -> {
            try {
                ShowResponse resp = restClient.post()
                        .uri("/api/show")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("name", m))
                        .retrieve()
                        .body(ShowResponse.class);
                return resp != null && resp.capabilities() != null && resp.capabilities().contains("thinking");
            } catch (Exception e) {
                log.debug("Verifica capability 'thinking' per '{}' non disponibile tramite /api/show ({}). Fallback per nome modello.", m, e.getMessage());
                return false;
            }
        });
    }

    @Override
    public DocumentType route(String payload) {
        return routeWithDecision(payload).documentType();
    }

    public boolean isRandomizeOptions() {
        return randomizeOptions;
    }

    @Override
    public RoutingDecision routeWithDecision(String payload) {
        if (!randomizeOptions) {
            return routeWithCandidates(payload, DEFAULT_CANDIDATES);
        }
        List<RouterCategoryCandidate> candidates = new java.util.ArrayList<>(DEFAULT_CANDIDATES);
        java.util.Collections.shuffle(candidates, random != null ? random : new java.util.Random());
        return routeWithCandidates(payload, candidates);
    }

    public RoutingDecision routeWithCandidates(String payload, List<RouterCategoryCandidate> candidates) {
        if (payload == null || payload.isBlank()) {
            return new RoutingDecision(DocumentType.ALTRO, ROUTER_NAME_FALLBACK);
        }

        log.info("Invocazione router APUS Reflex ('{}') per classificazione discreta ({} opzioni)...", model, candidates.size());

        java.util.Map<Character, DocumentType> letterMap = new java.util.HashMap<>();
        StringBuilder promptSb = new StringBuilder();
        promptSb.append("Given the following document text, classify it into exactly one of the four categories below:\n");
        for (int i = 0; i < candidates.size(); i++) {
            char letter = (char) ('A' + i);
            RouterCategoryCandidate c = candidates.get(i);
            letterMap.put(letter, c.type());
            promptSb.append(letter).append(": ").append(c.label()).append(" (").append(c.description()).append(")\n");
        }
        promptSb.append("\nDocument:\n<payload>\n").append(payload).append("\n</payload>\n\n");
        promptSb.append("Respond ONLY with the single uppercase letter of your choice (A, B, C, or D).\nChoice:");
        String userContent = promptSb.toString();

        Map<String, Object> options = Map.of(
                "temperature", 0.0,
                "num_predict", 1,
                "repeat_penalty", 1.0
        );

        Boolean thinkParam = isThinkingModel(model) ? Boolean.FALSE : null;

        OllamaChatRequest requestBody = new OllamaChatRequest(
                model,
                List.of(new Message(ROLE_USER, userContent)),
                false,
                options,
                "60m",
                thinkParam
        );

        try {
            OllamaChatResponse response = restClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response != null && response.message() != null) {
                String rawContent = response.message().content();
                String rawThinking = response.message().thinking();
                String token = (rawContent != null && !rawContent.isBlank())
                        ? rawContent.strip()
                        : (rawThinking != null ? rawThinking.strip() : "");
                DocumentType detected = parseChoiceTokenFromMap(token, letterMap);
                if (detected != null) {
                    long loadMs = response.load_duration() != null ? response.load_duration() / 1_000_000 : 0;
                    String routerName = ROUTER_NAME_PREFIX + model + ")";
                    log.info("APUS Reflex Router ha deciso: '{}' (token: '{}', load: {} ms)", detected, token, loadMs);
                    return new RoutingDecision(detected, routerName);
                }
                invalidTokenCount.incrementAndGet();
                fallbackInvocationCount.incrementAndGet();
                DocumentType fallbackType = LocalOllamaRouter.fallbackHeuristic(payload);
                String fallbackRouterName = ROUTER_NAME_PREFIX + model + " -> Fallback Euristico per Token Non Valido: '" + token + "')";
                log.warn("APUS Reflex Router ha restituito un token non riconosciuto ('{}'). Attivazione fallback: '{}'", token, fallbackRouterName);
                return new RoutingDecision(fallbackType, fallbackRouterName);
            }
        } catch (Exception e) {
            fallbackInvocationCount.incrementAndGet();
            DocumentType fallbackType = LocalOllamaRouter.fallbackHeuristic(payload);
            String fallbackRouterName = ROUTER_NAME_PREFIX + model + " -> Fallback Euristico per Errore: " + e.getMessage() + ")";
            log.error("Errore durante l'invocazione di APUS Reflex Router ({}): {}. Attivazione fallback: '{}'",
                    model, e.getMessage(), fallbackRouterName);
            return new RoutingDecision(fallbackType, fallbackRouterName);
        }

        fallbackInvocationCount.incrementAndGet();
        DocumentType fallbackType = LocalOllamaRouter.fallbackHeuristic(payload);
        log.info("Routing completato tramite fallback euristico: '{}'", fallbackType);
        return new RoutingDecision(fallbackType, ROUTER_NAME_FALLBACK);
    }

    public static DocumentType parseChoiceToken(String answer) {
        return parseChoiceTokenFromMap(answer, DEFAULT_LETTER_MAP);
    }

    private static final java.util.Map<Character, DocumentType> DEFAULT_LETTER_MAP = java.util.Map.of(
            'A', DocumentType.FATTURA,
            'B', DocumentType.EMAIL,
            'C', DocumentType.ANAGRAFICA,
            'D', DocumentType.ALTRO
    );

    public static DocumentType parseChoiceTokenFromMap(String answer, java.util.Map<Character, DocumentType> letterMap) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String clean = answer.strip().toUpperCase(Locale.ROOT);
        if (clean.startsWith("CHOICE:") || clean.startsWith("OPTION:") || clean.startsWith("SCELTA:")) {
            clean = clean.substring(clean.indexOf(':') + 1).strip();
        } else if (clean.startsWith("CHOICE ") || clean.startsWith("OPTION ") || clean.startsWith("SCELTA ")) {
            clean = clean.substring(clean.indexOf(' ') + 1).strip();
        }

        if (clean.isEmpty()) {
            return null;
        }

        char firstChar = clean.charAt(0);
        boolean isOptionLetter = clean.length() == 1 || !Character.isLetter(clean.charAt(1));

        if (isOptionLetter && letterMap.containsKey(firstChar)) {
            return letterMap.get(firstChar);
        }

        return parseFallbackWord(clean);
    }

    private static DocumentType parseFallbackWord(String text) {
        if (text.contains("FATTURA")) return DocumentType.FATTURA;
        if (text.contains("EMAIL") || text.contains("POSTA")) return DocumentType.EMAIL;
        if (text.contains("ANAGRAFICA") || text.contains("IDENTITA") || text.contains("PASSAPORTO") || text.contains("CIE")) return DocumentType.ANAGRAFICA;
        if (text.contains("ALTRO")) return DocumentType.ALTRO;
        return null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OllamaChatRequest(
            String model,
            List<Message> messages,
            boolean stream,
            Map<String, Object> options,
            String keep_alive,
            Boolean think) {}

    record Message(String role, String content, String thinking) {
        Message(String role, String content) {
            this(role, content, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShowResponse(List<String> capabilities) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChatResponse(Message message, Long load_duration) {}
}
