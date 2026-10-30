package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import java.util.Map;

/**
 * Definizione dichiarativa di un'azione tool da eseguire post-pipeline.
 */
public class ActionDefinition {

    private String tool;
    private Map<String, Object> args;

    public ActionDefinition() {
    }

    public ActionDefinition(String tool, Map<String, Object> args) {
        this.tool = tool;
        this.args = args;
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
}
