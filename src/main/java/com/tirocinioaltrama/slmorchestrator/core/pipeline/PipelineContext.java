package com.tirocinioaltrama.slmorchestrator.core.pipeline;

import java.util.HashMap;
import java.util.Map;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.StepMetric;

/**
 * Contesto condiviso e mutabile lungo l'esecuzione dei passi di una {@link Pipeline}.
 */
public class PipelineContext {

    private final String rawInput;
    private String sanitizedInput;
    private final DocumentType documentType;
    private String modelOutput;
    private final Map<String, Object> attributes = new HashMap<>();
    private final java.util.List<StepMetric> stepMetrics = new java.util.ArrayList<>();

    public PipelineContext(String rawInput, DocumentType documentType) {
        this.rawInput = rawInput;
        this.sanitizedInput = rawInput;
        this.documentType = documentType;
    }

    public void recordStepMetric(StepMetric metric) {
        if (metric != null) {
            stepMetrics.add(metric);
        }
    }

    public java.util.List<StepMetric> getStepMetrics() {
        return java.util.Collections.unmodifiableList(stepMetrics);
    }

    public String getRawInput() {
        return rawInput;
    }

    public String getSanitizedInput() {
        return sanitizedInput;
    }

    public void setSanitizedInput(String sanitizedInput) {
        this.sanitizedInput = sanitizedInput;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getModelOutput() {
        return modelOutput;
    }

    public void setModelOutput(String modelOutput) {
        this.modelOutput = modelOutput;
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    private String feedbackPrompt;
    private String overrideModelName;
    private Map<String, Object> samplingOptions;
    private long deadlineEpochMs;

    public long getDeadlineEpochMs() {
        return deadlineEpochMs;
    }

    public void setDeadlineEpochMs(long deadlineEpochMs) {
        this.deadlineEpochMs = deadlineEpochMs;
    }

    public String getFeedbackPrompt() {
        return feedbackPrompt;
    }

    public void setFeedbackPrompt(String feedbackPrompt) {
        this.feedbackPrompt = feedbackPrompt;
    }

    public String getOverrideModelName() {
        return overrideModelName;
    }

    public void setOverrideModelName(String overrideModelName) {
        this.overrideModelName = overrideModelName;
    }

    public Map<String, Object> getSamplingOptions() {
        return samplingOptions;
    }

    public void setSamplingOptions(Map<String, Object> samplingOptions) {
        this.samplingOptions = samplingOptions;
    }

    /**
     * Azzera lo stato transitorio del retry (feedback, override del modello di escalation
     * e opzioni di campionamento) dopo il completamento valido di uno step.
     */
    public void clearRetryState() {
        this.feedbackPrompt = null;
        this.overrideModelName = null;
        this.samplingOptions = null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        Object val = attributes.get(key);
        return type.isInstance(val) ? (T) val : null;
    }
}
