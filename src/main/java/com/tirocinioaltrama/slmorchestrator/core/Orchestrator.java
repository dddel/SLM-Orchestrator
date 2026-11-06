package com.tirocinioaltrama.slmorchestrator.core;

/**
 * Contratto per l'orchestratore di pipeline SLM/LLM.
 * Riceve la richiesta con il payload grezzo, determina internamente la pipeline
 * e il modello da eseguire e coordina i worker locali in modo opaco per il chiamante.
 */
public interface Orchestrator {

    OrchestratorResponse process(OrchestratorRequest request);
}
