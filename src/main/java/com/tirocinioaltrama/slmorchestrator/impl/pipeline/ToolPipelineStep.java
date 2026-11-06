package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

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

    public ToolPipelineStep(String id, String toolName, ToolRegistry toolRegistry) {
        this(id, toolName, toolRegistry, Map.of());
    }

    public ToolPipelineStep(String id, String toolName, ToolRegistry toolRegistry, Map<String, Object> args) {
        this.id = id != null ? id : "tool_step";
        this.toolName = toolName;
        this.toolRegistry = toolRegistry;
        this.args = args != null ? Map.copyOf(args) : Map.of();
    }

    public String getId() {
        return id;
    }

    public String getToolName() {
        return toolName;
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
                    List.of(new FieldError(id, "tool", ValidationErrorCode.INFERENCE_ERROR, msg)),
                    List.of()
            );
        }

        if (toolRegistry == null) {
            String msg = "ToolRegistry non inizializzato per lo step '" + id + "'.";
            log.error(msg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", ValidationErrorCode.INFERENCE_ERROR, msg)),
                    List.of()
            );
        }

        Tool tool = toolRegistry.get(toolName).orElse(null);
        if (tool == null) {
            String msg = "Tool '" + toolName + "' specificato nello step '" + id + "' non trovato in ToolRegistry.";
            log.error(msg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", ValidationErrorCode.INFERENCE_ERROR, msg)),
                    List.of()
            );
        }

        try {
            log.debug("Esecuzione step tool '{}' (id: '{}')...", toolName, id);
            ToolResult result = tool.execute(args);
            if (result == null || !result.success()) {
                String errMsg = result != null && result.message() != null ? result.message() : "Esecuzione tool fallita";
                log.warn("Step tool '{}' fallito: {}", id, errMsg);
                return ValidationResult.withFieldErrors(
                        List.of(new FieldError(id, "tool", ValidationErrorCode.INFERENCE_ERROR, errMsg)),
                        List.of()
                );
            }

            Object payload = result.data() != null ? result.data() : result.message();
            context.setAttribute("tool." + id, payload);
            context.setAttribute(toolName, payload);
            if ("company_roles_lookup".equalsIgnoreCase(toolName)) {
                context.setAttribute("company_roles", payload);
            }
            log.debug("Step tool '{}' completato con successo.", id);
            return ValidationResult.valid();
        } catch (Exception e) {
            String msg = "Eccezione durante l'esecuzione del tool nello step '" + id + "': " + e.getMessage();
            log.error(msg, e);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(id, "tool", ValidationErrorCode.INFERENCE_ERROR, msg)),
                    List.of()
            );
        }
    }
}
