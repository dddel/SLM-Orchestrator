package com.tirocinioaltrama.slmorchestrator.core.validation.field;

import java.util.List;
import java.util.regex.Pattern;

import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.Validator;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;

/**
 * Validatore deterministico per Codice Fiscale italiano (16 caratteri alfanumerici).
 * Effettua:
 * 1. Controllo di lunghezza e formato sintattico con gestione dell'omocodia per le cifre.
 * 2. Controllo di validità del mese e del range dei giorni (1-31 per uomini, 41-71 per donne).
 * 3. Verifica del codice catastale/estero (inizia per A-M o Z).
 * 4. Calcolo e verifica del carattere di controllo finale (CIN - codice di controllo ufficiale Agenzia delle Entrate).
 */
public class CodiceFiscaleValidator implements Validator<String> {

    // Regex strutturale:
    // - 6 lettere (cognome + nome)
    // - 2 cifre/omocodia (anno)
    // - 2 caratteri giorno/omocodia:
    //   Maschi: 01-31 -> decine [0-3LMNP], con vincolo: se decina 3/P unità [01LMN]
    //   Femmine: 41-71 -> decine [4-7QRST], con vincolo: se decina 7/T unità [01LMN]
    // - 4 caratteri (codice catastale Belfiore: A-M per comuni italiani, Z per stati esteri + 3 cifre/omocodia)
    // - 1 carattere di controllo (CIN)
    private static final Pattern CF_PATTERN = Pattern.compile(
            "^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST]"
                    + "(?:[0-2LMN][0-9LMNPQRSTUV]|[3P][01LMN]|[4-6QRS][0-9LMNPQRSTUV]|[7T][01LMN])"
                    + "[A-MZ][0-9LMNPQRSTUV]{3}[A-Z]$"
    );

    // Mappatura valori per caratteri in posizione dispari (1°, 3°, 5°, ...) - indicizzazione 1-based
    private static final int[] ODD_VALUES = {
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21, // 0-9
            1, 0, 5, 7, 9, 13, 15, 17, 19, 21, // A-J
            2, 4, 18, 20, 11, 3, 6, 8, 12, 14, // K-T
            16, 10, 22, 25, 24, 23             // U-Z
    };

    // Mappatura valori per caratteri in posizione pari (2°, 4°, 6°, ...) - indicizzazione 1-based
    private static final int[] EVEN_VALUES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,       // 0-9
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9,       // A-J
            10, 11, 12, 13, 14, 15, 16, 17, 18, 19, // K-T
            20, 21, 22, 23, 24, 25              // U-Z
    };

    @Override
    public ValidationResult validate(String cf) {
        if (cf == null || cf.isBlank()) {
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("", "codice_fiscale", ValidationErrorCode.VALUE_EMPTY, "Il codice fiscale non può essere nullo o vuoto.")),
                    List.of());
        }

        String cleaned = cf.replaceAll("\\s+", "").toUpperCase();
        if (cleaned.length() != 16) {
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("", "codice_fiscale", ValidationErrorCode.LENGTH_INVALID, "Il codice fiscale deve essere lungo esattamente 16 caratteri (trovati: " + cleaned.length() + ").")),
                    List.of());
        }

        if (!CF_PATTERN.matcher(cleaned).matches()) {
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("", "codice_fiscale", ValidationErrorCode.FORMAT_INVALID, "Il formato del codice fiscale non è valido: '" + cf + "'.")),
                    List.of());
        }

        // Controllo range giorno di nascita (1-31 per uomini, 41-71 per donne)
        int tens = decodeOmocodiaDigit(cleaned.charAt(9));
        int units = decodeOmocodiaDigit(cleaned.charAt(10));
        int rawDay = tens * 10 + units;
        int day = rawDay >= 40 ? rawDay - 40 : rawDay;
        if (day < 1 || day > 31) {
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("", "codice_fiscale", ValidationErrorCode.FORMAT_INVALID, "Giorno di nascita non valido nel codice fiscale (valore rilevato: " + rawDay + ").")),
                    List.of());
        }

        char expectedCin = calculateCin(cleaned);
        char actualCin = cleaned.charAt(15);
        if (expectedCin != actualCin) {
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError("", "codice_fiscale", ValidationErrorCode.CHECKSUM_INVALID, "Carattere di controllo del codice fiscale non valido (atteso: '" + expectedCin + "', trovato: '" + actualCin + "').")),
                    List.of());
        }

        return ValidationResult.valid();
    }

    /**
     * Calcola il carattere di controllo (CIN) ufficiale sui primi 15 caratteri del codice fiscale.
     */
    public static char calculateCin(String cf16) {
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            char c = cf16.charAt(i);
            int index = charToIndex(c);
            // i=0 è il 1° carattere (posizione dispari)
            if (i % 2 == 0) {
                sum += ODD_VALUES[index];
            } else {
                sum += EVEN_VALUES[index];
            }
        }
        return (char) ('A' + (sum % 26));
    }

    private static int charToIndex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return c - 'A' + 10;
        }
        throw new IllegalArgumentException("Carattere non alfanumerico: " + c);
    }

    private static int decodeOmocodiaDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        return switch (c) {
            case 'L' -> 0;
            case 'M' -> 1;
            case 'N' -> 2;
            case 'P' -> 3;
            case 'Q' -> 4;
            case 'R' -> 5;
            case 'S' -> 6;
            case 'T' -> 7;
            case 'U' -> 8;
            case 'V' -> 9;
            default -> -1;
        };
    }
}
