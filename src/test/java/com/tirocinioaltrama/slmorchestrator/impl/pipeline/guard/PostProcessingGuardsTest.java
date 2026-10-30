package com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard.PostProcessingGuards;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Test di non-regressione per la guardia deterministica G1 (PostProcessingGuards)
 * e verifica della sicurezza anti-svuotamento su denominazioni con parole riservate nel nome.
 */
class PostProcessingGuardsTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Test
    @DisplayName("G1: Stripping prefisso composito preservando 'Fornitore' nel nome della ragione sociale")
    void testG1SpettabileSocietaFornitriceFornitoreFittizio() throws Exception {
        String input = "Spett.le Società Fornitrice: Fornitore Fittizio S.r.l.";
        String cleaned = PostProcessingGuards.cleanLabelAndHonorifics(input);
        assertEquals("Fornitore Fittizio S.r.l.", cleaned);

        String json = """
                {
                    "fornitore": {
                        "denominazione": "Spett.le Società Fornitrice: Fornitore Fittizio S.r.l."
                    }
                }
                """;
        ObjectNode root = (ObjectNode) mapper.readTree(json);
        boolean modified = PostProcessingGuards.guardG1StripLabels(root);

        assertTrue(modified);
        assertNotNull(root.get("fornitore").get("denominazione"));
        assertFalse(root.get("fornitore").get("denominazione").isNull());
        assertEquals("Fornitore Fittizio S.r.l.", root.get("fornitore").get("denominazione").asString());
    }

    @Test
    @DisplayName("G1: Fallback anti-svuotamento su prefisso isolato senza ragione sociale")
    void testG1FallbackAntiSvuotamento() throws Exception {
        String barePrefix = "Società Fornitrice: ";
        String cleaned = PostProcessingGuards.cleanLabelAndHonorifics(barePrefix);
        assertEquals("Società Fornitrice:", cleaned);

        String json = """
                {
                    "fornitore": {
                        "denominazione": "Società Fornitrice: "
                    }
                }
                """;
        ObjectNode root = (ObjectNode) mapper.readTree(json);
        PostProcessingGuards.guardG1StripLabels(root);

        // La guardia non deve mai svuotare a null o cancellare il valore
        assertNotNull(root.get("fornitore").get("denominazione"));
        assertFalse(root.get("fornitore").get("denominazione").isNull());
        assertEquals("Società Fornitrice:", root.get("fornitore").get("denominazione").asString().strip());
    }

    @Test
    @DisplayName("G1: Preservazione denominazione legittima contenente la parola 'Fornitore' o 'Società'")
    void testG1PreservazioneFornitoreNelNome() throws Exception {
        String input1 = "Fornitore Fittizio S.r.l.";
        String cleaned1 = PostProcessingGuards.cleanLabelAndHonorifics(input1);
        assertEquals("Fornitore Fittizio S.r.l.", cleaned1);

        String input2 = "Società Elettrica S.p.A.";
        String cleaned2 = PostProcessingGuards.cleanLabelAndHonorifics(input2);
        assertEquals("Società Elettrica S.p.A.", cleaned2);
    }

    @Test
    @DisplayName("G2: Loop degenerativo ripetuto su enteRilascio (caso ANA_19) azzerato a null")
    void testG2LoopDegenerativoRipetutoAnnullato() throws Exception {
        // Simula il loop degenerativo infinito osservato in ANA_19
        String loopEnte = "Comune di Verona - Tutti i Mondi - Tutti i Mondi - Tutti i Mondi - Tutti i Mondi - Tutti i Mondi";
        String payload = "Repubblica Italiana - Carta d'Identità emessa dal Comune di Verona per Mario Rossi";

        ObjectNode root = mapper.createObjectNode();
        root.put("enteRilascio", loopEnte);

        boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);
        assertTrue(modified);
        assertTrue(root.get("enteRilascio").isNull());

        // Anche se sotto 80 caratteri, se i token ripetuti non compaiono nel payload la copertura scende sotto il 60%
        String loopEnteBreve = "Comune di Verona Mondi Mondi Mondi";
        ObjectNode rootBreve = mapper.createObjectNode();
        rootBreve.put("enteRilascio", loopEnteBreve);

        boolean modBreve = PostProcessingGuards.guardG2EnteRilascio(rootBreve, payload);
        assertTrue(modBreve);
        assertTrue(rootBreve.get("enteRilascio").isNull());
    }

    @Test
    @DisplayName("G2: Ente di rilascio legittimo con copertura token >= 60% preservato")
    void testG2EnteRilascioLegittimoPreservato() throws Exception {
        String payload = "Documento rilasciato da: Comune di Milano in data 10/01/2020";
        ObjectNode root = mapper.createObjectNode();
        root.put("enteRilascio", "Comune di Milano");

        boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);
        assertFalse(modified);
        assertEquals("Comune di Milano", root.get("enteRilascio").asString());
    }

    @Test
    @DisplayName("G2: Allucinazione ente totalmente assente dal payload annullata a null")
    void testG2EnteAllucinatoAnnullato() throws Exception {
        String payload = "Documento di identità emesso per Laura Bianchi nata a Roma";
        ObjectNode root = mapper.createObjectNode();
        root.put("enteRilascio", "Questura di Napoli");

        boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);
        assertTrue(modified);
        assertTrue(root.get("enteRilascio").isNull());
    }

    @Test
    @DisplayName("Date: Stringhe discorsive o non numeriche sui campi data azzerate a null")
    void testGuardDateFieldsNonNumeriche() throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("dataScadenza", "tempo indeterminato");
        root.put("dataFattura", "non specificato");
        root.put("dataEvento", "n/a");
        root.put("dataNascita", "12/03/1985");
        root.put("dataRilascio", "2022-05-10");

        boolean modified = PostProcessingGuards.guardDateFields(root);
        assertTrue(modified);
        assertTrue(root.get("dataScadenza").isNull());
        assertTrue(root.get("dataFattura").isNull());
        assertTrue(root.get("dataEvento").isNull());
        assertEquals("12/03/1985", root.get("dataNascita").asString());
        assertEquals("2022-05-10", root.get("dataRilascio").asString());
    }
}
