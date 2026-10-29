package com.tirocinioaltrama.llmorchestrator.core.model;

/**
 * Metriche di utilizzo e prestazionali restituite dal runtime di inferenza per un'invocazione.
 *
 * @param promptTokens     numero di token analizzati nel prompt di input
 * @param completionTokens numero di token generati nell'output
 * @param evalDurationMs   tempo impiegato per la sola generazione dei token (in ms)
 * @param totalDurationMs  tempo totale riportato dal motore di inferenza (in ms)
 */
public record ModelMetrics(
        int promptTokens,
        int completionTokens,
        long evalDurationMs,
        long totalDurationMs,
        long loadDurationMs,
        String doneReason
) {
    public ModelMetrics(int promptTokens, int completionTokens, long evalDurationMs, long totalDurationMs, long loadDurationMs) {
        this(promptTokens, completionTokens, evalDurationMs, totalDurationMs, loadDurationMs, null);
    }

    public ModelMetrics(int promptTokens, int completionTokens, long evalDurationMs, long totalDurationMs) {
        this(promptTokens, completionTokens, evalDurationMs, totalDurationMs, 0L, null);
    }

    public static ModelMetrics empty() {
        return new ModelMetrics(0, 0, 0, 0, 0L, null);
    }
}
