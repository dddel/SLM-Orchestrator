package com.tirocinioaltrama.slmorchestrator.core.exception;

/**
 * Eccezione sollevata in caso di fallimento o classificazione anomala durante la fase di routing.
 */
public class RoutingException extends OrchestratorException {

    public RoutingException(String message) {
        super(message);
    }

    public RoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
