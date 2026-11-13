package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.slmorchestrator.core.model.StepMetric;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolExecutionPolicy;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ToolInvoker;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ToolInvoker.ToolInvocationResult;

/**
 * Passo di pipeline che esegue un {@link Tool} registrato in {@link ToolRegistry}.
 * Consente il caricamento o la consultazione di servizi/dati esterni (come mock di ruoli)
 * e ne deposita l'output nel {@link PipelineContext} per i passi successivi.
 */
public class ToolPipelineStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ToolPipelineStep.class);

    private final String id;
    private final String toolName;
    private final ToolRegistry toolRegistry;
    private final Map<String, Object> args;
    private final ToolExecutionPolicy policy;
    private final ToolInvoker toolInvoker;

    public ToolPipelineStep(String id, String toolName, ToolRegistry toolRegistry) {
        this(id, toolName, toolRegistry, Map.of(), ToolExecutionPolicy.defaults(), new ToolInvoker());
    }

    public ToolPipelineStep(String id, String toolName, ToolRegistry toolRegistry, Map<String, Object> args) {
        this(id, toolName, toolRegistry, args, ToolExecutionPolicy.defaults(), new ToolInvoker());
    }

    public ToolPipelineStep(
            String id,
            String toolName,
            ToolRegistry toolRegistry,
            Map<String, Object> args,
            ToolExecutionPolicy policy,
            ToolInvoker toolInvoker
    ) {
        this.id = id != null ? id : "tool_step";
        this.toolName = toolName;
        this.toolRegistry = toolRegistry;
        this.args = args != null ? Map.copyOf(args) : Map.of();
        this.policy = policy != null ? policy : ToolExecutionPolicy.defaults();
        this.toolInvoker = toolInvoker != null ? toolInvoker : new ToolInvoker();
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
    }

    public ToolExecutionPolicy getPolicy() {
        return policy;
    }

    @Override
    public String getName() {
        return "ToolPipelineStep[" + toolName + "]";
    }

    @Override
    public ValidationResult execute(PipelineContext context) {
        if (context == null) {
            String msg = "PipelineContext non può essere nullo per lo step '" + id + "'.";
            log.error(msg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", ValidationErrorCode.TOOL_ERROR, msg)),
                    List.of()
            );
        }

        if (toolRegistry == null) {
            String msg = "ToolRegistry non inizializzato per lo step '" + id + "'.";
            log.error(msg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", ValidationErrorCode.TOOL_ERROR, msg)),
                    List.of()
            );
        }

        Tool tool = toolRegistry.get(toolName).orElse(null);
        if (tool == null) {
            String msg = "Tool '" + toolName + "' specificato nello step '" + id + "' non trovato in ToolRegistry.";
            log.error(msg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", ValidationErrorCode.TOOL_ERROR, msg)),
                    List.of()
            );
        }

        log.debug("Esecuzione step tool '{}' (id: '{}', timeoutMs={}, maxRetries={})...",
                toolName, id, policy.timeoutMs(), policy.maxRetries());
        ToolInvocationResult invRes = toolInvoker.invoke(tool, args, policy, context.getDeadlineEpochMs());
        context.recordStepMetric(new StepMetric(
                id,
                "TOOL",
                toolName,
                invRes.latencyMs(),
                0,
                0,
                invRes.outcome().name() + "/attempts=" + invRes.attempts()
        ));

        if (!invRes.isSuccess()) {
            ToolResult result = invRes.result();
            String errMsg = invRes.errorDetail() != null
                    ? invRes.errorDetail()
                    : (result != null && result.message() != null ? result.message() : "Esecuzione tool fallita");
            ValidationErrorCode code = (invRes.outcome() == ToolInvoker.Outcome.TIMEOUT)
                    ? ValidationErrorCode.TOOL_TIMEOUT
                    : ValidationErrorCode.TOOL_ERROR;
            log.warn("Step tool '{}' fallito ({}): {}", id, code, errMsg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", code, errMsg)),
                    List.of()
            );
        }

        ToolResult result = invRes.result();
        Object payload = result.data() != null ? result.data() : result.message();
        context.setAttribute("tool." + id, payload);
        context.setAttribute(toolName, payload);
        if ("company_roles_lookup".equalsIgnoreCase(toolName)) {
            context.setAttribute("company_roles", payload);
        }
        log.debug("Step tool '{}' completato con successo in {} ms (tentativi: {}).", id, invRes.latencyMs(), invRes.attempts());
        return ValidationResult.valid();
    }
}
