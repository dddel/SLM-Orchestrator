package com.tirocinioaltrama.slmorchestrator.core.tool;

import java.util.Map;

/**
 * Contratto generico per un Tool invocabile dal layer actions post-pipeline.
 */
public interface Tool {

    /**
     * Identificativo univoco del tool (es. "save_to_db", "contact_operator").
     */
    String getName();

    /**
     * Esegue l'azione con i parametri forniti.
     *
     * @param args mappa dei parametri risolti (placeholder o valori costanti)
     * @return esito dell'esecuzione
     */
    ToolResult execute(Map<String, Object> args);
}
