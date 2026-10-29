package com.tirocinioaltrama.llmorchestrator.core.exception;

/**
 * Eccezione sollevata quando l'esecuzione di una pipeline o del worker SLM sottostante fallisce.
 */
public class PipelineExecutionException extends OrchestratorException {

    public PipelineExecutionException(String message) {
        super(message);
    }

    public PipelineExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
