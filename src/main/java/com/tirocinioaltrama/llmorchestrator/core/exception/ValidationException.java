package com.tirocinioaltrama.llmorchestrator.core.exception;

import java.util.List;

import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;

/**
 * Eccezione sollevata quando la validazione di input, etichetta o output fallisce.
 */
public class ValidationException extends OrchestratorException {

    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ValidationException(String message, List<String> errors) {
        super(message + ": " + String.join("; ", errors));
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    public ValidationException(String message, ValidationResult validationResult) {
        this(message, validationResult.errors());
    }

    public List<String> getErrors() {
        return errors;
    }
}
