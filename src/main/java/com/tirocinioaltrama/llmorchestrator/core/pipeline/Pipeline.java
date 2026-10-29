package com.tirocinioaltrama.llmorchestrator.core.pipeline;

import com.tirocinioaltrama.llmorchestrator.core.DocumentType;

/**
 * Rappresenta una pipeline di elaborazione per uno specifico {@link DocumentType}.
 */
public interface Pipeline {

    /**
     * Il tipo di documento gestito da questa pipeline.
     */
    DocumentType getSupportedType();

    /**
     * Esegue la pipeline sul contesto fornito.
     *
     * @param context contesto contenente l'input e i dati intermedi
     * @return esito complessivo dell'elaborazione e validazione
     */
    PipelineResult execute(PipelineContext context);
}
