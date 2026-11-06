package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import java.util.List;

/**
 * Raggruppamento delle azioni post-pipeline distinte per esito (on_ok, on_indeterminate).
 */
public class ActionsGroupDefinition {

    private List<ActionDefinition> onOk;
    private List<ActionDefinition> onIndeterminate;

    public ActionsGroupDefinition() {
    }

    public List<ActionDefinition> getOnOk() {
        return onOk;
    }

    public void setOnOk(List<ActionDefinition> onOk) {
        this.onOk = onOk;
    }

    public List<ActionDefinition> getOnIndeterminate() {
        return onIndeterminate;
    }

    public void setOnIndeterminate(List<ActionDefinition> onIndeterminate) {
        this.onIndeterminate = onIndeterminate;
    }
}
