package com.tirocinioaltrama.slmorchestrator.api;

import java.time.Instant;
import java.util.List;

/**
 * Risposta standard JSON restituita in caso di errore dalle API dell'orchestratore.
 *
 * @param timestamp data e ora dell'errore (ISO-8601 UTC)
 * @param status    codice di stato HTTP numerico (es. 400, 422, 502, 503)
 * @param error     denominazione standard dell'errore HTTP (es. "Bad Request")
 * @param message   descrizione leggibile del problema
 * @param path      URI dell'endpoint su cui si è verificato l'errore
 * @param details   dettagli specifici o lista di errori di validazione
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public ApiErrorResponse(int status, String error, String message, String path, List<String> details) {
        this(Instant.now(), status, error, message, path, details != null ? List.copyOf(details) : List.of());
    }

    public ApiErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, List.of());
    }
}
