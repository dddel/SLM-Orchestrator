package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Passo della pipeline che invoca il modello worker SLM tramite {@link ModelClient}.
 * <p>
 * Nell'architettura "Single Base Model + Dynamic System Prompt" lo step trasporta il System Prompt
 * specifico della propria pipeline, inviato come messaggio {@code system}, mentre il messaggio
 * {@code user} contiene l'eventuale {@code <feedback>} seguito dal {@code <payload>} e dagli
 * eventuali blocchi dichiarativi configurati tramite {@code inject_context}.
 */
public class ModelInferenceStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(ModelInferenceStep.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> DEFAULT_MERGE_FIELDS = Set.of(
            "destinatario", "ruoloDestinatario", "motivoInstradamento"
    );

    private final String id;
    private final ModelClient modelClient;
    private final String modelName;
    private final String jsonSchema;
    private final String systemPrompt;
    private final Map<String, String> injectContext;
    private final Set<String> mergeFields;

    public ModelInferenceStep(ModelClient modelClient, String modelName, String jsonSchema) {
        this(null, modelClient, modelName, jsonSchema, null, null, null);
    }

    public ModelInferenceStep(String id, ModelClient modelClient, String modelName, String jsonSchema) {
        this(id, modelClient, modelName, jsonSchema, null, null, null);
    }

    public ModelInferenceStep(String id, ModelClient modelClient, String modelName, String jsonSchema, String systemPrompt) {
        this(id, modelClient, modelName, jsonSchema, systemPrompt, null, null);
    }

    public ModelInferenceStep(
            String id,
            ModelClient modelClient,
            String modelName,
            String jsonSchema,
            String systemPrompt,
            Map<String, String> injectContext,
            List<String> mergeFields
    ) {
        this.id = id != null ? id : "inference";
        this.modelClient = modelClient;
        this.modelName = modelName;
        this.jsonSchema = jsonSchema;
        this.systemPrompt = systemPrompt != null && !systemPrompt.isBlank() ? systemPrompt : null;
        if (injectContext != null && !injectContext.isEmpty()) {
            this.injectContext = Collections.unmodifiableMap(new LinkedHashMap<>(injectContext));
        } else {
            this.injectContext = Map.of();
        }
        if (mergeFields != null && !mergeFields.isEmpty()) {
            this.mergeFields = Set.copyOf(mergeFields);
        } else {
            this.mergeFields = Set.of();
        }
    }

    public String getId() {
        return id;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Map<String, String> getInjectContext() {
        return injectContext;
    }

    public Set<String> getMergeFields() {
        return mergeFields;
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

        String baseOutputForMerge = resolveContextAndEnrichPrompt(context, promptBuilder);

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

            if (baseOutputForMerge != null) {
                Set<String> effectiveMergeFields = !mergeFields.isEmpty() ? mergeFields : DEFAULT_MERGE_FIELDS;
                String merged = mergeJsonOutputs(baseOutputForMerge, rawOutput, effectiveMergeFields);
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

            Integer configuredNumCtx = context.getAttribute("context.num_ctx", Integer.class);
            Integer configuredNumPredict = context.getAttribute("context.num_predict", Integer.class);
            int numCtx = configuredNumCtx != null && configuredNumCtx > 0 ? configuredNumCtx : 2048;
            int numPredict = configuredNumPredict != null && configuredNumPredict > 0 ? configuredNumPredict : 400;
            if (pTokens > 0 && pTokens >= (numCtx - numPredict - 16)) {
                String truncMsg = String.format(
                        "Saturazione finestra di contesto rilevata post-inferenza nello step '%s': prompt_eval_count=%d token su num_ctx=%d (num_predict=%d).",
                        getId(), pTokens, numCtx, numPredict
                );
                log.warn(truncMsg);
                return ValidationResult.withFieldErrors(
                        List.of(new FieldError("$", "context", ValidationErrorCode.CONTEXT_TRUNCATED, truncMsg)),
                        List.of()
                );
            }

            if ("length".equalsIgnoreCase(doneReason)) {
                String outTruncMsg = String.format(
                        "Output del modello troncato per raggiungimento del limite num_predict nello step '%s' (done_reason=length, eval_count=%d).",
                        getId(), cTokens
                );
                log.warn(outTruncMsg);
                return ValidationResult.withFieldErrors(
                        List.of(new FieldError("$", "output", ValidationErrorCode.OUTPUT_TRUNCATED, outTruncMsg)),
                        List.of()
                );
            }

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

    /**
     * Risolve gli attributi di contesto dichiarati in {@code inject_context} (o il fallback legacy)
     * e arricchisce il prompt utente con i blocchi XML corrispondenti.
     * Restituisce l'output JSON precedente con cui effettuare il merge post-inferenza, oppure {@code null}.
     */
    private String resolveContextAndEnrichPrompt(PipelineContext context, StringBuilder promptBuilder) {
        if (!injectContext.isEmpty()) {
            Map<String, String> resolvedBlocks = new LinkedHashMap<>();
            String priorStepOutput = null;
            boolean hasExternalContextRequirement = false;
            boolean allExternalRequirementsMet = true;

            for (Map.Entry<String, String> entry : injectContext.entrySet()) {
                String xmlTag = entry.getKey();
                String attrKey = entry.getValue();
                boolean isStepOutputRef = attrKey.startsWith("step.") || "$previous_output".equals(attrKey);
                if (isStepOutputRef) {
                    String val = peekPriorOutput(context, attrKey);
                    if (val != null && !val.isBlank()) {
                        priorStepOutput = val;
                        resolvedBlocks.put(xmlTag, val.strip());
                    }
                } else {
                    hasExternalContextRequirement = true;
                    Object val = resolveAttributeWithFallback(context, attrKey);
                    if (val != null && !val.toString().isBlank()) {
                        resolvedBlocks.put(xmlTag, val.toString().strip());
                    } else {
                        allExternalRequirementsMet = false;
                    }
                }
            }

            if (!hasExternalContextRequirement || allExternalRequirementsMet) {
                for (Map.Entry<String, String> block : resolvedBlocks.entrySet()) {
                    promptBuilder.append("\n<").append(block.getKey()).append(">\n")
                            .append(block.getValue())
                            .append("\n</").append(block.getKey()).append(">");
                }
                if (priorStepOutput == null && !mergeFields.isEmpty()) {
                    priorStepOutput = peekPriorOutput(context, "$previous_output");
                }
                if (priorStepOutput != null) {
                    context.setAttribute("merge_base." + id, priorStepOutput);
                    if (context.getAttribute("email.extraction_output") == null) {
                        context.setAttribute("email.extraction_output", priorStepOutput);
                    }
                }
                return priorStepOutput;
            }
            return null;
        }

        if (!mergeFields.isEmpty()) {
            String prior = peekPriorOutput(context, "$previous_output");
            if (prior != null) {
                context.setAttribute("merge_base." + id, prior);
                if (context.getAttribute("email.extraction_output") == null) {
                    context.setAttribute("email.extraction_output", prior);
                }
            }
            return prior;
        }

        // Fallback di retrocompatibilità per istanziazioni dirette da codice senza inject_context YAML
        Object companyRoles = resolveAttributeWithFallback(context, "company_roles");
        if (companyRoles != null) {
            promptBuilder.append("\n<company_roles>\n")
                    .append(companyRoles.toString().strip())
                    .append("\n</company_roles>");
            String emailExtraction = peekPriorOutput(context, "$previous_output");
            if (emailExtraction != null) {
                context.setAttribute("merge_base." + id, emailExtraction);
                if (context.getAttribute("email.extraction_output") == null) {
                    context.setAttribute("email.extraction_output", emailExtraction);
                }
                promptBuilder.append("\n<extracted_info>\n")
                        .append(emailExtraction.strip())
                        .append("\n</extracted_info>");
                return emailExtraction;
            }
        }
        return null;
    }

    private static Object resolveAttributeWithFallback(PipelineContext context, String attrKey) {
        Object val = context.getAttribute(attrKey);
        if (val == null && "company_roles".equals(attrKey)) {
            val = context.getAttribute("company_roles_lookup");
            if (val == null) {
                val = context.getAttribute("tool.roles_lookup");
            }
        }
        return val;
    }

    private String peekPriorOutput(PipelineContext context, String attrKey) {
        if (attrKey != null && !"$previous_output".equals(attrKey)) {
            Object stepOut = context.getAttribute(attrKey);
            if (stepOut instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        String stepSnapshot = context.getAttribute("merge_base." + id, String.class);
        if (stepSnapshot != null && !stepSnapshot.isBlank()) {
            return stepSnapshot;
        }
        String existingSnapshot = context.getAttribute("email.extraction_output", String.class);
        if (existingSnapshot != null && !existingSnapshot.isBlank()) {
            return existingSnapshot;
        }
        String currentModelOut = context.getModelOutput();
        if (currentModelOut != null && !currentModelOut.isBlank()) {
            return currentModelOut;
        }
        return null;
    }

    private String mergeJsonOutputs(String previousOutput, String newOutput, Set<String> authoritativeFields) {
        if (previousOutput == null || previousOutput.isBlank()) {
            return newOutput;
        }
        if (newOutput == null || newOutput.isBlank()) {
            return previousOutput;
        }
        try {
            JsonNode prevNode = OBJECT_MAPPER.readTree(previousOutput);
            JsonNode newNode = OBJECT_MAPPER.readTree(newOutput);
            if (prevNode.isObject() && newNode.isObject()) {
                ObjectNode prevObj = (ObjectNode) prevNode;
                ObjectNode newObj = (ObjectNode) newNode;
                boolean added = false;
                for (var entry : newObj.properties()) {
                    String key = entry.getKey();
                    JsonNode val = entry.getValue();
                    if (authoritativeFields.contains(key)) {
                        // Per i campi dichiarati in merge_fields, questo step è autorevole
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
                return OBJECT_MAPPER.writeValueAsString(prevObj);
            }
        } catch (Exception e) {
            log.debug("Merge JSON non applicato: {}", e.getMessage());
        }
        return newOutput;
    }
}
