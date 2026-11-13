package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.StepMetric;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolExecutionPolicy;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ToolInvoker;

class ToolPipelineStepTest {

    @Test
    void testConstructorsAndAccessors() {
        ToolRegistry registry = new ToolRegistry();
        ToolPipelineStep step1 = new ToolPipelineStep(null, "tool1", registry);
        assertEquals("tool_step", step1.getId());
        assertEquals("tool1", step1.getToolName());
        assertEquals("ToolPipelineStep[tool1]", step1.getName());

        ToolPipelineStep step2 = new ToolPipelineStep("my_step", "tool2", registry, Map.of("k", "v"));
        assertEquals("my_step", step2.getId());
        assertEquals("tool2", step2.getToolName());
    }

    @Test
    void testExecuteNullRegistry() {
        ToolPipelineStep step = new ToolPipelineStep("step1", "any_tool", null);
        PipelineContext ctx = new PipelineContext("test", DocumentType.EMAIL);
        ValidationResult result = step.execute(ctx);

        assertFalse(result.isValid());
        assertTrue(result.errors().get(0).contains("ToolRegistry non inizializzato"));
    }

    @Test
    void testExecuteToolNotFound() {
        ToolRegistry registry = new ToolRegistry();
        ToolPipelineStep step = new ToolPipelineStep("step1", "missing_tool", registry);
        PipelineContext ctx = new PipelineContext("test", DocumentType.EMAIL);
        ValidationResult result = step.execute(ctx);

        assertFalse(result.isValid());
        assertTrue(result.errors().get(0).contains("non trovato in ToolRegistry"));
    }

    @Test
    void testExecuteToolSuccessDepositsContextAttributes() {
        ToolRegistry registry = new ToolRegistry();
        Tool mockTool = mock(Tool.class);
        when(mockTool.getName()).thenReturn("company_roles_lookup");
        when(mockTool.execute(any())).thenReturn(ToolResult.ok("Caricato", "{\"roles\": []}"));
        registry.register(mockTool);

        ToolPipelineStep step = new ToolPipelineStep("roles_step", "company_roles_lookup", registry);
        PipelineContext ctx = new PipelineContext("test", DocumentType.EMAIL);
        ValidationResult result = step.execute(ctx);

        assertTrue(result.isValid());
        assertEquals("{\"roles\": []}", ctx.getAttribute("tool.roles_step"));
        assertEquals("{\"roles\": []}", ctx.getAttribute("company_roles_lookup"));
        assertEquals("{\"roles\": []}", ctx.getAttribute("company_roles"));
    }

    @Test
    void testExecuteToolWithDeclarativeOutputKeyDepositsCustomContextAttribute() {
        ToolRegistry registry = new ToolRegistry();
        Tool mockTool = mock(Tool.class);
        when(mockTool.getName()).thenReturn("custom_catalog_tool");
        when(mockTool.execute(any())).thenReturn(ToolResult.ok("OK", "{\"items\": [\"A\", \"B\"]}"));
        registry.register(mockTool);

        ToolPipelineStep step = new ToolPipelineStep(
                "custom_step",
                "custom_catalog_tool",
                "custom_catalog_data",
                registry,
                Map.of(),
                ToolExecutionPolicy.defaults(),
                new ToolInvoker()
        );
        assertEquals("custom_catalog_data", step.getOutputKey());

        PipelineContext ctx = new PipelineContext("test", DocumentType.ALTRO);
        ValidationResult result = step.execute(ctx);

        assertTrue(result.isValid());
        assertEquals("{\"items\": [\"A\", \"B\"]}", ctx.getAttribute("tool.custom_step"));
        assertEquals("{\"items\": [\"A\", \"B\"]}", ctx.getAttribute("custom_catalog_tool"));
        assertEquals("{\"items\": [\"A\", \"B\"]}", ctx.getAttribute("custom_catalog_data"));
        assertNull(ctx.getAttribute("company_roles"));
    }

    @Test
    void testExecuteToolReturnsNullResult() {
        ToolRegistry registry = new ToolRegistry();
        Tool mockTool = mock(Tool.class);
        when(mockTool.getName()).thenReturn("null_tool");
        when(mockTool.execute(any())).thenReturn(null);
        registry.register(mockTool);

        ToolPipelineStep step = new ToolPipelineStep("step_null", "null_tool", registry);
        PipelineContext ctx = new PipelineContext("test", DocumentType.EMAIL);
        ValidationResult result = step.execute(ctx);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.TOOL_ERROR, result.fieldErrors().get(0).code());
    }

    @Test
    void testExecuteToolRecordsStepMetricAndHandlesTimeout() {
        ToolRegistry registry = new ToolRegistry();
        Tool slowTool = new Tool() {
            @Override
            public String getName() {
                return "slow_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                try {
                    Thread.sleep(2000);
                    return ToolResult.ok("OK", "x");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ToolResult.failure("Interrupted");
                }
            }
        };
        registry.register(slowTool);

        ToolExecutionPolicy policy = new ToolExecutionPolicy(60L, 0, 10L);
        ToolPipelineStep step = new ToolPipelineStep("step_slow", "slow_tool", registry, Map.of(), policy, new ToolInvoker());
        PipelineContext ctx = new PipelineContext("test", DocumentType.EMAIL);
        ValidationResult result = step.execute(ctx);

        assertFalse(result.isValid());
        assertEquals(ValidationErrorCode.TOOL_TIMEOUT, result.fieldErrors().get(0).code());
        assertEquals(1, ctx.getStepMetrics().size());
        StepMetric metric = ctx.getStepMetrics().get(0);
        assertEquals("TOOL", metric.type());
        assertEquals("slow_tool", metric.model());
        assertTrue(metric.doneReason().contains("TIMEOUT"));
    }
}
