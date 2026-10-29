package com.tirocinioaltrama.llmorchestrator.core;

/**
 * Risultato della decisione di instradamento del {@link Router}.
 *
 * @param documentType tipologia di documento identificata
 * @param routerName   nome identificativo del router/strategia utilizzata (es. 'Gemini Router', 'Fallback Euristico')
 */
public record RoutingDecision(DocumentType documentType, String routerName) {
}
