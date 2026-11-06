package com.tirocinioaltrama.slmorchestrator.core.validation;

import java.util.List;

/**
 * Risultato immutabile di un controllo di validazione deterministico.
 *
 * @param isValid     indica se la validazione è stata superata con successo
 * @param errors      lista di messaggi di errore testuali human-readable
 * @param warnings    lista di eventuali avvisi non bloccanti
 * @param fieldErrors lista strutturata degli errori riscontrati (FieldError)
 */
public record ValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings,
        List<FieldError> fieldErrors
) {

    public ValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
        fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public ValidationResult(boolean isValid, List<String> errors, List<String> warnings) {
        this(isValid, errors, warnings, List.of());
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, List.of(), List.of(), List.of());
    }

    public static ValidationResult error(String error) {
        return new ValidationResult(false, List.of(error), List.of(), List.of());
    }

    public static ValidationResult errors(List<String> errors) {
        return new ValidationResult(errors.isEmpty(), errors, List.of(), List.of());
    }

    public static ValidationResult withFieldErrors(List<FieldError> fieldErrors, List<String> warnings) {
        List<String> messages = fieldErrors.stream().map(FieldError::message).toList();
        return new ValidationResult(fieldErrors.isEmpty(), messages, warnings, fieldErrors);
    }

    public static ValidationResult withWarnings(List<String> warnings) {
        return new ValidationResult(true, List.of(), warnings, List.of());
    }
}
