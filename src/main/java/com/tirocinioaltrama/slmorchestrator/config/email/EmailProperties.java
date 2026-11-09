package com.tirocinioaltrama.slmorchestrator.config.email;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configurazione dell'azione email. L'invio è disabilitato per default.
 */
@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        boolean enabled,
        boolean dryRun,
        String from,
        List<String> allowedRecipientDomains
) {

    @ConstructorBinding
    public EmailProperties {
        from = from == null || from.isBlank() ? "no-reply@example.test" : from.strip();
        allowedRecipientDomains = allowedRecipientDomains == null
                ? List.of()
                : allowedRecipientDomains.stream()
                .filter(domain -> domain != null && !domain.isBlank())
                .map(String::strip)
                .map(String::toLowerCase)
                .toList();
    }
}
