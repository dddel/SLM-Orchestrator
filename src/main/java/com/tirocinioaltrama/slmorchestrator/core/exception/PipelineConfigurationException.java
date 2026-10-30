package com.tirocinioaltrama.slmorchestrator.core.exception;

/**
 * Eccezione specifica per errori di sintassi, struttura o validazione
 * dei file di configurazione delle pipeline (es. YAML o JSON Schema).
 */
public class PipelineConfigurationException extends OrchestratorException {

    public PipelineConfigurationException(String message) {
        super(message);
    }

    public PipelineConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
