package com.tirocinioaltrama.llmorchestrator.core.validation.field;

import java.util.regex.Pattern;

import com.tirocinioaltrama.llmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.core.validation.Validator;

/**
 * Validatore deterministico per indirizzi email basato su RFC 5322 e vincoli DNS RFC 1035 / RFC 5321.
 * - Verifica lunghezza massima consentita (totale <= 254 caratteri, local-part <= 64).
 * - Impedisce punti all'inizio, alla fine o consecutivi nella local-part.
 * - Supporta set completo di caratteri RFC 5322 nella local-part.
 * - Impedisce etichette di dominio con trattini iniziali o finali.
 * - Supporta qualsiasi TLD alfabetico di almeno 2 caratteri (es. .technology, .photography).
 */
public class EmailFormatValidator implements Validator<String> {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?!\\.)(?!.*\\.\\.)[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+(?<!\\.)"
                    + "@(?!-)[a-zA-Z0-9-]+(?<!-)(?:\\.[a-zA-Z0-9-]+(?<!-))*\\.[a-zA-Z]{2,}$"
    );

    @Override
    public ValidationResult validate(String email) {
        if (email == null || email.isBlank()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "email", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.VALUE_EMPTY, "L'indirizzo email non può essere nullo o vuoto.")),
                    java.util.List.of());
        }

        String cleaned = email.strip();
        if (cleaned.length() > MAX_EMAIL_LENGTH) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "email", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.LENGTH_INVALID, "L'indirizzo email supera la lunghezza massima consentita di " + MAX_EMAIL_LENGTH + " caratteri.")),
                    java.util.List.of());
        }

        int atIndex = cleaned.indexOf('@');
        if (atIndex == -1 || atIndex > MAX_LOCAL_PART_LENGTH) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "email", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "La parte locale dell'indirizzo email supera la lunghezza massima consentita di " + MAX_LOCAL_PART_LENGTH + " caratteri o manca la '@'.")),
                    java.util.List.of());
        }

        if (!EMAIL_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("", "email", com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode.FORMAT_INVALID, "Formato indirizzo email non valido: '" + email + "'")),
                    java.util.List.of());
        }

        return ValidationResult.valid();
    }
}
