package com.tirocinioaltrama.slmorchestrator.core.model;

/**
 * Risposta di un modello linguistico.
 *
 * @param content   testo prodotto dal modello, senza interpretazioni
 * @param latencyMs tempo trascorso tra l'invio della richiesta e la risposta, in millisecondi
 * @param metrics   metriche di calcolo e conteggio token (opzionali)
 */
public record ModelResponse(String content, long latencyMs, ModelMetrics metrics) {

    public ModelResponse(String content, long latencyMs) {
        this(content, latencyMs, ModelMetrics.empty());
    }
}