package com.tirocinioaltrama.slmorchestrator.core.tool;

/**
 * Policy di controllo deterministico per l'esecuzione di un {@link Tool}
 * (sia in-pipeline tramite {@code ToolPipelineStep} sia post-pipeline tramite {@code ActionExecutor}).
 *
 * @param timeoutMs  tempo massimo per singolo tentativo in millisecondi (> 0)
 * @param maxRetries numero massimo di tentativi aggiuntivi su timeout o eccezione (0..5)
 * @param backoffMs  attesa base per il backoff esponenziale tra tentativi (>= 0)
 */
public record ToolExecutionPolicy(
        long timeoutMs,
        int maxRetries,
        long backoffMs
) {

    public static final long DEFAULT_TIMEOUT_MS = 5000L;
    public static final int DEFAULT_MAX_RETRIES = 0;
    public static final long DEFAULT_BACKOFF_MS = 200L;

    public static ToolExecutionPolicy defaults() {
        return new ToolExecutionPolicy(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MS);
    }

    public static ToolExecutionPolicy resolve(
            Long configuredTimeoutMs,
            Integer configuredMaxRetries,
            Long configuredBackoffMs,
            long defaultTimeoutMs,
            int defaultMaxRetries,
            long defaultBackoffMs
    ) {
        return new ToolExecutionPolicy(
                configuredTimeoutMs != null ? configuredTimeoutMs : defaultTimeoutMs,
                configuredMaxRetries != null ? configuredMaxRetries : defaultMaxRetries,
                configuredBackoffMs != null ? configuredBackoffMs : defaultBackoffMs
        );
    }
}
