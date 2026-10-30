package com.tirocinioaltrama.slmorchestrator.core.model;

import java.util.List;

/**
 * Report di osservabilità aggregato per la richiesta dell'orchestratore.
 *
 * @param totalDurationMs      durata complessiva end-to-end
 * @param totalPromptTokens    totale token di prompt consumati attraverso tutti gli step e retry
 * @param totalCompletionTokens totale token generati attraverso tutti gli step e retry
 * @param steps                dettaglio per ogni step eseguito
 */
public record ExecutionMetrics(
        long totalDurationMs,
        int totalPromptTokens,
        int totalCompletionTokens,
        List<StepMetric> steps
) {
    public static ExecutionMetrics empty(long durationMs) {
        return new ExecutionMetrics(durationMs, 0, 0, List.of());
    }

    public static ExecutionMetrics of(long totalDurationMs, List<StepMetric> steps) {
        int promptSum = 0;
        int completionSum = 0;
        if (steps != null) {
            for (StepMetric sm : steps) {
                promptSum += sm.promptTokens();
                completionSum += sm.completionTokens();
            }
        }
        return new ExecutionMetrics(
                totalDurationMs,
                promptSum,
                completionSum,
                steps != null ? List.copyOf(steps) : List.of()
        );
    }
}
