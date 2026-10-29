package com.tirocinioaltrama.llmorchestrator.impl.pipeline;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.llmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;

/**
 * Passo della pipeline che invoca il modello worker SLM tramite {@link ModelClient}.
 * <p>
 * Nell'architettura "Single Base Model + Dynamic System Prompt" lo step trasporta il System Prompt
 * specifico della propria pipeline, inviato come messaggio {@code system}, mentre il messaggio
 * {@code user} contiene l'eventuale {@code <feedback>} seguito dal {@code <payload>}.
 */
public class ModelInferenceStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ModelInferenceStep.class);

    private final String id;
    private final ModelClient modelClient;
    private final String modelName;
    private final String jsonSchema;
    private final String systemPrompt;

    public ModelInferenceStep(ModelClient modelClient, String modelName, String jsonSchema) {
        this(null, modelClient, modelName, jsonSchema, null);
    }

    public ModelInferenceStep(String id, ModelClient modelClient, String modelName, String jsonSchema) {
        this(id, modelClient, modelName, jsonSchema, null);
    }

    public ModelInferenceStep(String id, ModelClient modelClient, String modelName, String jsonSchema, String systemPrompt) {
        this.id = id != null ? id : "inference";
        this.modelClient = modelClient;
        this.modelName = modelName;
        this.jsonSchema = jsonSchema;
        this.systemPrompt = systemPrompt != null && !systemPrompt.isBlank() ? systemPrompt : null;
    }

    public String getId() {
        return id;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public String getName() {
        return "ModelInferenceStep[" + modelName + "]";
    }

    @Override
    public ValidationResult execute(PipelineContext context) {
        StringBuilder promptBuilder = new StringBuilder();
        if (context.getFeedbackPrompt() != null && !context.getFeedbackPrompt().isBlank()) {
            promptBuilder.append("<feedback>\n")
                    .append(context.getFeedbackPrompt().strip())
                    .append("\n</feedback>\n");
        }
        promptBuilder.append("<payload>\n")
                .append(context.getSanitizedInput())
                .append("\n</payload>");

        String effectiveModel = context.getOverrideModelName() != null && !context.getOverrideModelName().isBlank()
                ? context.getOverrideModelName()
                : modelName;

        ModelRequest request = new ModelRequest(
                effectiveModel,
                promptBuilder.toString(),
                jsonSchema,
                context.getSamplingOptions(),
                systemPrompt
        );

        try {
            log.debug("Invocazione modello SLM '{}'...", effectiveModel);
            ModelResponse response = modelClient.generate(request);
            if (response == null || response.content() == null || response.content().isBlank()) {
                String msg = "Il modello SLM ha restituito una risposta vuota o nulla.";
                return ValidationResult.withFieldErrors(
                        List.of(new com.tirocinioaltrama.llmorchestrator.core.validation.FieldError("$", "system", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, msg)),
                        List.of()
                );
            }
            context.setModelOutput(response.content());
            context.setAttribute("latencyMs", response.latencyMs());

            int pTokens = response.metrics() != null ? response.metrics().promptTokens() : 0;
            int cTokens = response.metrics() != null ? response.metrics().completionTokens() : 0;
            String doneReason = response.metrics() != null ? response.metrics().doneReason() : null;
            context.recordStepMetric(new com.tirocinioaltrama.llmorchestrator.core.model.StepMetric(
                    getId(),
                    "MODEL_INFERENCE",
                    effectiveModel,
                    response.latencyMs(),
                    pTokens,
                    cTokens,
                    doneReason
            ));

            return ValidationResult.valid();
        } catch (Exception e) {
            log.error("Errore durante l'invocazione del modello '{}': {}", effectiveModel, e.getMessage(), e);
            String msg = "Errore durante l'elaborazione del modello SLM: " + e.getMessage();
            return ValidationResult.withFieldErrors(
                    List.of(new com.tirocinioaltrama.llmorchestrator.core.validation.FieldError("$", "system", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR, msg)),
                    List.of()
            );
        }
    }
}
