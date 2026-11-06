package com.tirocinioaltrama.slmorchestrator.core.pipeline;

import com.tirocinioaltrama.slmorchestrator.core.model.ExecutionMetrics;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

/**
 * Risultato immutabile restituito al termine dell'esecuzione di una {@link Pipeline}.
 *
 * @param success           esito positivo dell'intera pipeline
 * @param output            output finale prodotto (es. JSON sanificato e validato) oppure null se indeterminato/fallito
 * @param unvalidatedOutput output grezzo dell'ultimo tentativo (se la validazione è fallita e l'esito è INDETERMINATE)
 * @param validationResult  risultato cumulativo delle validazioni eseguite
 * @param executionTimeMs   tempo complessivo di esecuzione in millisecondi
 */
public record PipelineResult(
        boolean success,
        String output,
        String unvalidatedOutput,
        ValidationResult validationResult,
        long executionTimeMs,
        ExecutionMetrics metrics
) {

    public PipelineResult(boolean success, String output, String unvalidatedOutput, ValidationResult validationResult, long executionTimeMs) {
        this(success, output, unvalidatedOutput, validationResult, executionTimeMs, null);
    }

    public PipelineResult(boolean success, String output, ValidationResult validationResult, long executionTimeMs) {
        this(success, output, null, validationResult, executionTimeMs, null);
    }

    public static PipelineResult failure(String errorMessage, long executionTimeMs) {
        return new PipelineResult(false, null, null, ValidationResult.error(errorMessage), executionTimeMs, null);
    }

    public static PipelineResult failure(ValidationResult validationResult, long executionTimeMs) {
        return new PipelineResult(false, null, null, validationResult, executionTimeMs, null);
    }

    public static PipelineResult failure(ValidationResult validationResult, long executionTimeMs, ExecutionMetrics metrics) {
        return new PipelineResult(false, null, null, validationResult, executionTimeMs, metrics);
    }

    public static PipelineResult indeterminate(String unvalidatedOutput, ValidationResult validationResult, long executionTimeMs) {
        return new PipelineResult(false, null, unvalidatedOutput, validationResult, executionTimeMs, null);
    }

    public static PipelineResult indeterminate(String unvalidatedOutput, ValidationResult validationResult, long executionTimeMs, ExecutionMetrics metrics) {
        return new PipelineResult(false, null, unvalidatedOutput, validationResult, executionTimeMs, metrics);
    }

    public static PipelineResult success(String output, ValidationResult validationResult, long executionTimeMs) {
        return new PipelineResult(true, output, null, validationResult, executionTimeMs, null);
    }

    public static PipelineResult success(String output, ValidationResult validationResult, long executionTimeMs, ExecutionMetrics metrics) {
        return new PipelineResult(true, output, null, validationResult, executionTimeMs, metrics);
    }
}
