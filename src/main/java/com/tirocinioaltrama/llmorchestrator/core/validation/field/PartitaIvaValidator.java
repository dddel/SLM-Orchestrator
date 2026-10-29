package com.tirocinioaltrama.llmorchestrator.core.validation.field;

import java.util.regex.Pattern;

import com.tirocinioaltrama.llmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.core.validation.Validator;

/**
 * Validatore deterministico per Partita IVA italiana.
 * - Supporta sia il formato a sole 11 cifre numeriche che il formato con prefisso paese "IT" (13 caratteri).
 * - Verifica che la matricola (prime 7 cifre) e il codice ufficio (cifre 8-10) non siano interamente nulli.
 * - Valida il carattere di controllo finale tramite algoritmo Luhn a pesi alternati (posizioni pari moltiplicate per 2).
 */
public class PartitaIvaValidator implements Validator<String> {

    private static final Pattern PIVA_PATTERN = Pattern.compile("^(?:IT)?([0-9]{11})$");

    @Override
    public ValidationResult validate(String piva) {
        if (piva == null || piva.isBlank()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "partita_iva", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.VALUE_EMPTY, "La partita IVA non può essere nulla o vuota.")),
                    java.util.List.of());
        }

        String cleaned = piva.replaceAll("\\s+", "").toUpperCase();
        var matcher = PIVA_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "partita_iva", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "La partita IVA deve essere composta da 11 cifre numeriche, con eventuale prefisso 'IT' (ricevuto: '" + piva + "').")),
                    java.util.List.of());
        }

        String digits = matcher.group(1);

        // Controllo matricola: le prime 7 cifre non possono essere tutte '0'
        if (digits.startsWith("0000000")) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "partita_iva", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "La matricola della partita IVA non può essere interamente composta da zeri.")),
                    java.util.List.of());
        }

        // Controllo codice ufficio/provincia (cifre da indice 7 a 9 compreso): non può essere '000'
        String officeCode = digits.substring(7, 10);
        if ("000".equals(officeCode)) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "partita_iva", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "Il codice ufficio della partita IVA non può essere '000'.")),
                    java.util.List.of());
        }

        if (!validateChecksum(digits)) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "partita_iva", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.CHECKSUM_INVALID, "Il codice di controllo della partita IVA non è valido (checksum Luhn errato per '" + piva + "').")),
                    java.util.List.of());
        }

        return ValidationResult.valid();
    }

    private boolean validateChecksum(String digits) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            int digit = Character.digit(digits.charAt(i), 10);
            if (i % 2 == 0) {
                // Posizioni dispari (1-based: 1, 3, 5, 7, 9 -> indici 0, 2, 4, 6, 8)
                sum += digit;
            } else {
                // Posizioni pari (1-based: 2, 4, 6, 8, 10 -> indici 1, 3, 5, 7, 9)
                int doubled = digit * 2;
                sum += (doubled > 9) ? (doubled - 9) : doubled;
            }
        }
        int expectedCheckDigit = (10 - (sum % 10)) % 10;
        int actualCheckDigit = Character.digit(digits.charAt(10), 10);
        return expectedCheckDigit == actualCheckDigit;
    }
}
