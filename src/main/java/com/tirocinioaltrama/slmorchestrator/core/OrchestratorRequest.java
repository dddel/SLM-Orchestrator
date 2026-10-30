package com.tirocinioaltrama.slmorchestrator.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Richiesta in ingresso all'orchestratore.
 * Se l'etichetta (type) non è specificata, viene determinata automaticamente
 * dal Router tramite analisi semantica del payload.
 *
 * @param type       tipologia del documento (opzionale, dedotto dal Router se nullo)
 * @param payload    testo non strutturato fornito dall'utente
 * @param routerType tipo di router da utilizzare (opzionale: 'local-ollama')
 */
public record OrchestratorRequest(
        DocumentType type,
        String payload,
        RouterType routerType
) {

    @JsonCreator
    public OrchestratorRequest(
            @JsonProperty("type") DocumentType type,
            @JsonProperty("payload") String payload,
            @JsonProperty("routerType") RouterType routerType
    ) {
        this.type = type;
        this.payload = payload;
        this.routerType = routerType;
    }

    public OrchestratorRequest(DocumentType type, String payload) {
        this(type, payload, null);
    }

    public OrchestratorRequest(String payload) {
        this(null, payload, null);
    }

    public OrchestratorRequest(String payload, RouterType routerType) {
        this(null, payload, routerType);
    }
}
