package com.tirocinioaltrama.slmorchestrator.core.pipeline;

import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;

/**
 * Singolo passo eseguibile all'interno di una {@link Pipeline}.
 */
public interface PipelineStep {

    /**
     * Nome identificativo dello step (es. "ModelInferenceStep", "FormatValidationStep").
     */
    String getName();

    /**
     * Esegue l'elaborazione o la validazione sul contesto fornito.
     *
     * @param context contesto condiviso
     * @return esito della validazione o esecuzione per questo step
     */
    ValidationResult execute(PipelineContext context);
}
