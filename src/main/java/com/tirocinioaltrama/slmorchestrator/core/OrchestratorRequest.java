package com.tirocinioaltrama.slmorchestrator.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Richiesta in ingresso all'orchestratore.
 * Se l'etichetta (type) non è specificata, viene determinata automaticamente
 * dal Router tramite analisi semantica del payload.
 *
 * @param type                   tipologia del documento (opzionale, dedotto dal Router se nullo)
 * @param payload                testo non strutturato fornito dall'utente
 * @param routerType             tipo di router da utilizzare (opzionale: 'local-ollama')
 * @param allowHeuristicFallback se specificato, abilita/disabilita il fallback euristico a parole chiave per questa richiesta
 */
public record OrchestratorRequest(
        DocumentType type,
        String payload,
        RouterType routerType,
        Boolean allowHeuristicFallback
) {

    @JsonCreator
    public OrchestratorRequest(
            @JsonProperty("type") DocumentType type,
            @JsonProperty("payload") String payload,
            @JsonProperty("routerType") RouterType routerType,
            @JsonProperty("allowHeuristicFallback") Boolean allowHeuristicFallback
    ) {
        this.type = type;
        this.payload = payload;
        this.routerType = routerType;
        this.allowHeuristicFallback = allowHeuristicFallback;
    }

    public OrchestratorRequest(DocumentType type, String payload, RouterType routerType) {
        this(type, payload, routerType, null);
    }

    public OrchestratorRequest(DocumentType type, String payload) {
        this(type, payload, null, null);
    }

    public OrchestratorRequest(String payload) {
        this(null, payload, null, null);
    }

    public OrchestratorRequest(String payload, RouterType routerType) {
        this(null, payload, routerType, null);
    }
}
