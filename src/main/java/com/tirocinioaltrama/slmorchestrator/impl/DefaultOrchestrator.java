package com.tirocinioaltrama.slmorchestrator.impl;

import java.util.List;
import java.util.Set;

import com.tirocinioaltrama.slmorchestrator.core.*;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.BasePipeline;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.ActionsGroupDefinition;
import com.tirocinioaltrama.slmorchestrator.impl.tool.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tirocinioaltrama.slmorchestrator.core.Orchestrator;
import com.tirocinioaltrama.slmorchestrator.core.RouterRegistry;
import com.tirocinioaltrama.slmorchestrator.core.exception.PipelineExecutionException;
import com.tirocinioaltrama.slmorchestrator.core.exception.RoutingException;
import com.tirocinioaltrama.slmorchestrator.core.exception.ValidationException;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineRegistry;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.InputValidator;
import com.tirocinioaltrama.slmorchestrator.core.validation.LabelValidator;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

/**
 * Implementazione dell'Orchestratore a pipeline con validazione a più livelli:
 * 1. Validazione & sanitizzazione dell'input (dimensione, protezione XML/injection).
 * 2. Routing intelligente tramite selezione del router locale (Local Ollama).
 * 3. Validazione dell'etichetta restituita dal Router.
 * 4. Esecuzione della Pipeline specifica (Worker SLM specializzato + validazione output).
 */
@Service
public class DefaultOrchestrator implements Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrchestrator.class);

    private final RouterRegistry routerRegistry;
    private final PipelineRegistry pipelineRegistry;
    private final InputValidator inputValidator;
    private final LabelValidator labelValidator;
    private final ActionExecutor actionExecutor;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultOrchestrator(
            RouterRegistry routerRegistry,
            PipelineRegistry pipelineRegistry,
            ActionExecutor actionExecutor
    ) {
        this.routerRegistry = routerRegistry;
        this.pipelineRegistry = pipelineRegistry;
        this.actionExecutor = actionExecutor != null
                ? actionExecutor
                : new ActionExecutor(new ToolRegistry());
        this.inputValidator = new InputValidator();
        this.labelValidator = new LabelValidator(Set.copyOf(pipelineRegistry.getSupportedTypes()));
    }

    /**
     * Costruttore di compatibilità per test con singolo Router.
     */
    public DefaultOrchestrator(
            Router router,
            PipelineRegistry pipelineRegistry,
            ActionExecutor actionExecutor
    ) {
        this(createRegistryFromRouter(router), pipelineRegistry, actionExecutor);
    }

    private static RouterRegistry createRegistryFromRouter(Router router) {
        RouterRegistry registry = new RouterRegistry();
        if (router != null) {
            registry.register(RouterType.LOCAL_OLLAMA, router);
            registry.setDefaultRouterType(RouterType.LOCAL_OLLAMA);
        }
        return registry;
    }

    @Override
    public OrchestratorResponse process(OrchestratorRequest request) {
        long startTime = System.currentTimeMillis();

        if (request == null) {
            throw new IllegalArgumentException("La richiesta non può essere nulla.");
        }

        // Controllo null/vuoto gestito direttamente nell'orchestratore
        if (request.payload() == null || request.payload().isBlank()) {
            ValidationResult emptyValidation = ValidationResult.error("Il payload di input non può essere nullo o vuoto.");
            log.error("Validazione input fallita: Il payload di input non può essere nullo o vuoto.");
            throw new ValidationException("Input non valido", emptyValidation);
        }

        // 1. Validazione dell'input grezzo (controllo dimensionale e warning delegati a InputValidator)
        ValidationResult inputValidation = inputValidator.validate(request.payload());
        if (!inputValidation.isValid()) {
            String errorMsg = String.join("; ", inputValidation.errors());
            log.error("Validazione input fallita: {}", errorMsg);
            throw new ValidationException("Input non valido", inputValidation);
        }
        if (!inputValidation.warnings().isEmpty()) {
            log.warn("Avvisi validazione input: {}", inputValidation.warnings());
        }

        // Sanitizzazione input (neutralizza tag XML che potrebbero interferire col Modelfile)
        String sanitizedPayload = inputValidator.sanitize(request.payload());

        // 2. Determinazione o recupero dell'etichetta del documento
        DocumentType type = request.type();
        String routerUsed;
        if (type == null) {
            Router activeRouter = (request.routerType() != null)
                    ? routerRegistry.resolve(request.routerType())
                    : routerRegistry.getDefaultRouter();

            if (activeRouter == null) {
                log.error("Nessun router configurato o disponibile per l'instradamento.");
                throw new RoutingException("Nessun router configurato o disponibile nel registry.");
            }

            log.info("Tipo documento non specificato: interrogazione del Router (tipo richiesto: '{}', effettivo: '{}')...",
                    request.routerType(), activeRouter.getClass().getSimpleName());
            try {
                RoutingDecision decision = activeRouter.routeWithDecision(sanitizedPayload);
                type = decision.documentType();
                routerUsed = decision.routerName();
            } catch (Exception e) {
                log.error("Errore durante l'invocazione del Router: {}", e.getMessage(), e);
                throw new RoutingException("Impossibile determinare il tipo di documento tramite il Router", e);
            }
        } else {
            routerUsed = "CLIENT_SPECIFIED";
        }

        // 3. Validazione dell'etichetta rispetto alle pipeline registrate
        ValidationResult labelValidation = labelValidator.validate(type);
        if (!labelValidation.isValid()) {
            String errorMsg = String.join("; ", labelValidation.errors());
            log.error("Validazione etichetta fallita: {}", errorMsg);
            throw new ValidationException("Etichetta documento non supportata: " + type, labelValidation);
        }

        final DocumentType effectiveType = type;
        log.info("Orchestratore: instradamento verso pipeline '{}' tramite router '{}' (payload sanificato: {} caratteri)",
                effectiveType, routerUsed, sanitizedPayload.length());

        // 4. Selezione ed esecuzione della Pipeline dedicata
        Pipeline pipeline = pipelineRegistry.get(effectiveType)
                .orElseThrow(() -> new PipelineExecutionException("Nessuna pipeline registrata per il tipo: " + effectiveType));

        PipelineContext context = new PipelineContext(request.payload(), effectiveType);
        context.setSanitizedInput(sanitizedPayload);

        PipelineResult result;
        try {
            result = pipeline.execute(context);
        } catch (Exception e) {
            long totalElapsed = System.currentTimeMillis() - startTime;
            log.error("Eccezione imprevista durante l'esecuzione della pipeline '{}': {}", effectiveType, e.getMessage(), e);
            FieldError sysErr = new FieldError("$", "system", ValidationErrorCode.INFERENCE_ERROR, "Errore imprevisto esecuzione pipeline: " + e.getMessage());
            return OrchestratorResponse.failure(effectiveType, routerUsed, List.of(sysErr), totalElapsed);
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        ActionsGroupDefinition actionsGroup = null;
        if (pipeline instanceof BasePipeline bp) {
            actionsGroup = bp.getActionsGroup();
        }

        if (!result.success()) {
            List<FieldError> fieldErrors = result.validationResult() != null && result.validationResult().fieldErrors() != null
                    ? result.validationResult().fieldErrors()
                    : List.of(new FieldError("$", "system", ValidationErrorCode.INFERENCE_ERROR, "Elaborazione pipeline non determinata"));

            log.warn("Esecuzione pipeline '{}' non andata a buon fine (status INDETERMINATE): {} errori", effectiveType, fieldErrors.size());

            List<String> actionErrors = actionExecutor.executeActions(
                    actionsGroup,
                    OrchestratorStatus.INDETERMINATE,
                    null,
                    result.unvalidatedOutput(),
                    fieldErrors
            );

            return OrchestratorResponse.indeterminate(effectiveType, routerUsed, result.unvalidatedOutput(), fieldErrors, actionErrors, totalElapsed, result.metrics());
        }

        if (!result.validationResult().warnings().isEmpty()) {
            log.warn("Avvisi completamento pipeline '{}': {}", effectiveType, result.validationResult().warnings());
        }

        log.info("Orchestratore: completato con successo (status OK) per '{}' in {} ms (pipeline: {} ms)",
                effectiveType, totalElapsed, result.executionTimeMs());

        List<String> actionErrors = actionExecutor.executeActions(
                actionsGroup,
                OrchestratorStatus.OK,
                result.output(),
                null,
                List.of()
        );

        return OrchestratorResponse.ok(effectiveType, routerUsed, result.output(), actionErrors, totalElapsed, result.metrics());
    }
}
