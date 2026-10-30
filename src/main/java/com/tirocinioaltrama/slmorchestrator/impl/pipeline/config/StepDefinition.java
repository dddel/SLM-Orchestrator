package com.tirocinioaltrama.slmorchestrator.impl.pipeline.config;

import java.util.List;
import java.util.Map;

/**
 * Modello DTO per la definizione di uno step di una pipeline caricata da configurazione YAML.
 */
public class StepDefinition {

    private String id;
    private String type;
    private String model;
    private String schema;
    private String systemPrompt; // system_prompt: path risorsa o testo inline
    private String name; // Per type: tool
    private String tool; // Per type: tool (chiave alternativa/preferita 'tool')
    private Map<String, Object> args; // Argomenti opzionali per type: tool
    private Map<String, Object> onFailure; // on_failure opzionale
    private OnFailureDefinition onFailureDefinition;
    private List<FieldValidatorDefinition> validators;
    private List<String> unvalidated;
    private String candidatesSource;
    private String targetField;
    private Boolean shuffleCandidates;

    public StepDefinition() {
    }

    public Boolean getShuffleCandidates() {
        return shuffleCandidates;
    }

    public void setShuffleCandidates(Boolean shuffleCandidates) {
        this.shuffleCandidates = shuffleCandidates;
    }

    public String getCandidatesSource() {
        return candidatesSource;
    }

    public void setCandidatesSource(String candidatesSource) {
        this.candidatesSource = candidatesSource;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public OnFailureDefinition getOnFailureDefinition() {
        return onFailureDefinition;
    }

    public void setOnFailureDefinition(OnFailureDefinition onFailureDefinition) {
        this.onFailureDefinition = onFailureDefinition;
    }

    public List<String> getUnvalidated() {
        return unvalidated;
    }

    public void setUnvalidated(List<String> unvalidated) {
        this.unvalidated = unvalidated;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * System prompt dello step model_inference (chiave YAML {@code system_prompt}):
     * path a una risorsa (es. {@code prompts/fattura-system.txt}, {@code classpath:...}, {@code file:...})
     * oppure testo inline.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTool() {
        return tool != null ? tool : name;
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

    public Map<String, Object> getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(Map<String, Object> onFailure) {
        this.onFailure = onFailure;
    }

    public List<FieldValidatorDefinition> getValidators() {
        return validators;
    }

    public void setValidators(List<FieldValidatorDefinition> validators) {
        this.validators = validators;
    }

    /**
     * DTO per la definizione di un singolo validatore di campo.
     */
    public static class FieldValidatorDefinition {
        private String field;
        private String validator;

        public FieldValidatorDefinition() {
        }

        public FieldValidatorDefinition(String field, String validator) {
            this.field = field;
            this.validator = validator;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getValidator() {
            return validator;
        }

        public void setValidator(String validator) {
            this.validator = validator;
        }
    }
}
