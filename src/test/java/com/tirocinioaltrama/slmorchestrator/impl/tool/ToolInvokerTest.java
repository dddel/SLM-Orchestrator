package com.tirocinioaltrama.slmorchestrator.impl.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolExecutionPolicy;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ToolInvoker.Outcome;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ToolInvoker.ToolInvocationResult;

class ToolInvokerTest {

    private ToolInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new ToolInvoker();
    }

    @AfterEach
    void tearDown() {
        invoker.shutdown();
    }

    @Test
    @DisplayName("TT.1 - Tool bloccato in sleep supera timeout_ms -> interrotto entro ~150 ms e outcome=TIMEOUT")
    void testToolTimeoutInterruptsWithinConfiguredBudget() {
        Tool slowTool = new Tool() {
            @Override
            public String getName() {
                return "slow_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                try {
                    Thread.sleep(5000);
                    return ToolResult.ok("Completato", "data");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ToolResult.failure("Interrotto");
                }
            }
        };

        ToolExecutionPolicy policy = new ToolExecutionPolicy(100L, 0, 10L);
        long start = System.currentTimeMillis();
        ToolInvocationResult res = invoker.invoke(slowTool, Map.of(), policy, 0L);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(res.isSuccess());
        assertEquals(Outcome.TIMEOUT, res.outcome());
        assertEquals(1, res.attempts());
        assertTrue(elapsed < 1000L, "Deve interrompere il tool bloccato rapidamente (elapsed=" + elapsed + "ms)");
    }

    @Test
    @DisplayName("TT.2 - Tool che solleva RuntimeException al 1° tentativo e ha successo al 2° -> outcome=OK, attempts=2")
    void testTransientExceptionRecoversOnRetry() {
        AtomicInteger calls = new AtomicInteger(0);
        Tool flakyTool = new Tool() {
            @Override
            public String getName() {
                return "flaky_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                if (calls.incrementAndGet() == 1) {
                    throw new RuntimeException("Glitch di rete temporaneo");
                }
                return ToolResult.ok("Riuscito al retry", Map.of("ok", true));
            }
        };

        ToolExecutionPolicy policy = new ToolExecutionPolicy(1000L, 2, 20L);
        ToolInvocationResult res = invoker.invoke(flakyTool, Map.of(), policy, 0L);

        assertTrue(res.isSuccess());
        assertEquals(Outcome.OK, res.outcome());
        assertEquals(2, res.attempts());
        assertEquals(2, calls.get());
    }

    @Test
    @DisplayName("TT.3 - Tool che restituisce ToolResult.failure() logico NON viene ritentato -> attempts=1")
    void testExplicitLogicalFailureIsNotRetried() {
        AtomicInteger calls = new AtomicInteger(0);
        Tool logicalFailTool = new Tool() {
            @Override
            public String getName() {
                return "logical_fail_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                calls.incrementAndGet();
                return ToolResult.failure("Parametro obbligatorio mancante");
            }
        };

        ToolExecutionPolicy policy = new ToolExecutionPolicy(1000L, 3, 10L);
        ToolInvocationResult res = invoker.invoke(logicalFailTool, Map.of(), policy, 0L);

        assertFalse(res.isSuccess());
        assertEquals(Outcome.FAILED, res.outcome());
        assertEquals(1, res.attempts(), "I fallimenti logici espliciti non devono innescare retry");
        assertEquals(1, calls.get());
    }

    @Test
    @DisplayName("TT.4 - Rispetto della deadline globale della pipeline quando minore di timeout_ms del tool")
    void testGlobalPipelineDeadlineCapsToolTimeout() {
        Tool slowTool = new Tool() {
            @Override
            public String getName() {
                return "deadline_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                try {
                    Thread.sleep(5000);
                    return ToolResult.ok("OK", "data");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ToolResult.failure("Interrotto");
                }
            }
        };

        // Il tool ha timeout=5000ms, ma la pipeline scade tra 80ms
        ToolExecutionPolicy policy = new ToolExecutionPolicy(5000L, 2, 50L);
        long deadlineEpochMs = System.currentTimeMillis() + 80L;
        long start = System.currentTimeMillis();
        ToolInvocationResult res = invoker.invoke(slowTool, Map.of(), policy, deadlineEpochMs);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(res.isSuccess());
        assertEquals(Outcome.TIMEOUT, res.outcome());
        assertTrue(elapsed < 1000L, "Il timeout effettivo deve essere limitato dalla deadline globale della pipeline");
    }

    @Test
    @DisplayName("TT.5 - Cancellazione tramite future.cancel(true) propaga InterruptedException al virtual thread del tool")
    void testThreadInterruptionPropagatedOnTimeout() throws InterruptedException {
        CountDownLatch interruptedLatch = new CountDownLatch(1);
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);

        Tool interruptibleTool = new Tool() {
            @Override
            public String getName() {
                return "interruptible_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                try {
                    Thread.sleep(10000);
                    return ToolResult.ok("OK", null);
                } catch (InterruptedException e) {
                    wasInterrupted.set(true);
                    interruptedLatch.countDown();
                    Thread.currentThread().interrupt();
                    return ToolResult.failure("Interrupted");
                }
            }
        };

        ToolExecutionPolicy policy = new ToolExecutionPolicy(60L, 0, 0L);
        ToolInvocationResult res = invoker.invoke(interruptibleTool, Map.of(), policy, 0L);

        assertEquals(Outcome.TIMEOUT, res.outcome());
        assertTrue(interruptedLatch.await(1, TimeUnit.SECONDS), "Il virtual thread deve ricevere InterruptedException");
        assertTrue(wasInterrupted.get());
    }
}
