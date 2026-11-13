package com.tirocinioaltrama.slmorchestrator.core;

import java.util.Locale;

/**
 * Risultato della decisione di instradamento del {@link Router}.
 *
 * @param documentType tipologia di documento identificata
 * @param routerName   nome identificativo del router/strategia utilizzata (es. 'Ollama Local (qwen2.5:3b)', 'Fallback Euristico')
 */
public record RoutingDecision(DocumentType documentType, String routerName) {

    /**
     * Restituisce true se la decisione è stata prodotta dal fallback euristico a parole chiave.
     */
    public boolean isHeuristicFallback() {
        return routerName != null && routerName.toLowerCase(Locale.ROOT).contains("fallback");
    }
}
