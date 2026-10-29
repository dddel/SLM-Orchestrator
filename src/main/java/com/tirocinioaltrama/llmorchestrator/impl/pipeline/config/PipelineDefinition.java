package com.tirocinioaltrama.llmorchestrator.impl.pipeline.config;

import java.util.List;

/**
 * Modello DTO per la definizione di una pipeline dichiarativa caricata da file YAML.
 */
public class PipelineDefinition {

    private String id;
    private String documentType;
    private List<StepDefinition> steps;
    private ActionsGroupDefinition actions;

    public PipelineDefinition() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public List<StepDefinition> getSteps() {
        return steps;
    }

    public void setSteps(List<StepDefinition> steps) {
        this.steps = steps;
    }

    public ActionsGroupDefinition getActions() {
        return actions;
    }

    public void setActions(ActionsGroupDefinition actions) {
        this.actions = actions;
    }
}
