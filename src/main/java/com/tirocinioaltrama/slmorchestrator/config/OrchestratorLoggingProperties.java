package com.tirocinioaltrama.slmorchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietà di configurazione per il logging e la gestione dei dati sensibili.
 */
@ConfigurationProperties(prefix = "orchestrator.logging")
public record OrchestratorLoggingProperties(boolean includeSensitiveValues) {

    public OrchestratorLoggingProperties {
        // Record constructor: includeSensitiveValues ha valore booleano standard (default false)
    }

    public OrchestratorLoggingProperties() {
        this(false);
    }
}
