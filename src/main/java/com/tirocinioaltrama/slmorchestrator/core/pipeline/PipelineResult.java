package com.tirocinioaltrama.slmorchestrator.core.pipeline;

import com.tirocinioaltrama.slmorchestrator.core.model.ExecutionMetrics;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

/**
 * Risultato immutabile restituito al termine dell'esecuzione di una {@link Pipeline}.
 *
 * @param success           esito positivo dell'intera pipeline
 * @param outcome           esito strutturato (SUCCESS, INDETERMINATE di dominio o FAILURE infrastrutturale)
 * @param output            output finale prodotto (es. JSON sanificato e validato) oppure null se indeterminato/fallito
 * @param unvalidatedOutput output grezzo dell'ultimo tentativo (se la validazione è fallita e l'esito è INDETERMINATE)
 * @param validationResult  risultato cumulativo delle validazioni eseguite
 * @param executionTimeMs   tempo complessivo di esecuzione in millisecondi
 * @param metrics           metriche di esecuzione raccolte dagli step
 */
public record PipelineResult(
        boolean success,
        Outcome outcome,
        String output,
        String unvalidatedOutput,
        ValidationResult validationResult,
        long executionTimeMs,
        ExecutionMetrics metrics
) {

    public enum Outcome {
        SUCCESS,
        INDETERMINATE,
        FAILURE
    }

    public PipelineResult(
            boolean success,
            String output,
            String unvalidatedOutput,
            ValidationResult validationResult,
            long executionTimeMs,
            ExecutionMetrics metrics
    ) {
        this(
                success,
                success ? Outcome.SUCCESS : Outcome.INDETERMINATE,
                output,
                unvalidatedOutput,
                validationResult,
                executionTimeMs,
                metrics
        );
    }

    public PipelineResult(boolean success, String output, String unvalidatedOutput, ValidationResult validationResult, long executionTimeMs) {
        this(success, output, unvalidatedOutput, validationResult, executionTimeMs, null);
    }

    public PipelineResult(boolean success, String output, ValidationResult validationResult, long executionTimeMs) {
        this(success, output, null, validationResult, executionTimeMs, null);
    }

    public static PipelineResult failure(String errorMessage, long executionTimeMs) {
        return new PipelineResult(false, Outcome.FAILURE, null, null, ValidationResult.error(errorMessage), executionTimeMs, null);
    }

    public static PipelineResult failure(ValidationResult validationResult, long executionTimeMs) {
        return new PipelineResult(false, Outcome.FAILURE, null, null, validationResult, executionTimeMs, null);
    }

    public static PipelineResult failure(ValidationResult validationResult, long executionTimeMs, ExecutionMetrics metrics) {
        return new PipelineResult(false, Outcome.FAILURE, null, null, validationResult, executionTimeMs, metrics);
    }

    public static PipelineResult indeterminate(String unvalidatedOutput, ValidationResult validationResult, long executionTimeMs) {
        return new PipelineResult(false, Outcome.INDETERMINATE, null, unvalidatedOutput, validationResult, executionTimeMs, null);
    }

    public static PipelineResult indeterminate(String unvalidatedOutput, ValidationResult validationResult, long executionTimeMs, ExecutionMetrics metrics) {
        return new PipelineResult(false, Outcome.INDETERMINATE, null, unvalidatedOutput, validationResult, executionTimeMs, metrics);
    }

    public static PipelineResult success(String output, ValidationResult validationResult, long executionTimeMs) {
        return new PipelineResult(true, Outcome.SUCCESS, output, null, validationResult, executionTimeMs, null);
    }

    public static PipelineResult success(String output, ValidationResult validationResult, long executionTimeMs, ExecutionMetrics metrics) {
        return new PipelineResult(true, Outcome.SUCCESS, output, null, validationResult, executionTimeMs, metrics);
    }
}
