package com.tirocinioaltrama.slmorchestrator.core;

import java.util.List;

import com.tirocinioaltrama.slmorchestrator.core.model.ExecutionMetrics;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;

/**
 * Risposta restituita dall'orchestratore.
 *
 * @param status            esito formale dell'elaborazione (OK o INDETERMINATE)
 * @param label             etichetta del documento assegnata o richiesta
 * @param router            nome del router utilizzato per la classificazione (o 'CLIENT_SPECIFIED' se fornito dal chiamante)
 * @param result            output strutturato e validato prodotto dall'elaborazione (tipicamente JSON) oppure null se INDETERMINATE
 * @param unvalidatedResult output grezzo dell'ultimo tentativo (disponibile quando lo stato è INDETERMINATE per revisione operatore)
 * @param errors            errori strutturati emersi durante l'esecuzione della pipeline [{path, code, message}]
 * @param actionErrors      eventuali errori nell'esecuzione di azioni post-pipeline (RF8)
 * @param latencyMs         tempo totale impiegato dall'elaborazione in millisecondi
 */
public record OrchestratorResponse(
        OrchestratorStatus status,
        DocumentType label,
        String router,
        String result,
        String unvalidatedResult,
        List<FieldError> errors,
        List<String> actionErrors,
        long latencyMs,
        ExecutionMetrics metrics
) {

    public OrchestratorResponse(
            OrchestratorStatus status,
            DocumentType label,
            String router,
            String result,
            String unvalidatedResult,
            List<FieldError> errors,
            List<String> actionErrors,
            long latencyMs
    ) {
        this(status, label, router, result, unvalidatedResult, errors, actionErrors, latencyMs, null);
    }

    public OrchestratorResponse(DocumentType label, String router, String result, long latencyMs) {
        this(OrchestratorStatus.OK, label, router, result, null, List.of(), List.of(), latencyMs, null);
    }

    public OrchestratorResponse(DocumentType label, String result, long latencyMs) {
        this(OrchestratorStatus.OK, label, null, result, null, List.of(), List.of(), latencyMs, null);
    }

    public static OrchestratorResponse ok(DocumentType label, String router, String result, List<String> actionErrors, long latencyMs) {
        return ok(label, router, result, actionErrors, latencyMs, null);
    }

    public static OrchestratorResponse ok(DocumentType label, String router, String result, List<String> actionErrors, long latencyMs, ExecutionMetrics metrics) {
        return new OrchestratorResponse(
                OrchestratorStatus.OK,
                label,
                router,
                result,
                null,
                List.of(),
                actionErrors != null ? actionErrors : List.of(),
                latencyMs,
                metrics
        );
    }

    public static OrchestratorResponse ok(DocumentType label, String router, String result, long latencyMs) {
        return ok(label, router, result, List.of(), latencyMs, null);
    }

    public static OrchestratorResponse indeterminate(
            DocumentType label,
            String router,
            String unvalidatedResult,
            List<FieldError> errors,
            List<String> actionErrors,
            long latencyMs
    ) {
        return indeterminate(label, router, unvalidatedResult, errors, actionErrors, latencyMs, null);
    }

    public static OrchestratorResponse indeterminate(
            DocumentType label,
            String router,
            String unvalidatedResult,
            List<FieldError> errors,
            List<String> actionErrors,
            long latencyMs,
            ExecutionMetrics metrics
    ) {
        return new OrchestratorResponse(
                OrchestratorStatus.INDETERMINATE,
                label,
                router,
                null,
                unvalidatedResult,
                errors != null ? errors : List.of(),
                actionErrors != null ? actionErrors : List.of(),
                latencyMs,
                metrics
        );
    }

    public static OrchestratorResponse indeterminate(
            DocumentType label,
            String router,
            String unvalidatedResult,
            List<FieldError> errors,
            long latencyMs
    ) {
        return indeterminate(label, router, unvalidatedResult, errors, List.of(), latencyMs, null);
    }

    public static OrchestratorResponse failure(DocumentType label, String router, List<FieldError> errors, long latencyMs) {
        return indeterminate(label, router, null, errors, List.of(), latencyMs, null);
    }
}
