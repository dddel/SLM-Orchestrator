package com.tirocinioaltrama.slmorchestrator.config.ollama;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configurazione Ollama per l'architettura "Single Base Model + Dynamic Per-Pipeline System Prompt".
 * <p>
 * {@code baseModel} è l'unico modello residente in RAM: le property per-pipeline
 * ({@code model}, {@code emailModel}, {@code invoiceModel}, {@code anagraficaModel}, {@code routerModel})
 * sono mantenute per retrocompatibilità e, se non valorizzate, ripiegano sul modello base.
 *
 * @param keepAlive     durata di permanenza in RAM del runner (es. "60m" o "-1"); null per il default di Ollama
 * @param numCtx        finestra di contesto (num_ctx), default 2048
 * @param repeatPenalty penalità di ripetizione, default 1.0
 * @param numThread     numero di thread CPU opzionale (null = scelta automatica di Ollama)
 * @param numPredict    limite massimo di token da generare (num_predict), default 400
 * @param temperature   temperatura di campionamento, default 0.0
 * @param seed          seed deterministico per la generazione, default 42
 */
@ConfigurationProperties(prefix = "ollama")
public record OllamaProperties(
        String baseUrl,
        String model,
        String emailModel,
        String invoiceModel,
        String anagraficaModel,
        Duration connectTimeout,
        Duration readTimeout,
        String baseModel,
        String routerModel,
        String routerSystemPrompt,
        String escalationModel,
        String keepAlive,
        Integer numCtx,
        Double repeatPenalty,
        Integer numThread,
        Boolean initEnabled,
        Integer numPredict,
        Double temperature,
        Integer seed,
        String reflexModel) {

    public static final String DEFAULT_BASE_MODEL = "qwen2.5:3b-instruct-q8_0";
    public static final String DEFAULT_REFLEX_MODEL = "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M";
    public static final String DEFAULT_ROUTER_SYSTEM_PROMPT = "prompts/router-system.txt";
    public static final int DEFAULT_NUM_CTX = 2048;
    public static final double DEFAULT_REPEAT_PENALTY = 1.0;
    public static final int DEFAULT_NUM_PREDICT = 400;
    public static final double DEFAULT_TEMPERATURE = 0.0;
    public static final int DEFAULT_SEED = 42;

    @ConstructorBinding
    public OllamaProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:11434";
        }
        if (baseModel == null || baseModel.isBlank()) {
            baseModel = (model != null && !model.isBlank()) ? model.strip() : DEFAULT_BASE_MODEL;
        }
        if (model == null || model.isBlank()) {
            model = baseModel;
        }
        if (emailModel == null || emailModel.isBlank()) {
            emailModel = baseModel;
        }
        if (invoiceModel == null || invoiceModel.isBlank()) {
            invoiceModel = baseModel;
        }
        if (anagraficaModel == null || anagraficaModel.isBlank()) {
            anagraficaModel = baseModel;
        }
        if (routerModel == null || routerModel.isBlank()) {
            routerModel = baseModel;
        }
        if (routerSystemPrompt == null || routerSystemPrompt.isBlank()) {
            routerSystemPrompt = DEFAULT_ROUTER_SYSTEM_PROMPT;
        }
        if (escalationModel != null && escalationModel.isBlank()) {
            escalationModel = null;
        }
        if (keepAlive != null && keepAlive.isBlank()) {
            keepAlive = null;
        }
        if (numCtx == null || numCtx <= 0) {
            numCtx = DEFAULT_NUM_CTX;
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
        if (initEnabled == null) {
            initEnabled = true;
        }
        if (reflexModel == null || reflexModel.isBlank()) {
            reflexModel = DEFAULT_REFLEX_MODEL;
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(30);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofMinutes(4);
        }
    }

    /**
     * Costruttore a 17 parametri senza temperature e seed espliciti (default: 0.0 e 42).
     */
    public OllamaProperties(
            String baseUrl,
            String model,
            String emailModel,
            String invoiceModel,
            String anagraficaModel,
            Duration connectTimeout,
            Duration readTimeout,
            String baseModel,
            String routerModel,
            String routerSystemPrompt,
            String escalationModel,
            String keepAlive,
            Integer numCtx,
            Double repeatPenalty,
            Integer numThread,
            Boolean initEnabled,
            Integer numPredict) {
        this(baseUrl, model, emailModel, invoiceModel, anagraficaModel, connectTimeout, readTimeout,
                baseModel, routerModel, routerSystemPrompt, escalationModel, keepAlive, numCtx, repeatPenalty, numThread, initEnabled, numPredict, DEFAULT_TEMPERATURE, DEFAULT_SEED, DEFAULT_REFLEX_MODEL);
    }

    /**
     * Costruttore a 16 parametri con initEnabled esplicito ma senza numPredict, temperature e seed.
     */
    public OllamaProperties(
            String baseUrl,
            String model,
            String emailModel,
            String invoiceModel,
            String anagraficaModel,
            Duration connectTimeout,
            Duration readTimeout,
            String baseModel,
            String routerModel,
            String routerSystemPrompt,
            String escalationModel,
            String keepAlive,
            Integer numCtx,
            Double repeatPenalty,
            Integer numThread,
            Boolean initEnabled) {
        this(baseUrl, model, emailModel, invoiceModel, anagraficaModel, connectTimeout, readTimeout,
                baseModel, routerModel, routerSystemPrompt, escalationModel, keepAlive, numCtx, repeatPenalty, numThread, initEnabled, DEFAULT_NUM_PREDICT, DEFAULT_TEMPERATURE, DEFAULT_SEED, DEFAULT_REFLEX_MODEL);
    }

    /**
     * Costruttore a 15 parametri senza initEnabled né numPredict, temperature e seed espliciti.
     */
    public OllamaProperties(
            String baseUrl,
            String model,
            String emailModel,
            String invoiceModel,
            String anagraficaModel,
            Duration connectTimeout,
            Duration readTimeout,
            String baseModel,
            String routerModel,
            String routerSystemPrompt,
            String escalationModel,
            String keepAlive,
            Integer numCtx,
            Double repeatPenalty,
            Integer numThread) {
        this(baseUrl, model, emailModel, invoiceModel, anagraficaModel, connectTimeout, readTimeout,
                baseModel, routerModel, routerSystemPrompt, escalationModel, keepAlive, numCtx, repeatPenalty, numThread, true, DEFAULT_NUM_PREDICT, DEFAULT_TEMPERATURE, DEFAULT_SEED, DEFAULT_REFLEX_MODEL);
    }

    /**
     * Costruttore retrocompatibile (configurazione multi-modello precedente).
     */
    public OllamaProperties(
            String baseUrl,
            String model,
            String emailModel,
            String invoiceModel,
            String anagraficaModel,
            Duration connectTimeout,
            Duration readTimeout) {
        this(baseUrl, model, emailModel, invoiceModel, anagraficaModel, connectTimeout, readTimeout,
                null, null, null, null, null, null, null, null, true, DEFAULT_NUM_PREDICT, DEFAULT_TEMPERATURE, DEFAULT_SEED, DEFAULT_REFLEX_MODEL);
    }
}
