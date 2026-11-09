package com.tirocinioaltrama.slmorchestrator.core.email;

/**
 * Errore infrastrutturale durante l'invio di un messaggio email.
 */
public class EmailSendingException extends RuntimeException {

    public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
