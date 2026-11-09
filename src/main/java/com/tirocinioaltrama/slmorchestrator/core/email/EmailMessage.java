package com.tirocinioaltrama.slmorchestrator.core.email;

/**
 * Messaggio email indipendente dal trasporto utilizzato.
 */
public record EmailMessage(
        String from,
        String to,
        String subject,
        String text
) {
}
