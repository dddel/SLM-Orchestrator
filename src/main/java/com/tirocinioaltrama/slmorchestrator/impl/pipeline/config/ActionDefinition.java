package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import java.util.Map;

/**
 * Definizione dichiarativa di un'azione tool da eseguire post-pipeline.
 */
public class ActionDefinition {

    private String tool;
    private Map<String, Object> args;
    private Long timeoutMs;
    private Integer maxRetries;
    private Long retryBackoffMs;

    public ActionDefinition() {
    }

    public ActionDefinition(String tool, Map<String, Object> args) {
        this(tool, args, null, null, null);
    }

    public ActionDefinition(String tool, Map<String, Object> args, Long timeoutMs, Integer maxRetries, Long retryBackoffMs) {
        this.tool = tool;
        this.args = args;
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(Long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }
}
