package com.tirocinioaltrama.slmorchestrator.core.exception;

/**
 * Eccezione base non controllata (unchecked) per tutti gli errori
 * specifici del dominio dell'orchestratore.
 */
public abstract class OrchestratorException extends RuntimeException {

    protected OrchestratorException(String message) {
        super(message);
    }

    protected OrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
