package com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard.AnagraficaCoherenceValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard.PostProcessingGuards;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Test di unità per la rimozione dei prefissi numerici di layout (PostProcessingGuards e AnagraficaCoherenceValidator).
 * Verifica che il pattern ^[0-9]+[a-z]?\\.(?!\\d)\\s* rimuova i prefissi di layout senza alterare
 * date o numeri con punto separatore.
 */
class NumberingPrefixesPatternTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "[{index}] stripNumberingPrefix(\"{0}\") -> \"{1}\"")
    @CsvSource(value = {
            "1. ROSSI|ROSSI",
            "2. MARIO|MARIO",
            "4a. 12/05/2019|12/05/2019",
            "3b.Verdi|Verdi",
            "12.03.1985|12.03.1985",
            "01.02.2020|01.02.2020",
            "2022.12.12|2022.12.12",
            "1.234|1.234",
            "ROSSI|ROSSI"
    }, delimiter = '|')
    @DisplayName("AnagraficaCoherenceValidator.stripNumberingPrefix: casi parametrici")
    void testAnagraficaCoherenceValidatorStripNumberingPrefix(String input, String expected) {
        assertEquals(expected, AnagraficaCoherenceValidator.stripNumberingPrefix(input));
    }

    @Test
    @DisplayName("AnagraficaCoherenceValidator.stripNumberingPrefix: stringa vuota e null")
    void testAnagraficaCoherenceValidatorNullAndEmpty() {
        assertNull(AnagraficaCoherenceValidator.stripNumberingPrefix(null));
        assertEquals("", AnagraficaCoherenceValidator.stripNumberingPrefix(""));
    }

    @ParameterizedTest(name = "[{index}] guardStripNumberingPrefixes(\"{0}\") -> \"{1}\"")
    @CsvSource(value = {
            "1. ROSSI|ROSSI",
            "2. MARIO|MARIO",
            "4a. 12/05/2019|12/05/2019",
            "3b.Verdi|Verdi",
            "12.03.1985|12.03.1985",
            "01.02.2020|01.02.2020",
            "2022.12.12|2022.12.12",
            "1.234|1.234",
            "ROSSI|ROSSI"
    }, delimiter = '|')
    @DisplayName("PostProcessingGuards.guardStripNumberingPrefixes: casi parametrici su campi anagrafici")
    void testPostProcessingGuardsStripNumberingPrefixes(String input, String expected) {
        ObjectNode root = mapper.createObjectNode();
        root.put("nome", input);
        root.put("cognome", input);
        root.put("dataNascita", input);
        root.put("numeroDocumento", input);

        PostProcessingGuards.guardStripNumberingPrefixes(root, DocumentType.ANAGRAFICA);

        assertEquals(expected, root.get("nome").asString());
        assertEquals(expected, root.get("cognome").asString());
        assertEquals(expected, root.get("dataNascita").asString());
        assertEquals(expected, root.get("numeroDocumento").asString());
    }

    @Test
    @DisplayName("PostProcessingGuards.guardStripNumberingPrefixes: stringa vuota e null")
    void testPostProcessingGuardsNullAndEmpty() {
        ObjectNode root = mapper.createObjectNode();
        root.putNull("nome");
        root.put("cognome", "");

        PostProcessingGuards.guardStripNumberingPrefixes(root, DocumentType.ANAGRAFICA);

        assertEquals("", root.get("cognome").asString());
        assertEquals(true, root.get("nome").isNull());
    }

    @Test
    @DisplayName("PostProcessingGuards.applyGuards: preserva dataNascita/dataRilascio/dataScadenza in formato GG.MM.AAAA")
    void testApplyGuardsPreservesDotSeparatedDates() {
        ObjectNode root = mapper.createObjectNode();
        root.put("nome", "1. MARIO");
        root.put("cognome", "2. ROSSI");
        root.put("dataNascita", "12.03.1985");
        root.put("dataRilascio", "01.02.2020");
        root.put("dataScadenza", "2022.12.12");

        String payload = "Payload test con MARIO ROSSI nato il 12.03.1985";
        PostProcessingGuards.applyGuards(root, payload, DocumentType.ANAGRAFICA);

        assertEquals("MARIO", root.get("nome").asString());
        assertEquals("ROSSI", root.get("cognome").asString());
        assertEquals("12.03.1985", root.get("dataNascita").asString(), "dataNascita non deve essere troncata da guardStripNumberingPrefixes");
        assertEquals("01.02.2020", root.get("dataRilascio").asString(), "dataRilascio non deve essere troncata da guardStripNumberingPrefixes");
        assertEquals("2022.12.12", root.get("dataScadenza").asString(), "dataScadenza non deve essere troncata da guardStripNumberingPrefixes");
    }
}
