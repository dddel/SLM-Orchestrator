package com.tirocinioaltrama.llmorchestrator.core.validation.field;

import java.util.List;

import com.tirocinioaltrama.llmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.core.validation.Validator;

/**
 * Validatore composito per codice fiscale o partita IVA.
 * <p>
 * In Italia, il codice fiscale di una persona giuridica (società, ente) è composto
 * da 11 cifre numeriche e coincide con la partita IVA, mentre quello delle persone fisiche
 * è composto da 16 caratteri alfanumerici.
 * <p>
 * Regole di validazione:
 * <ul>
 *   <li>Se la stringa (normalizzata) è di 16 caratteri alfanumerici: delega a {@link CodiceFiscaleValidator}.</li>
 *   <li>Se la stringa è di 11 cifre numeriche: applica l'algoritmo Luhn (senza imporre vincoli su codice ufficio o matricola, trattandosi di codice fiscale societario).</li>
 *   <li>Altrimenti: restituisce errore {@link ValidationErrorCode#LENGTH_INVALID} o {@link ValidationErrorCode#FORMAT_INVALID}.</li>
 * </ul>
 */
public class CodiceFiscaleOPartitaIvaValidator implements Validator<String> {

    private final CodiceFiscaleValidator cfValidator;

    public CodiceFiscaleOPartitaIvaValidator() {
        this(new CodiceFiscaleValidator());
    }

    public CodiceFiscaleOPartitaIvaValidator(CodiceFiscaleValidator cfValidator) {
        this.cfValidator = cfValidator != null ? cfValidator : new CodiceFiscaleValidator();
    }

    @Override
    public ValidationResult validate(String input) {
        if (input == null || input.isBlank()) {
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("", "codice_fiscale_o_partita_iva", ValidationErrorCode.VALUE_EMPTY, "Il codice fiscale o partita IVA non può essere nullo o vuoto.")),
                    List.of()
            );
        }

        String cleaned = input.replaceAll("\\s+", "").toUpperCase();

        // Se 16 caratteri: delega al validatore del Codice Fiscale
        if (cleaned.length() == 16) {
            ValidationResult res = cfValidator.validate(cleaned);
            if (res.isValid()) {
                return res;
            }
            List<FieldError> mappedErrors = res.fieldErrors().stream()
                    .map(fe -> new FieldError(fe.path(), "codice_fiscale_o_partita_iva", fe.code(), fe.message()))
                    .toList();
            return ValidationResult.withFieldErrors(mappedErrors, res.warnings());
        }

        // Se 11 caratteri: verifica cifre numeriche e Luhn
        if (cleaned.length() == 11) {
            if (!cleaned.matches("^[0-9]{11}$")) {
                return ValidationResult.withFieldErrors(
                        List.of(new FieldError("", "codice_fiscale_o_partita_iva", ValidationErrorCode.FORMAT_INVALID, "Il codice fiscale a 11 caratteri deve contenere esclusivamente cifre numeriche: '" + input + "'.")),
                        List.of()
                );
            }

            if (!validateLuhnChecksum(cleaned)) {
                return ValidationResult.withFieldErrors(
                        List.of(new FieldError("", "codice_fiscale_o_partita_iva", ValidationErrorCode.CHECKSUM_INVALID, "Il codice di controllo del codice fiscale/partita IVA non è valido (checksum Luhn errato per '" + input + "').")),
                        List.of()
                );
            }

            return ValidationResult.valid();
        }

        // Lunghezza diversa da 11 e da 16 caratteri
        return ValidationResult.withFieldErrors(
                List.of(new FieldError("", "codice_fiscale_o_partita_iva", ValidationErrorCode.LENGTH_INVALID, "Il codice fiscale o partita IVA deve essere lungo 11 cifre (società) o 16 caratteri (persona fisica), trovati: " + cleaned.length() + ".")),
                List.of()
        );
    }

    private boolean validateLuhnChecksum(String digits) {
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
