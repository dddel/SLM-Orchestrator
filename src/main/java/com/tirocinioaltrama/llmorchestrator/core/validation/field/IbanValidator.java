package com.tirocinioaltrama.llmorchestrator.core.validation.field;

import java.math.BigInteger;
import java.util.regex.Pattern;

import com.tirocinioaltrama.llmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.core.validation.Validator;

/**
 * Validatore deterministico per codici IBAN internazionali (MOD-97 / ISO 13616 / ISO 7064).
 * - Verifica formato base e lunghezza standard per paese (es. Italia 27 caratteri, Germania 22, Francia 27).
 * - Per l'Italia verifica anche la struttura specifica: IT + 2 cifre + 1 lettera CIN + 5 cifre ABI + 5 cifre CAB + 12 caratteri conto.
 * - Calcola il MOD-97 tramite algoritmo lineare di Horner a zero allocazioni heap (senza BigInteger).
 */
public class IbanValidator implements Validator<String> {

    private static final Pattern IBAN_FORMAT_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    // Regola specifica per IBAN italiano (27 caratteri): IT + 2 cifre + 1 lettera (CIN) + 10 cifre (ABI+CAB) + 12 alfanumerici (conto)
    private static final Pattern IBAN_ITALIAN_PATTERN = Pattern.compile("^IT[0-9]{2}[A-Z][0-9]{10}[A-Z0-9]{12}$");

    // Mappa lunghezze IBAN per i principali paesi europei/internazionali
    private static final java.util.Map<String, Integer> COUNTRY_IBAN_LENGTHS = java.util.Map.ofEntries(
            java.util.Map.entry("IT", 27),
            java.util.Map.entry("SM", 27),
            java.util.Map.entry("DE", 22),
            java.util.Map.entry("FR", 27),
            java.util.Map.entry("ES", 24),
            java.util.Map.entry("GB", 22),
            java.util.Map.entry("CH", 21),
            java.util.Map.entry("AT", 20),
            java.util.Map.entry("BE", 16),
            java.util.Map.entry("NL", 18),
            java.util.Map.entry("PT", 25)
    );

    @Override
    public ValidationResult validate(String iban) {
        if (iban == null || iban.isBlank()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "iban", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.VALUE_EMPTY, "Il codice IBAN non può essere nullo o vuoto.")),
                    java.util.List.of());
        }

        String cleaned = iban.replaceAll("\\s+", "").toUpperCase();
        if (!IBAN_FORMAT_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "iban", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "Formato IBAN non valido: '" + iban + "'")),
                    java.util.List.of());
        }

        String countryCode = cleaned.substring(0, 2);
        Integer expectedLength = COUNTRY_IBAN_LENGTHS.get(countryCode);
        if (expectedLength != null && cleaned.length() != expectedLength) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "iban", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.LENGTH_INVALID, "Lunghezza IBAN per il paese " + countryCode + " non valida: attesi " + expectedLength + " caratteri, trovati " + cleaned.length() + ".")),
                    java.util.List.of());
        }

        // Se italiano, verifica anche la struttura BBAN specifica
        if ("IT".equals(countryCode) && !IBAN_ITALIAN_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "iban", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "Struttura IBAN italiano non conforme (atteso IT + 2 cifre + CIN alfabetico + ABI/CAB numerici): '" + iban + "'")),
                    java.util.List.of());
        }

        // Calcolo MOD-97 ISO 7064 senza BigInteger (spostamento dei primi 4 caratteri alla fine)
        if (!validateMod97(cleaned)) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "iban", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.CHECKSUM_INVALID, "Checksum IBAN (MOD-97) non valido per '" + iban + "'")),
                    java.util.List.of());
        }

        return ValidationResult.valid();
    }

    private boolean validateMod97(String cleaned) {
        String rearranged = cleaned.substring(4) + cleaned.substring(0, 4);
        int remainder = 0;
        for (int i = 0; i < rearranged.length(); i++) {
            char ch = rearranged.charAt(i);
            if (ch >= '0' && ch <= '9') {
                remainder = (remainder * 10 + (ch - '0')) % 97;
            } else if (ch >= 'A' && ch <= 'Z') {
                int value = ch - 'A' + 10;
                remainder = (remainder * 100 + value) % 97;
            } else {
                return false;
            }
        }
        return remainder == 1;
    }
}
