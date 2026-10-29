package com.tirocinioaltrama.llmorchestrator.impl.router;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tirocinioaltrama.llmorchestrator.config.router.JevProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.Router;
import com.tirocinioaltrama.llmorchestrator.core.RoutingDecision;

/**
 * Router basato sui modelli decisionali "System One":
 * - Jev (TypeSafe AI) via /v1/systemone
 * - Clef / Clef-flash (Cloudflare Workers AI) compatibile con l'API decisionale Jev.
 *
 * Utilizza una domanda a risposta chiusa (primitive "choice") su [EMAIL, FATTURA, ANAGRAFICA, ALTRO],
 * eliminando il parsing del testo e riducendo la latenza a frazioni di secondo.
 */
public class JevRouter implements Router {

    private static final Logger log = LoggerFactory.getLogger(JevRouter.class);

    public static final String QUESTION_ID = "doc_type";
    public static final String ROUTER_NAME_PREFIX = "Jev Decision Router (";
    public static final String ROUTER_NAME_FALLBACK = "Fallback Euristico";

    private final JevProperties properties;
    private final RestClient restClient;

    public JevRouter(JevProperties properties) {
        this.properties = properties;
        Duration effectiveTimeout = properties.timeout() != null ? properties.timeout() : Duration.ofSeconds(10);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(effectiveTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(effectiveTimeout);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public JevRouter(JevProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
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

        if (isPlaceholderKey(properties.apiKey())) {
            log.warn("Jev/Cloudflare API Key non configurata o segnaposto. Utilizzo fallback euristico per il routing.");
            return new RoutingDecision(fallbackHeuristic(payload), ROUTER_NAME_FALLBACK);
        }

        String strategyName = ROUTER_NAME_PREFIX + properties.provider() + "/" + properties.model() + ")";
        log.info("Invocazione router decisionale {} per classificazione payload...", strategyName);

        Map<String, String> criteria = new HashMap<>();
        criteria.put("EMAIL", "Comunicazioni, corrispondenza, email con mittente/destinatario/oggetto");
        criteria.put("FATTURA", "Fatture commerciali, parcelle, note di debito/credito, importi con IVA o coordinate bancarie IBAN");
        criteria.put("ANAGRAFICA", "Documenti di riconoscimento, carte d'identita, patenti, passaporti, certificati anagrafici e codici fiscali");
        criteria.put("ALTRO", "Qualsiasi testo che non sia una fattura commerciale, un'email o un documento anagrafico");

        JevQuestion question = new JevQuestion(
                "choice",
                "Determina la categoria del documento analizzando il contenuto dello stato.",
                criteria
        );

        // Cloudflare richiede che /model nel body sia "clef" o "clef-flash" oppure nullo/assente (essendo già nell'URL)
        String bodyModel = properties.model();
        if ("cloudflare".equalsIgnoreCase(properties.provider())) {
            bodyModel = null;
        }

        JevRequest requestBody = new JevRequest(
                bodyModel,
                payload,
                Collections.singletonMap(QUESTION_ID, question)
        );

        try {
            JevResponse response = restClient.post()
                    .uri(properties.baseUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(JevResponse.class);

            if (response != null) {
                String selected = extractSelection(response);
                if (selected != null) {
                    DocumentType detected = parseAnswer(selected);
                    log.info("Jev Router ha classificato il payload come: '{}' tramite {}", detected, strategyName);
                    return new RoutingDecision(detected, strategyName);
                }
            }
        } catch (Exception e) {
            log.error("Errore durante la chiamata a Jev/Cloudflare API ({}): {}. Attivazione fallback euristico.",
                    properties.model(), e.getMessage());
        }

        return new RoutingDecision(fallbackHeuristic(payload), ROUTER_NAME_FALLBACK);
    }

    private String extractSelection(JevResponse response) {
        // Se la risposta è nel formato standard TypeSafe AI (al primo livello o dentro result)
        Map<String, JevAnswer> answers = response.answers();
        if ((answers == null || answers.isEmpty()) && response.result() != null) {
            answers = response.result().answers();
        }

        if (answers != null && answers.containsKey(QUESTION_ID)) {
            JevAnswer answer = answers.get(QUESTION_ID);
            if (answer.choice() != null && !answer.choice().isBlank()) {
                return answer.choice();
            }
            if (answer.selected() != null && !answer.selected().isBlank()) {
                return answer.selected();
            }
            if (answer.probabilities() != null && !answer.probabilities().isEmpty()) {
                // Prendi l'opzione con probabilità più alta
                return answer.probabilities().entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
            }
        }
        return null;
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

    private boolean isPlaceholderKey(String key) {
        return key == null || key.isBlank() || key.contains("INSERISCI_QUI") || key.contains("PLACEHOLDER");
    }

    // Mapping DTO per TypeSafe System One / Cloudflare Workers AI
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JevRequest(
            String model,
            Object state,
            Map<String, JevQuestion> questions
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record JevQuestion(
            String type,
            String instructions,
            Map<String, String> criteria
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JevResponse(
            Map<String, JevAnswer> answers,
            JevResult result
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JevResult(
            Map<String, JevAnswer> answers
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JevAnswer(
            String type,
            String choice,
            String selected,
            Double confidence,
            Map<String, Double> probabilities
    ) {}
}
