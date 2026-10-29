package com.tirocinioaltrama.llmorchestrator.impl.pipeline;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.OnFailureDefinition;

/**
 * Pipeline sequenziale con controllo del flusso a indice per supportare retry (RF7).
 * Gestione dello stato concorrente sicura: i contatori di retry e lo stato di escalation
 * risiedono esclusivamente in variabili locali dello stack o in {@link PipelineContext}.
 *
 * Flusso di recupero (RF7):
 * 1. Fino a max_retries tentativi sullo SLM originale, con sampling modificato (temperature incrementale)
 *    e feedback strutturato privo di canali di injection.
 * 2. Se falliscono tutti i tentativi SLM ed è configurato escalate_to, viene eseguito esattamente 1 tentativo
 *    sul modello di escalation.
 * 3. Se anche l'escalation (o i retry ordinari senza escalation) fallisce, la pipeline restituisce
 *    l'esito di dominio INDETERMINATE preservando l'output grezzo dell'ultimo tentativo per l'operatore.
 */
public class BasePipeline implements Pipeline {

    private static final Logger log = LoggerFactory.getLogger(BasePipeline.class);

    private final DocumentType supportedType;
    private final List<PipelineStep> steps;
    private final Map<String, Integer> stepIndexMap;
    private final long maxDurationMs;
    private final com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionsGroupDefinition actionsGroup;
    private final boolean includeSensitiveValues;

    public BasePipeline(DocumentType supportedType, List<PipelineStep> steps) {
        this(supportedType, steps, 60_000L, null, false);
    }

    public BasePipeline(DocumentType supportedType, List<PipelineStep> steps, long maxDurationMs) {
        this(supportedType, steps, maxDurationMs, null, false);
    }

    public BasePipeline(
            DocumentType supportedType,
            List<PipelineStep> steps,
            long maxDurationMs,
            com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionsGroupDefinition actionsGroup
    ) {
        this(supportedType, steps, maxDurationMs, actionsGroup, false);
    }

    public BasePipeline(
            DocumentType supportedType,
            List<PipelineStep> steps,
            long maxDurationMs,
            com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionsGroupDefinition actionsGroup,
            boolean includeSensitiveValues
    ) {
        this.supportedType = supportedType;
        this.steps = steps != null ? List.copyOf(steps) : List.of();
        this.maxDurationMs = maxDurationMs > 0 ? maxDurationMs : 60_000L;
        this.actionsGroup = actionsGroup;
        this.includeSensitiveValues = includeSensitiveValues;
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < this.steps.size(); i++) {
            PipelineStep step = this.steps.get(i);
            if (step instanceof ModelInferenceStep mis) {
                indexMap.put(mis.getId(), i);
            } else if (step instanceof OutputValidationStep ovs) {
                indexMap.put(ovs.getId(), i);
            }
        }
        this.stepIndexMap = Map.copyOf(indexMap);
    }

    public com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.ActionsGroupDefinition getActionsGroup() {
        return actionsGroup;
    }

    @Override
    public DocumentType getSupportedType() {
        return supportedType;
    }

    @Override
    public PipelineResult execute(PipelineContext context) {
        long start = System.currentTimeMillis();
        List<String> accumulatedWarnings = new ArrayList<>();

        // Contatori locali per-richiesta: nessuna condivisione tra thread
        Map<String, Integer> stepRetryCount = new HashMap<>();
        Map<String, Boolean> stepEscalated = new HashMap<>();

        int currentIndex = 0;
        ValidationResult lastStepResult = null;
        ValidationResult lastValidationResult = null;

        while (currentIndex < steps.size()) {
            long currentElapsed = System.currentTimeMillis() - start;
            if (currentElapsed > maxDurationMs) {
                log.warn("Durata massima superata per pipeline {} ({} ms > {} ms). Transizione a INDETERMINATE.",
                        supportedType, currentElapsed, maxDurationMs);
                com.tirocinioaltrama.llmorchestrator.core.validation.FieldError timeoutError =
                        new com.tirocinioaltrama.llmorchestrator.core.validation.FieldError(
                                "$", "system", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.INFERENCE_ERROR,
                                "Tempo massimo di esecuzione pipeline superato (" + maxDurationMs + " ms)"
                        );
                List<com.tirocinioaltrama.llmorchestrator.core.validation.FieldError> fieldErrors = new ArrayList<>();
                if (lastValidationResult != null && lastValidationResult.fieldErrors() != null) {
                    fieldErrors.addAll(lastValidationResult.fieldErrors());
                }
                if (!fieldErrors.contains(timeoutError)) {
                    fieldErrors.add(timeoutError);
                }
                ValidationResult timeoutResult = ValidationResult.withFieldErrors(fieldErrors, accumulatedWarnings);
                com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics metrics =
                        com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics.of(currentElapsed, context.getStepMetrics());
                return PipelineResult.indeterminate(context.getModelOutput(), timeoutResult, currentElapsed, metrics);
            }

            PipelineStep step = steps.get(currentIndex);
            log.debug("Esecuzione step '{}' (indice {}) per pipeline {}", step.getName(), currentIndex, supportedType);

            ValidationResult stepResult = step.execute(context);
            lastStepResult = stepResult;

            if (stepResult.warnings() != null) {
                accumulatedWarnings.addAll(stepResult.warnings());
            }

            if (!stepResult.isValid()) {
                if (includeSensitiveValues) {
                    log.warn("Step '{}' non valido per pipeline {}: {}", step.getName(), supportedType, stepResult.errors());
                } else {
                    List<String> sanitizedErrors = stepResult.fieldErrors() != null && !stepResult.fieldErrors().isEmpty()
                            ? stepResult.fieldErrors().stream()
                                    .map(fe -> fe.path() + ": " + fe.code())
                                    .toList()
                            : stepResult.errors();
                    log.warn("Step '{}' non valido per pipeline {}: {}", step.getName(), supportedType, sanitizedErrors);
                }

                // Se il fallimento avviene su ModelInferenceStep:
                // Se esiste già un output precedente (guasto durante un retry o escalation), passiamo a INDETERMINATE preservando l'output precedente.
                // Se non esiste alcun output (guasto al primo colpo), fallimento secco infrastrutturale.
                if (step instanceof ModelInferenceStep) {
                    long elapsed = System.currentTimeMillis() - start;
                    com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics metrics =
                            com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics.of(elapsed, context.getStepMetrics());
                    if (context.getModelOutput() != null && !context.getModelOutput().isBlank()) {
                        log.warn("Guasto inferenza durante retry per pipeline {}. Output precedente preservato in INDETERMINATE.", supportedType);
                        List<com.tirocinioaltrama.llmorchestrator.core.validation.FieldError> combinedFieldErrors = new ArrayList<>();
                        if (lastValidationResult != null && lastValidationResult.fieldErrors() != null) {
                            combinedFieldErrors.addAll(lastValidationResult.fieldErrors());
                        }
                        if (stepResult.fieldErrors() != null) {
                            for (com.tirocinioaltrama.llmorchestrator.core.validation.FieldError fe : stepResult.fieldErrors()) {
                                if (!combinedFieldErrors.contains(fe)) {
                                    combinedFieldErrors.add(fe);
                                }
                            }
                        }
                        ValidationResult finalResult = ValidationResult.withFieldErrors(combinedFieldErrors, accumulatedWarnings);
                        return PipelineResult.indeterminate(context.getModelOutput(), finalResult, elapsed, metrics);
                    } else {
                        log.error("Guasto inferenza al primo tentativo per pipeline {}. Fallimento infrastrutturale.", supportedType);
                        return PipelineResult.failure(stepResult, elapsed, metrics);
                    }
                }

                if (step instanceof OutputValidationStep) {
                    lastValidationResult = stepResult;
                }

                // Verifica se lo step possiede una policy on_failure (abilitata su OutputValidationStep)
                OnFailureDefinition onFailure = null;
                if (step instanceof OutputValidationStep ovs) {
                    onFailure = ovs.getOnFailureDefinition();
                }

                if (onFailure != null && onFailure.getRetryStep() != null) {
                    // Controllo cortocircuito: se tutti gli errori sono imputabili al documento sorgente
                    // (es. SOURCE_CHECKSUM_INVALID) e non vi sono errori rimediabili (come FIELD_SWAP_DETECTED o campi required mancanti),
                    // interrompi subito i retry e transita a INDETERMINATE.
                    if (canShortCircuitSourceErrors(stepResult)) {
                        log.warn("Dato algebricamente invalido presente letteralmente nel testo sorgente per pipeline {}. Cortocircuito retry attivo: transizione immediata a INDETERMINATE.", supportedType);
                        long elapsed = System.currentTimeMillis() - start;
                        com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics metrics =
                                com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics.of(elapsed, context.getStepMetrics());
                        return PipelineResult.indeterminate(context.getModelOutput(), stepResult, elapsed, metrics);
                    }

                    String stepId = step instanceof OutputValidationStep ovs ? ovs.getId() : String.valueOf(currentIndex);
                    int currentRetries = stepRetryCount.getOrDefault(stepId, 0);
                    boolean alreadyEscalated = stepEscalated.getOrDefault(stepId, false);

                    // 1. Fase retry ordinario sullo SLM
                    if (currentRetries < onFailure.getMaxRetries()) {
                        int attemptNumber = currentRetries + 1;
                        stepRetryCount.put(stepId, attemptNumber);
                        log.info("Attivazione retry #{} (max: {}) per lo step '{}' -> rilancio di '{}'",
                                attemptNumber, onFailure.getMaxRetries(), stepId, onFailure.getRetryStep());

                        prepareContextForRetry(context, onFailure, stepResult, attemptNumber, null);
                        Integer targetIndex = stepIndexMap.get(onFailure.getRetryStep());
                        if (targetIndex != null) {
                            currentIndex = targetIndex;
                            continue;
                        }
                    }

                    // 2. Fase escalation (1 solo tentativo finale)
                    if (!alreadyEscalated && onFailure.getEscalateTo() != null && !onFailure.getEscalateTo().isBlank()) {
                        stepEscalated.put(stepId, true);
                        String escalateModel = onFailure.getEscalateTo();
                        log.info("Retry esauriti per '{}'. Escalation a modello '{}' -> rilancio di '{}'",
                                stepId, escalateModel, onFailure.getRetryStep());

                        prepareContextForRetry(context, onFailure, stepResult, onFailure.getMaxRetries() + 1, escalateModel);
                        Integer targetIndex = stepIndexMap.get(onFailure.getRetryStep());
                        if (targetIndex != null) {
                            currentIndex = targetIndex;
                            continue;
                        }
                    }

                    // 3. Tutti i tentativi e l'eventuale escalation sono esauriti -> esito INDETERMINATE
                    log.warn("Tentativi e policy di recupero esauriti per pipeline {}. Transizione a INDETERMINATE.", supportedType);
                    long elapsed = System.currentTimeMillis() - start;
                    com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics metrics =
                            com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics.of(elapsed, context.getStepMetrics());
                    return PipelineResult.indeterminate(context.getModelOutput(), stepResult, elapsed, metrics);
                }

                // Nessun retry configurato per lo step: fallimento secco (esito INDETERMINATE se output presente, altrimenti failure)
                long elapsed = System.currentTimeMillis() - start;
                com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics metrics =
                        com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics.of(elapsed, context.getStepMetrics());
                if (context.getModelOutput() != null && !context.getModelOutput().isBlank()) {
                    return PipelineResult.indeterminate(context.getModelOutput(), stepResult, elapsed, metrics);
                }
                return PipelineResult.failure(stepResult, elapsed, metrics);
            }

            // Step valido: pulizia eventuale feedback per passi successivi e avanzamento
            if (step instanceof OutputValidationStep) {
                lastValidationResult = null;
            }
            context.setFeedbackPrompt(null);
            currentIndex++;
        }

        long elapsed = System.currentTimeMillis() - start;
        com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics metrics =
                com.tirocinioaltrama.llmorchestrator.core.model.ExecutionMetrics.of(elapsed, context.getStepMetrics());
        ValidationResult finalValidation = new ValidationResult(true, List.of(), accumulatedWarnings);
        return PipelineResult.success(context.getModelOutput(), finalValidation, elapsed, metrics);
    }

    private void prepareContextForRetry(
            PipelineContext context,
            OnFailureDefinition onFailure,
            ValidationResult stepResult,
            int retryAttempt,
            String escalateModel
    ) {
        if (onFailure.isFeedback()) {
            String feedbackMsg = ValidationFeedbackBuilder.buildFeedbackMessage(stepResult.fieldErrors());
            context.setFeedbackPrompt(feedbackMsg);
        } else {
            context.setFeedbackPrompt(null);
        }

        if (escalateModel != null) {
            context.setOverrideModelName(escalateModel);
            // In escalation usiamo temperature 0.0 standard del modello di escalation
            context.setSamplingOptions(Map.of("temperature", 0.0));
        } else {
            context.setOverrideModelName(null);
            // Variazione sampling: temperature 0.2 al primo retry, 0.4 al secondo, ecc.
            double temperature = Math.min(0.2 * retryAttempt, 0.8);
            context.setSamplingOptions(Map.of("temperature", temperature));
        }
    }

    private boolean canShortCircuitSourceErrors(ValidationResult stepResult) {
        if (stepResult == null || stepResult.fieldErrors() == null || stepResult.fieldErrors().isEmpty()) {
            return false;
        }
        return stepResult.fieldErrors().stream()
                .allMatch(fe -> fe.code() == com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.SOURCE_CHECKSUM_INVALID);
    }
}
