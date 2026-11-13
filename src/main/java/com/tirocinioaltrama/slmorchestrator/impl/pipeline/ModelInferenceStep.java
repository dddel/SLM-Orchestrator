package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import java.util.List;

import com.tirocinioaltrama.slmorchestrator.core.model.StepMetric;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

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

        Object companyRoles = context.getAttribute("company_roles");
        if (companyRoles == null) {
            companyRoles = context.getAttribute("company_roles_lookup");
        }
        if (companyRoles == null) {
            companyRoles = context.getAttribute("tool.roles_lookup");
        }
        if (companyRoles != null) {
            promptBuilder.append("\n<company_roles>\n")
                    .append(companyRoles.toString().strip())
                    .append("\n</company_roles>");
        }

        String emailExtraction = context.getAttribute("email.extraction_output", String.class);
        if (companyRoles != null && emailExtraction == null && context.getModelOutput() != null) {
            emailExtraction = context.getModelOutput();
            context.setAttribute("email.extraction_output", emailExtraction);
        }

        if (emailExtraction != null && companyRoles != null) {
            promptBuilder.append("\n<extracted_info>\n")
                    .append(emailExtraction.strip())
                    .append("\n</extracted_info>");
        }

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
                        List.of(new FieldError("$", "system", ValidationErrorCode.INFERENCE_ERROR, msg)),
                        List.of()
                );
            }
            String rawOutput = response.content();
            context.setAttribute("step." + getId() + ".output", rawOutput);

            if (emailExtraction != null && companyRoles != null) {
                String merged = mergeJsonOutputs(emailExtraction, rawOutput);
                context.setModelOutput(merged);
            } else {
                context.setModelOutput(rawOutput);
            }

            context.setAttribute("latencyMs", response.latencyMs());

            int pTokens = response.metrics() != null ? response.metrics().promptTokens() : 0;
            int cTokens = response.metrics() != null ? response.metrics().completionTokens() : 0;
            String doneReason = response.metrics() != null ? response.metrics().doneReason() : null;
            context.recordStepMetric(new StepMetric(
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
            ValidationErrorCode errorCode = isModelUnavailable(e)
                    ? ValidationErrorCode.MODEL_UNAVAILABLE
                    : ValidationErrorCode.INFERENCE_ERROR;
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("$", "system", errorCode, msg)),
                    List.of()
            );
        }
    }

    private static boolean isModelUnavailable(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof org.springframework.web.client.ResourceAccessException
                    || curr instanceof org.springframework.web.client.HttpServerErrorException
                    || curr instanceof java.net.ConnectException
                    || curr instanceof java.net.SocketTimeoutException
                    || curr instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }

    private String mergeJsonOutputs(String previousOutput, String newOutput) {
        if (previousOutput == null || previousOutput.isBlank()) {
            return newOutput;
        }
        if (newOutput == null || newOutput.isBlank()) {
            return previousOutput;
        }
        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            tools.jackson.databind.JsonNode prevNode = mapper.readTree(previousOutput);
            tools.jackson.databind.JsonNode newNode = mapper.readTree(newOutput);
            if (prevNode.isObject() && newNode.isObject()) {
                tools.jackson.databind.node.ObjectNode prevObj = (tools.jackson.databind.node.ObjectNode) prevNode;
                tools.jackson.databind.node.ObjectNode newObj = (tools.jackson.databind.node.ObjectNode) newNode;
                java.util.Set<String> recipientFields = java.util.Set.of("destinatario", "ruoloDestinatario", "motivoInstradamento");
                boolean added = false;
                for (var entry : newObj.properties()) {
                    String key = entry.getKey();
                    tools.jackson.databind.JsonNode val = entry.getValue();
                    if (recipientFields.contains(key)) {
                        // Per i campi di instradamento/destinatario, lo step dedicato è autorevole
                        prevObj.set(key, val);
                        added = true;
                    } else if (!prevObj.has(key)
                            || prevObj.get(key).isNull()
                            || (prevObj.get(key).isTextual() && prevObj.get(key).asString().isBlank())) {
                        prevObj.set(key, val);
                        added = true;
                    }
                }
                if (!added) {
                    return previousOutput;
                }
                return mapper.writeValueAsString(prevObj);
            }
        } catch (Exception e) {
            log.debug("Merge JSON non applicato: {}", e.getMessage());
        }
        return newOutput;
    }
}
