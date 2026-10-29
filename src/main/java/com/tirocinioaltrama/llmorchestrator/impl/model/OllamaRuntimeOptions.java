package com.tirocinioaltrama.llmorchestrator.impl.model;

/**
 * Parametri runtime applicati a ogni richiesta verso il runner Ollama del modello base unificato.
 *
 * @param keepAlive     durata di permanenza del modello in RAM (es. "60m", "-1"); null = default Ollama
 * @param numCtx        finestra di contesto (num_ctx); null = non inviato
 * @param repeatPenalty penalità di ripetizione; null = 1.0
 * @param numThread     numero di thread CPU; null = scelta automatica di Ollama
 * @param numPredict    limite massimo di token da generare (num_predict); null = default (400)
 * @param temperature   temperatura di campionamento (default: 0.0)
 * @param seed          seed deterministico per la generazione (default: 42)
 */
public record OllamaRuntimeOptions(
        String keepAlive,
        Integer numCtx,
        Double repeatPenalty,
        Integer numThread,
        Integer numPredict,
        Double temperature,
        Integer seed
) {

    public static final double DEFAULT_REPEAT_PENALTY = 1.0;
    public static final int DEFAULT_NUM_PREDICT = 400;
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final int DEFAULT_SEED = 42;

    public OllamaRuntimeOptions {
        if (keepAlive != null && keepAlive.isBlank()) {
            keepAlive = null;
        }
        if (numCtx != null && numCtx <= 0) {
            numCtx = null;
        }
        if (repeatPenalty == null) {
            repeatPenalty = DEFAULT_REPEAT_PENALTY;
        }
        if (numThread != null && numThread <= 0) {
            numThread = null;
        }
        if (numPredict == null || numPredict <= 0) {
            numPredict = DEFAULT_NUM_PREDICT;
        }
        if (temperature == null) {
            temperature = DEFAULT_TEMPERATURE;
        }
        if (seed == null) {
            seed = DEFAULT_SEED;
        }
    }

    /** Costruttore a 5 parametri retrocompatibile. */
    public OllamaRuntimeOptions(String keepAlive, Integer numCtx, Double repeatPenalty, Integer numThread, Integer numPredict) {
        this(keepAlive, numCtx, repeatPenalty, numThread, numPredict, DEFAULT_TEMPERATURE, DEFAULT_SEED);
    }

    /** Costruttore di compatibilità a 4 parametri (applica numPredict = 400 di default). */
    public OllamaRuntimeOptions(String keepAlive, Integer numCtx, Double repeatPenalty, Integer numThread) {
        this(keepAlive, numCtx, repeatPenalty, numThread, DEFAULT_NUM_PREDICT, DEFAULT_TEMPERATURE, DEFAULT_SEED);
    }

    /** Opzioni di default retrocompatibili (repeat_penalty = 1.0, num_predict = 400, temp = 0.0, seed = 42). */
    public static OllamaRuntimeOptions defaults() {
        return new OllamaRuntimeOptions(null, null, DEFAULT_REPEAT_PENALTY, null, DEFAULT_NUM_PREDICT, DEFAULT_TEMPERATURE, DEFAULT_SEED);
    }
}
