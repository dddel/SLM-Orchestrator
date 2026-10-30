package com.tirocinioaltrama.slmorchestrator.core.model;

import java.util.Map;

/**
 * Richiesta a un modello linguistico.
 * Architettura "Single Base Model + Dynamic System Prompt": il system prompt specifico
 * della pipeline (o del router) viene iniettato a runtime come messaggio {@code role: system}.
 * Eventuali opzioni dinamiche (es. temperature variata nei retry) possono essere passate in options.
 *
 * @param model        nome del modello come lo conosce il provider (opzionale, usa default se nullo)
 * @param userPrompt   testo da elaborare (es. &lt;payload&gt;...&lt;/payload&gt;)
 * @param jsonSchema   JSON Schema, come testo, che vincola l'output; null o vuoto se assente
 * @param options      parametri di campionamento aggiuntivi (es. {"temperature": 0.2}); può essere vuoto o null
 * @param systemPrompt system prompt specifico della pipeline/router; null o vuoto se assente
 * @param think        parametro esplicito per abilitare/disabilitare la catena di pensiero (null = default del runner)
 */
public record ModelRequest(
        String model,
        String userPrompt,
        String jsonSchema,
        Map<String, Object> options,
        String systemPrompt,
        Boolean think) {

    public ModelRequest(String model, String userPrompt, String jsonSchema, Map<String, Object> options, String systemPrompt) {
        this(model, userPrompt, jsonSchema, options, systemPrompt, null);
    }

    public ModelRequest(String model, String userPrompt, String jsonSchema, Map<String, Object> options) {
        this(model, userPrompt, jsonSchema, options, null, null);
    }

    public ModelRequest(String model, String userPrompt, String jsonSchema) {
        this(model, userPrompt, jsonSchema, null, null, null);
    }
}