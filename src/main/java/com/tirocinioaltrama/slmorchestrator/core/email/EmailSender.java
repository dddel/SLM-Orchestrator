package com.tirocinioaltrama.slmorchestrator.core.email;

/**
 * Porta applicativa per l'invio di messaggi email.
 */
@FunctionalInterface
public interface EmailSender {

    void send(EmailMessage message);
}
