package com.tirocinioaltrama.llmorchestrator.core;

/**
 * Interfaccia per il componente Router.
 * Analizza il payload grezzo e determina a quale tipologia di documento (etichetta)
 * appartiene, per consentire all'orchestratore di instradarlo verso il worker SLM opportuno.
 */
public interface Router {

    /**
     * Determina la tipologia di documento analizzando il payload.
     */
    default DocumentType route(String payload) {
        RoutingDecision decision = routeWithDecision(payload);
        return decision != null ? decision.documentType() : null;
    }

    /**
     * Determina la tipologia di documento fornendo anche il nome del router/strategia utilizzata.
     */
    RoutingDecision routeWithDecision(String payload);
}
