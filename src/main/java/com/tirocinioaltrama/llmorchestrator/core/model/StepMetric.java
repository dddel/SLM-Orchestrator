package com.tirocinioaltrama.llmorchestrator.core.model;

/**
 * Traccia dell'esecuzione di un singolo step per osservabilità e analisi diagnostica.
 *
 * @param stepId           identificativo del passo o componente
 * @param type             tipologia (es. 'ROUTER', 'MODEL_INFERENCE', 'FIELD_VALIDATION', 'ACTION')
 * @param model            modello utilizzato (se applicabile)
 * @param latencyMs        latenza misurata in millisecondi
 * @param promptTokens     token del prompt di input (0 se non applicabile)
 * @param completionTokens token generati in output (0 se non applicabile)
 */
public record StepMetric(
        String stepId,
        String type,
        String model,
        long latencyMs,
        int promptTokens,
        int completionTokens,
        String doneReason
) {
    public StepMetric(String stepId, String type, String model, long latencyMs, int promptTokens, int completionTokens) {
        this(stepId, type, model, latencyMs, promptTokens, completionTokens, null);
    }

    public static StepMetric simple(String stepId, String type, long latencyMs) {
        return new StepMetric(stepId, type, null, latencyMs, 0, 0, null);
    }
}
