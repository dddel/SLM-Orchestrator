package com.tirocinioaltrama.llmorchestrator.config.router;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout) {

    public GeminiProperties {
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "INSERISCI_QUI_LA_TUA_CHIAVE_GOOGLE_AI_STUDIO";
        }
        if (model == null || model.isBlank()) {
            model = "gemini-3.5-flash-lite";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
    }
}
