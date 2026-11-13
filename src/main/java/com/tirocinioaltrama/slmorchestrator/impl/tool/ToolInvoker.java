package com.tirocinioaltrama.slmorchestrator.impl.tool;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolExecutionPolicy;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;

/**
 * Esecutore controllato per le invocazioni di {@link Tool} con isolamento su virtual thread,
 * timeout per tentativo vincolato anche alla scadenza globale della pipeline,
 * e retry con backoff esponenziale sui soli fallimenti transitori (timeout o eccezioni).
 */
@Component
public class ToolInvoker {

    private static final Logger log = LoggerFactory.getLogger(ToolInvoker.class);

    public enum Outcome {
        OK,
        FAILED,
        TIMEOUT,
        ERROR
    }

    public record ToolInvocationResult(
            ToolResult result,
            int attempts,
            long latencyMs,
            Outcome outcome,
            String errorDetail
    ) {
        public boolean isSuccess() {
            return outcome == Outcome.OK && result != null && result.success();
        }
    }

    private final ExecutorService executor;

    public ToolInvoker() {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public ToolInvocationResult invoke(
            Tool tool,
            Map<String, Object> args,
            ToolExecutionPolicy policy,
            long deadlineEpochMs
    ) {
        ToolExecutionPolicy effectivePolicy = policy != null ? policy : ToolExecutionPolicy.defaults();
        int maxAttempts = Math.max(1, 1 + effectivePolicy.maxRetries());
        long startOverall = System.currentTimeMillis();

        Outcome lastOutcome = Outcome.ERROR;
        String lastErrorDetail = "Esecuzione tool non completata";
        ToolResult lastResult = null;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            long now = System.currentTimeMillis();
            long effectiveTimeoutMs = effectivePolicy.timeoutMs();
            if (deadlineEpochMs > 0) {
                long remainingPipelineMs = deadlineEpochMs - now;
                if (remainingPipelineMs <= 0) {
                    long elapsed = now - startOverall;
                    String msg = "Scadenza tempo massimo di pipeline raggiunta prima dell'invocazione del tool '" + tool.getName() + "'";
                    return new ToolInvocationResult(ToolResult.failure(msg), attempt, elapsed, Outcome.TIMEOUT, msg);
                }
                effectiveTimeoutMs = Math.min(effectiveTimeoutMs, remainingPipelineMs);
            }
            effectiveTimeoutMs = Math.max(1L, effectiveTimeoutMs);

            Future<ToolResult> future = executor.submit(() -> tool.execute(args != null ? args : Map.of()));
            try {
                ToolResult res = future.get(effectiveTimeoutMs, TimeUnit.MILLISECONDS);
                long elapsed = System.currentTimeMillis() - startOverall;
                if (res != null && res.success()) {
                    return new ToolInvocationResult(res, attempt, elapsed, Outcome.OK, null);
                }
                // Fallimento logico esplicito restituito dal tool (ToolResult.failure): NON si ritenta
                String errMsg = (res != null && res.message() != null) ? res.message() : "Esecuzione tool fallita";
                return new ToolInvocationResult(
                        res != null ? res : ToolResult.failure(errMsg),
                        attempt,
                        elapsed,
                        Outcome.FAILED,
                        errMsg
                );
            } catch (TimeoutException te) {
                future.cancel(true);
                lastOutcome = Outcome.TIMEOUT;
                lastErrorDetail = "Timeout del tool '" + tool.getName() + "' dopo " + effectiveTimeoutMs + " ms (tentativo " + attempt + "/" + maxAttempts + ")";
                lastResult = ToolResult.failure(lastErrorDetail);
                log.warn(lastErrorDetail);
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                lastOutcome = Outcome.ERROR;
                lastErrorDetail = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                lastResult = ToolResult.failure(lastErrorDetail);
                log.warn("Eccezione nel tool '{}' al tentativo {}/{}: {}", tool.getName(), attempt, maxAttempts, lastErrorDetail);
            } catch (InterruptedException ie) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                long elapsed = System.currentTimeMillis() - startOverall;
                String msg = "Esecuzione del tool '" + tool.getName() + "' interrotta";
                return new ToolInvocationResult(ToolResult.failure(msg), attempt, elapsed, Outcome.ERROR, msg);
            }

            if (attempt < maxAttempts) {
                long backoff = effectivePolicy.backoffMs() * (1L << (attempt - 1));
                if (deadlineEpochMs > 0) {
                    long remaining = deadlineEpochMs - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    backoff = Math.min(backoff, remaining);
                }
                if (backoff > 0) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        long totalElapsed = System.currentTimeMillis() - startOverall;
        return new ToolInvocationResult(lastResult, attempt, totalElapsed, lastOutcome, lastErrorDetail);
    }
}
