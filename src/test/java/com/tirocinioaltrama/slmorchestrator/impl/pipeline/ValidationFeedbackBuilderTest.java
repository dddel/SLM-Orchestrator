package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.ValidationFeedbackBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;

class ValidationFeedbackBuilderTest {

    @Test
    @DisplayName("Verifica che nessun valore rifiutato dell'input compaia nel testo del feedback")
    void testNessunValoreInputCompareNelTestoDelFeedback() {
        String fittizioIban = "IT02L1234512345000000123456";
        String fittiziaPiva = "12345678904";

        FieldError errIban = new FieldError(
                "datiPagamento.iban",
                "iban",
                ValidationErrorCode.CHECKSUM_INVALID,
                "Codice di controllo errato per l'IBAN: " + fittizioIban
        );

        FieldError errPiva = new FieldError(
                "fornitore.partitaIva",
                "partita_iva",
                ValidationErrorCode.CHECKSUM_INVALID,
                "La partita IVA " + fittiziaPiva + " non rispetta l'algoritmo Luhn."
        );

        String feedback = ValidationFeedbackBuilder.buildFeedbackMessage(List.of(errIban, errPiva));

        // Verifica che il feedback contenga i percorsi formali e i codici/descrizioni
        assertTrue(feedback.contains("datiPagamento.iban"));
        assertTrue(feedback.contains("fornitore.partitaIva"));
        assertTrue(feedback.contains("codice di controllo (checksum) non valido"));

        // Verifica cruciale: nessun valore di input presente in FieldError.message deve trasparire
        assertFalse(feedback.contains(fittizioIban), "L'IBAN rifiutato non deve comparire nel messaggio di feedback");
        assertFalse(feedback.contains(fittiziaPiva), "La partita IVA rifiutata non deve comparire nel messaggio di feedback");
    }

    @Test
    @DisplayName("Verifica che il feedback istruisca esplicitamente a mantenere invariati i campi corretti e obbligatori in modo domain-agnostic")
    void testIstruzionePreservazioneCampiValidi() {
        FieldError errPiva = new FieldError(
                "fornitore.partitaIva",
                "partita_iva",
                ValidationErrorCode.CHECKSUM_INVALID,
                "Luhn errato"
        );

        String feedback = ValidationFeedbackBuilder.buildFeedbackMessage(List.of(errPiva));

        assertTrue(feedback.contains("Estrai nuovamente tutti i dati correggendo rigorosamente i campi indicati"),
                "Il feedback deve contenere l'istruzione di re-estrazione mirata");
        assertTrue(feedback.contains("mantenendo invariati e valorizzati tutti gli altri campi (inclusi i campi obbligatori dello schema) già estratti correttamente dal testo"),
                "Il feedback deve imporre di mantenere invariati tutti gli altri campi validi e obbligatori");
        assertTrue(feedback.contains("Emetti l'intero JSON completo e valido."),
                "Il feedback deve richiedere l'intero JSON completo");

        // Verifica assenza di hardcoding di dominio (es. campi specifici di fattura estranei al FieldError fornito)
        assertFalse(feedback.contains("fornitore.denominazione"),
                "Il template non deve contenere hardcoding di campi specifici di dominio come fornitore.denominazione");
        assertFalse(feedback.contains("numeroFattura"),
                "Il template non deve contenere hardcoding di campi specifici di dominio come numeroFattura");
    }
}
