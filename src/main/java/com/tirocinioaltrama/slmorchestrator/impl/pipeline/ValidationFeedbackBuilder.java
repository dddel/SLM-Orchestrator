package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import java.util.List;

import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;

/**
 * Costruttore deterministico del messaggio di correzione (feedback) per il modello SLM.
 *
 * Regole di sicurezza anti-injection:
 * 1. Forma fissa da template generata esclusivamente a partire da {@link FieldError} strutturati.
 * 2. Nessun valore rifiutato viene mai reiniettato nel feedback: zero superficie di injection.
 * 3. Il messaggio indica esclusivamente il percorso del campo e la causa formale dell'errore.
 */
public final class ValidationFeedbackBuilder {

    private ValidationFeedbackBuilder() {
    }

    public static String buildFeedbackMessage(List<FieldError> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "ATTENZIONE: L'output precedente non è conforme allo schema JSON richiesto. Estrai nuovamente tutti i dati rispettando rigorosamente lo schema, mantenendo invariati e valorizzati tutti gli altri campi (inclusi i campi obbligatori dello schema) già estratti correttamente dal testo. Emetti l'intero JSON completo e valido.";
        }

        boolean hasSwap = fieldErrors.stream().anyMatch(e -> e.code() == ValidationErrorCode.FIELD_SWAP_DETECTED);
        if (hasSwap && fieldErrors.size() == 1) {
            return "ATTENZIONE: Rilevata inversione tra i campi 'nome' e 'cognome'.\n" +
                    "- Nei documenti con numerazione europea (es. Patente di guida), il punto '1.' indica rigorosamente il COGNOME e il punto '2.' indica rigorosamente il NOME.\n" +
                    "- Verifica inoltre che 'cognome' e 'nome' siano coerenti con le etichette del testo e con il Codice Fiscale se presente.\n" +
                    "Estrai nuovamente tutti i dati correggendo rigorosamente i campi indicati basandoti sul documento originale nel <payload>, mantenendo invariati e valorizzati tutti gli altri campi (inclusi i campi obbligatori dello schema) già estratti correttamente dal testo. Emetti l'intero JSON completo e valido.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ATTENZIONE: L'output precedente conteneva errori di formato, calcolo o conformità allo schema JSON:\n");

        for (FieldError err : fieldErrors) {
            sb.append("- Campo '").append(err.path() != null && !err.path().isBlank() ? err.path() : "documento").append("': ");
            sb.append(describeError(err));
            sb.append("\n");
        }

        sb.append("Estrai nuovamente tutti i dati correggendo rigorosamente i campi indicati, mantenendo invariati e valorizzati tutti gli altri campi (inclusi i campi obbligatori dello schema) già estratti correttamente dal testo. Emetti l'intero JSON completo e valido.");
        return sb.toString();
    }

    private static String describeError(FieldError err) {
        return switch (err.code()) {
            case REQUIRED_MISSING -> "campo obbligatorio mancante nell'output.";
            case REQUIRED_EMPTY -> "campo obbligatorio presente ma vuoto.";
            case REQUIRED_NULL -> "campo obbligatorio valorizzato a null.";
            case JSON_SYNTAX_INVALID -> "l'output non è un JSON valido o risulta troncato.";
            case CHECKSUM_INVALID -> "codice di controllo (checksum) non valido.";
            case SOURCE_CHECKSUM_INVALID -> "codice di controllo (checksum) non valido nel dato sorgente.";
            case LENGTH_INVALID -> "lunghezza non conforme alla specifica richiesta.";
            case FORMAT_INVALID -> "formato non conforme alla specifica richiesta.";
            case VALUE_EMPTY -> "valore vuoto non consentito.";
            case VALUE_NOT_IN_CATALOG -> "valore non appartenente al catalogo o insieme di scelte consentite.";
            case FIELD_SWAP_DETECTED -> "rilevata inversione tra i campi 'nome' e 'cognome'. Nei documenti con numerazione europea (es. Patente di guida), '1.' indica il COGNOME e '2.' il NOME; verifica la coerenza con le etichette e con il Codice Fiscale.";
            case INFERENCE_ERROR -> "errore di generazione del modello SLM.";
            case INDETERMINATE -> "decisione indeterminata per consenso insufficiente tra permutazioni.";
        };
    }
}
