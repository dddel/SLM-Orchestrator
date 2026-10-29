package com.tirocinioaltrama.llmorchestrator.impl.router;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.tirocinioaltrama.llmorchestrator.config.router.GeminiProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.Router;

/**
 * Router implementato tramite l'API esterna Google Gemini (Google AI Studio).
 * Classifica il testo grezzo determinando l'etichetta del documento (EMAIL, FATTURA, ANAGRAFICA).
 */
public class GeminiRouter implements Router {

    private static final Logger log = LoggerFactory.getLogger(GeminiRouter.class);
    private static final String HEADER_API_KEY = "x-goog-api-key";

    private final GeminiProperties properties;
    private final RestClient restClient;

    public GeminiRouter(GeminiProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.timeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public GeminiRouter(GeminiProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public static final String ROUTER_NAME_GEMINI_PREFIX = "Gemini Online (";
    public static final String ROUTER_NAME_FALLBACK = "Fallback Euristico";

    @Override
    public DocumentType route(String payload) {
        return routeWithDecision(payload).documentType();
    }

    @Override
    public com.tirocinioaltrama.llmorchestrator.core.RoutingDecision routeWithDecision(String payload) {
        if (payload == null || payload.isBlank()) {
            return new com.tirocinioaltrama.llmorchestrator.core.RoutingDecision(DocumentType.ALTRO, ROUTER_NAME_FALLBACK);
        }

        if (isPlaceholderKey(properties.apiKey())) {
            log.warn("Gemini API Key non configurata (è ancora segnaposto). Utilizzo fallback euristico per il routing.");
            return new com.tirocinioaltrama.llmorchestrator.core.RoutingDecision(fallbackHeuristic(payload), ROUTER_NAME_FALLBACK);
        }

        log.info("Invocazione router esterno Gemini ('{}') per classificazione payload...", properties.model());

        String prompt = """
                Sei un router deterministico. Analizza il seguente testo ed estrai esclusivamente l'etichetta della categoria a cui appartiene tra queste quattro opzioni:
                - EMAIL (comunicazioni, corrispondenza, email con mittente/destinatario/oggetto)
                - FATTURA (fatture commerciali, parcelle, importi con IVA/IBAN)
                - ANAGRAFICA (documenti di identità, dati anagrafici, patenti, passaporti)
                - ALTRO (qualsiasi testo che non rientri chiaramente in EMAIL, FATTURA o ANAGRAFICA)

                Rispondi tassativamente con una sola parola (EMAIL, FATTURA, ANAGRAFICA oppure ALTRO) senza punteggiatura, spazi extra o markdown.

                Testo:
                <payload>
                %s
                </payload>
                """.formatted(payload);

        GeminiRequest requestBody = new GeminiRequest(
                List.of(new Content(List.of(new Part(prompt))))
        );

        try {
            String path = "/models/" + properties.model() + ":generateContent";
            GeminiResponse response = restClient.post()
                    .uri(path)
                    .header(HEADER_API_KEY, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
                Candidate candidate = response.candidates().get(0);
                if (candidate.content() != null && candidate.content().parts() != null && !candidate.content().parts().isEmpty()) {
                    String answer = candidate.content().parts().get(0).text();
                    DocumentType detected = parseAnswer(answer);
                    String routerName = ROUTER_NAME_GEMINI_PREFIX + properties.model() + ")";
                    log.info("Gemini Router ha classificato il payload come: '{}' tramite {}", detected, routerName);
                    return new com.tirocinioaltrama.llmorchestrator.core.RoutingDecision(detected, routerName);
                }
            }
        } catch (Exception e) {
            log.error("Errore durante la chiamata a Google Gemini API ({}): {}. Attivazione fallback euristico.",
                    properties.model(), e.getMessage());
        }

        return new com.tirocinioaltrama.llmorchestrator.core.RoutingDecision(fallbackHeuristic(payload), ROUTER_NAME_FALLBACK);
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

    // Record interni per mappare il protocollo REST di Google Gemini API
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GeminiRequest(List<Content> contents) {}

    record Content(List<Part> parts) {}

    record Part(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}
}
