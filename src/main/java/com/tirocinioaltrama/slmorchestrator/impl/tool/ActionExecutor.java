package com.tirocinioaltrama.slmorchestrator.impl.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorStatus;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolExecutionPolicy;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.ActionDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.ActionsGroupDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ToolInvoker.ToolInvocationResult;

/**
 * Esecutore isolato per le azioni tool dichiarate nei file YAML post-pipeline (RF8).
 * Esegue le azioni una volta sola, all'uscita della pipeline, senza interferire
 * con lo status formale dei dati ma popolando la lista di errori actionErrors.
 */
@Component
public class ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionExecutor.class);

    private final ToolRegistry toolRegistry;
    private final ToolInvoker toolInvoker;
    private final long defaultTimeoutMs;
    private final int defaultMaxRetries;
    private final long defaultBackoffMs;
    private final ObjectMapper objectMapper;

    public ActionExecutor(ToolRegistry toolRegistry) {
        this(
                toolRegistry,
                new ToolInvoker(),
                ToolExecutionPolicy.DEFAULT_TIMEOUT_MS,
                ToolExecutionPolicy.DEFAULT_MAX_RETRIES,
                ToolExecutionPolicy.DEFAULT_BACKOFF_MS
        );
    }

    @Autowired
    public ActionExecutor(
            ToolRegistry toolRegistry,
            ToolInvoker toolInvoker,
            @Value("${orchestrator.tools.default-timeout-ms:5000}") long defaultTimeoutMs,
            @Value("${orchestrator.tools.default-max-retries:0}") int defaultMaxRetries,
            @Value("${orchestrator.tools.default-retry-backoff-ms:200}") long defaultBackoffMs
    ) {
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        this.toolInvoker = toolInvoker != null ? toolInvoker : new ToolInvoker();
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultMaxRetries = defaultMaxRetries;
        this.defaultBackoffMs = defaultBackoffMs;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Esegue le azioni appropriate (on_ok o on_indeterminate) e restituisce la lista di errori accumulati.
     *
     * @param actionsGroup      definizioni delle azioni associate alla pipeline
     * @param status            esito formale dell'elaborazione (OK o INDETERMINATE)
     * @param result            output validato (se OK)
     * @param unvalidatedResult output grezzo dell'ultimo tentativo (se INDETERMINATE)
     * @param errors            errori di validazione strutturati
     * @return lista di eventuali errori riscontrati durante l'esecuzione dei tool
     */
    public List<String> executeActions(
            ActionsGroupDefinition actionsGroup,
            OrchestratorStatus status,
            String result,
            String unvalidatedResult,
            List<FieldError> errors
    ) {
        if (actionsGroup == null) {
            return List.of();
        }

        List<ActionDefinition> actionsToRun = (status == OrchestratorStatus.OK)
                ? actionsGroup.getOnOk()
                : actionsGroup.getOnIndeterminate();

        if (actionsToRun == null || actionsToRun.isEmpty()) {
            return List.of();
        }

        List<String> actionErrors = new ArrayList<>();

        for (ActionDefinition action : actionsToRun) {
            String toolName = action.getTool();
            Tool tool = toolRegistry.get(toolName).orElse(null);

            if (tool == null) {
                String err = "Tool '" + toolName + "' non trovato in ToolRegistry";
                log.error(err);
                actionErrors.add(err);
                continue;
            }

            Map<String, Object> resolvedArgs = resolveArgs(action.getArgs(), result, unvalidatedResult, errors);
            ToolExecutionPolicy policy = ToolExecutionPolicy.resolve(
                    action.getTimeoutMs(),
                    action.getMaxRetries(),
                    action.getRetryBackoffMs(),
                    defaultTimeoutMs,
                    defaultMaxRetries,
                    defaultBackoffMs
            );

            log.info("Esecuzione azione tool '{}' con parametri {} (timeoutMs={}, maxRetries={})",
                    toolName, resolvedArgs.keySet(), policy.timeoutMs(), policy.maxRetries());
            ToolInvocationResult invRes = toolInvoker.invoke(tool, resolvedArgs, policy, 0L);
            if (!invRes.isSuccess()) {
                ToolResult tr = invRes.result();
                String msg = invRes.errorDetail() != null
                        ? invRes.errorDetail()
                        : (tr != null && tr.message() != null ? tr.message() : "Esecuzione fallita senza messaggio");
                String fullErr = "Tool '" + toolName + "' fallito (" + invRes.outcome() + ", tentativi=" + invRes.attempts() + "): " + msg;
                log.warn(fullErr);
                actionErrors.add(fullErr);
            } else {
                ToolResult tr = invRes.result();
                log.info("Azione tool '{}' completata con successo in {} ms (tentativi={}): {}",
                        toolName, invRes.latencyMs(), invRes.attempts(), tr != null ? tr.message() : "");
            }
        }

        return actionErrors;
    }

    private Map<String, Object> resolveArgs(
            Map<String, Object> rawArgs,
            String result,
            String unvalidatedResult,
            List<FieldError> errors
    ) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawArgs.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String strVal) {
                switch (strVal) {
                    case "$result" -> resolved.put(entry.getKey(), parseJsonOrRaw(result));
                    case "$unvalidatedResult" -> resolved.put(entry.getKey(), parseJsonOrRaw(unvalidatedResult));
                    case "$errors" -> resolved.put(entry.getKey(), errors != null ? errors : List.of());
                    default -> resolved.put(entry.getKey(), strVal);
                }
            } else {
                resolved.put(entry.getKey(), val);
            }
        }
        return resolved;
    }

    private Object parseJsonOrRaw(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(jsonString);
        } catch (Exception e) {
            return jsonString;
        }
    }
}
