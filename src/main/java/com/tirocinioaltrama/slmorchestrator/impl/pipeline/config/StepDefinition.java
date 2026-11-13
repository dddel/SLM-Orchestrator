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
    private String outputKey; // Per type: tool (chiave nel contesto in cui salvare il risultato)
    private Map<String, Object> args; // Argomenti opzionali per type: tool
    private Map<String, Object> onFailure; // on_failure opzionale
    private OnFailureDefinition onFailureDefinition;
    private Map<String, String> injectContext; // Per type: model_inference (tag XML -> chiave attributo nel contesto)
    private List<String> mergeFields; // Per type: model_inference (campi autorevoli da unire al JSON precedente)
    private List<FieldValidatorDefinition> validators;
    private List<String> unvalidated;
    private CatalogValidationDefinition catalogValidation; // Per type: field_validation (validazione su catalogo dinamico)
    private List<String> coherenceChecks; // Per type: field_validation (es. ["anagrafica"])
    private String candidatesSource;
    private String targetField;
    private Boolean shuffleCandidates;
    private Long timeoutMs;
    private Integer maxRetries;
    private Long retryBackoffMs;

    public StepDefinition() {
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

    public String getOutputKey() {
        return outputKey;
    }

    public void setOutputKey(String outputKey) {
        this.outputKey = outputKey;
    }

    public Map<String, String> getInjectContext() {
        return injectContext;
    }

    public void setInjectContext(Map<String, String> injectContext) {
        this.injectContext = injectContext;
    }

    public List<String> getMergeFields() {
        return mergeFields;
    }

    public void setMergeFields(List<String> mergeFields) {
        this.mergeFields = mergeFields;
    }

    public CatalogValidationDefinition getCatalogValidation() {
        return catalogValidation;
    }

    public void setCatalogValidation(CatalogValidationDefinition catalogValidation) {
        this.catalogValidation = catalogValidation;
    }

    public List<String> getCoherenceChecks() {
        return coherenceChecks;
    }

    public void setCoherenceChecks(List<String> coherenceChecks) {
        this.coherenceChecks = coherenceChecks;
    }

    /**
     * DTO per la definizione della validazione di appartenenza a un catalogo dinamico nel contesto.
     */
    public static class CatalogValidationDefinition {
        private String source;
        private List<String> fields;

        public CatalogValidationDefinition() {
        }

        public CatalogValidationDefinition(String source, List<String> fields) {
            this.source = source;
            this.fields = fields;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields;
        }
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
