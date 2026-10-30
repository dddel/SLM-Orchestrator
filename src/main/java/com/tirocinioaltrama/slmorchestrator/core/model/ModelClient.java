package com.tirocinioaltrama.slmorchestrator.core.model;

/**
 * Confine verso un modello linguistico.
 *
 * Ogni implementazione traduce la {@link ModelRequest} nel formato del proprio
 * provider e restituisce il risultato come {@link ModelResponse}.
 */
public interface ModelClient {

    ModelResponse generate(ModelRequest request);
}