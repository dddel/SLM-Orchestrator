package com.tirocinioaltrama.llmorchestrator.config.router;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietà di configurazione per Jev (TypeSafe AI) e Cloudflare Workers AI (Clef / Clef-flash).
 * Ispirato alle decision model API "System One".
 */
@ConfigurationProperties(prefix = "jev")
public record JevProperties(
        String provider,          // "typesafe" o "cloudflare"
        String apiKey,            // TYPESAFE_API_KEY oppure CLOUDFLARE_API_TOKEN
        String accountId,         // Obbligatorio se provider == "cloudflare"
        String model,             // "jev-latest" per typesafe, oppure "@cf/cloudflare/clef-flash" per cloudflare
        String baseUrl,           // Facoltativo per override URL
        Duration timeout) {

    public JevProperties {
        if (provider == null || provider.isBlank()) {
            provider = "typesafe";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "INSERISCI_QUI_LA_TUA_CHIAVE";
        }
        if (accountId == null) {
            accountId = "";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
        if (model == null || model.isBlank()) {
            model = "cloudflare".equalsIgnoreCase(provider) ? "@cf/cloudflare/clef-flash" : "jev-latest";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            if ("cloudflare".equalsIgnoreCase(provider)) {
                baseUrl = "https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run/" + model;
            } else {
                baseUrl = "https://api.typesafe.ai/v1/systemone";
            }
        }
    }
}
