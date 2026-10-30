package com.tirocinioaltrama.slmorchestrator.core;

/**
 * Risultato della decisione di instradamento del {@link Router}.
 *
 * @param documentType tipologia di documento identificata
 * @param routerName   nome identificativo del router/strategia utilizzata (es. 'Ollama Local (qwen2.5:3b)', 'Fallback Euristico')
 */
public record RoutingDecision(DocumentType documentType, String routerName) {
}
