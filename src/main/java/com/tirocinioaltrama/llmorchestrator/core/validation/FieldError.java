package com.tirocinioaltrama.llmorchestrator.core.validation;

/**
 * Errore di validazione strutturato relativo a un campo specifico o all'output complessivo.
 *
 * @param path          percorso del campo (es. "fornitore.partitaIva") oppure "$" per l'intero documento/JSON
 * @param validatorName nome del validatore logico ("partita_iva", "iban", "codice_fiscale", "email", "json", "required", "system")
 * @param code          codice univoco di errore (es. {@link ValidationErrorCode})
 * @param message       messaggio descrittivo human-readable
 */
public record FieldError(
        String path,
        String validatorName,
        ValidationErrorCode code,
        String message
) {
}
