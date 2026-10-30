package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import com.tirocinioaltrama.slmorchestrator.impl.pipeline.OutputValidationStep;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.ValidationFeedbackBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard.AnagraficaCoherenceValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard.PostProcessingGuards;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Suite di test unitari per le Guardie Simboliche (G1-G4, Evidenza Sesso),
 * i Validatori di Coerenza Anagrafica (V1, V2) e il Feedback mirato RF7.
 */
class SymbolicGuardsAndCoherenceTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("G1: Normalizzazione Etichette e Onorifici su Denominazioni")
    class GuardG1Tests {

        @Test
        @DisplayName("G1: Rimozione prefisso 'Società fornitrice:' e 'Destinatario: Egr.'")
        void testG1StripEtichetteEOnorifici() throws Exception {
            String json = """
                    {
                        "fornitore": {
                            "denominazione": "Società fornitrice: Kronos Energy S.r.l."
                        },
                        "cliente": {
                            "denominazione": "Destinatario: Egr. Studio Associato Rossi"
                        }
                    }
                    """;
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            boolean modified = PostProcessingGuards.guardG1StripLabels(root);

            assertTrue(modified);
            assertEquals("Kronos Energy S.r.l.", root.get("fornitore").get("denominazione").asString());
            assertEquals("Studio Associato Rossi", root.get("cliente").get("denominazione").asString());
        }

        @Test
        @DisplayName("G1: Rimozione prefisso 'Fornitore: Spett.le' e formule di cortesia multiple")
        void testG1FornitoreSpettabile() throws Exception {
            String json = """
                    {
                        "fornitore": {
                            "denominazione": "Fornitore: Spett.le Dott. Mario Rossi Consulting S.r.l."
                        }
                    }
                    """;
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            boolean modified = PostProcessingGuards.guardG1StripLabels(root);

            assertTrue(modified);
            assertEquals("Mario Rossi Consulting S.r.l.", root.get("fornitore").get("denominazione").asString());
        }

        @Test
        @DisplayName("G1: Preservazione denominazione legittima senza separatore (es. 'Fornitore Aziendale 1 S.r.l.')")
        void testG1PreservazioneNomeLegittimo() throws Exception {
            String json = """
                    {
                        "fornitore": {
                            "denominazione": "Fornitore Aziendale 1 S.r.l."
                        },
                        "cliente": {
                            "denominazione": "Cliente Sicuro S.p.A."
                        }
                    }
                    """;
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            boolean modified = PostProcessingGuards.guardG1StripLabels(root);

            assertFalse(modified);
            assertEquals("Fornitore Aziendale 1 S.r.l.", root.get("fornitore").get("denominazione").asString());
            assertEquals("Cliente Sicuro S.p.A.", root.get("cliente").get("denominazione").asString());
        }

        @Test
        @DisplayName("G1: Rimozione prefisso 'Professionista: Architetto' e etichetta 'Committente:'")
        void testG1ProfessionistaArchitettoECommittente() throws Exception {
            String json = """
                    {
                        "fornitore": {
                            "denominazione": "Professionista: Architetto Marco Rossi"
                        },
                        "cliente": {
                            "denominazione": "Committente: Alfa Costruzioni S.p.A."
                        }
                    }
                    """;
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            boolean modified = PostProcessingGuards.guardG1StripLabels(root);

            assertTrue(modified);
            assertEquals("Marco Rossi", root.get("fornitore").get("denominazione").asString());
            assertEquals("Alfa Costruzioni S.p.A.", root.get("cliente").get("denominazione").asString());
        }

        @Test
        @DisplayName("G1: Rimozione quando l'onorifico precede l'etichetta descrittiva (es. 'Spett.le Committente: Impianti Solari S.p.A.')")
        void testG1OnorificoPrecedeEtichettaDescrittiva() throws Exception {
            String json = """
                    {
                        "cliente": {
                            "denominazione": "Spett.le Committente: Impianti Solari S.p.A."
                        }
                    }
                    """;
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            boolean modified = PostProcessingGuards.guardG1StripLabels(root);

            assertTrue(modified);
            assertEquals("Impianti Solari S.p.A.", root.get("cliente").get("denominazione").asString());
        }

        @Test
        @DisplayName("G1: Stripping prefisso composito preservando 'Fornitore' nel nome della ragione sociale (Scenario B1)")
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
    }

    @Nested
    @DisplayName("G2: Guardia di Evidenza Lessicale e Pattern Data su enteRilascio")
    class GuardG2Tests {

        @Test
        @DisplayName("G2: Annullamento a null se enteRilascio contiene un pattern data")
        void testG2AnnullaDataIncompatibile() throws Exception {
            String json = "{\"enteRilascio\": \"05/05/2020\"}";
            String payload = "PATENTE DI GUIDA 4a. 05/05/2020 4b. 05/05/2030";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);

            assertTrue(modified);
            assertTrue(root.get("enteRilascio").isNull());
        }

        @Test
        @DisplayName("G2: Annullamento allucinazione 'Ministero' in assenza di evidenza lessicale nel payload")
        void testG2AnnullaAllucinazioneMinistero() throws Exception {
            String json = "{\"enteRilascio\": \"Ministero\"}";
            String payload = "CARTA DI IDENTITA CA12345XY Emissione: 01/02/2022 Scadenza: 15/07/2032";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);

            assertTrue(modified);
            assertTrue(root.get("enteRilascio").isNull());
        }

        @Test
        @DisplayName("G2: Annullamento valori placeholder come 'non specificato' e 'tempo indeterminato'")
        void testG2AnnullaPlaceholderNonSpecificatoETempoIndeterminato() throws Exception {
            String json1 = "{\"enteRilascio\": \"non specificato\"}";
            ObjectNode root1 = (ObjectNode) mapper.readTree(json1);
            assertTrue(PostProcessingGuards.guardG2EnteRilascio(root1, "Documento senza ente"));
            assertTrue(root1.get("enteRilascio").isNull());

            String json2 = "{\"enteRilascio\": \"tempo indeterminato\"}";
            ObjectNode root2 = (ObjectNode) mapper.readTree(json2);
            assertTrue(PostProcessingGuards.guardG2EnteRilascio(root2, "Documento"));
            assertTrue(root2.get("enteRilascio").isNull());
        }

        @Test
        @DisplayName("G2: Ignora intestazione statica 'MINISTERO DELL'INTERNO' in assenza di rilascio esplicito")
        void testG2IgnoraHeaderMinisterialeStatico() throws Exception {
            String json = "{\"enteRilascio\": \"MINISTERO\"}";
            String payload = "MINISTERO DELL'INTERNO - DOCUMENTO DI IDENTITA N. AB9876543 Rilascio: 10/05/2019";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);

            assertTrue(modified);
            assertTrue(root.get("enteRilascio").isNull());
        }

        @Test
        @DisplayName("G2: Mantiene ente valido presente nel payload (es. 'COMUNE DI MILANO')")
        void testG2MantieneEnteValidoPresente() throws Exception {
            String json = "{\"enteRilascio\": \"COMUNE DI MILANO\"}";
            String payload = "REPUBBLICA ITALIANA - COMUNE DI MILANO - CARTA D'IDENTITA CA98765AB Rilasciata da: COMUNE DI MILANO";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG2EnteRilascio(root, payload);

            assertFalse(modified);
            assertEquals("COMUNE DI MILANO", root.get("enteRilascio").asString());
        }
    }

    @Nested
    @DisplayName("Evidenza Lessicale Sesso")
    class SessoLexicalEvidenceTests {

        @Test
        @DisplayName("Sesso: annullamento a null se la parola 'sesso' è assente nel payload (Sez. 1.1 GT Rules)")
        void testSessoAnnullatoSeParolaAssente() throws Exception {
            String json = "{\"sesso\": \"M\", \"nome\": \"Mario\", \"cognome\": \"Rossi\"}";
            String payload = "Mario Rossi nato il 15/07/1982 a Roma CF: RSSMRA82L15H501U";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardSessoLexicalEvidence(root, payload);

            assertTrue(modified);
            assertTrue(root.get("sesso").isNull());
        }

        @Test
        @DisplayName("Sesso: mantenuto se la parola 'sesso' è presente nel payload")
        void testSessoMantenutoSeParolaPresente() throws Exception {
            String json = "{\"sesso\": \"M\", \"nome\": \"Mario\", \"cognome\": \"Rossi\"}";
            String payload = "Mario Rossi nato il 15/07/1982 a Roma Sesso: M CF: RSSMRA82L15H501U";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardSessoLexicalEvidence(root, payload);

            assertFalse(modified);
            assertEquals("M", root.get("sesso").asString());
        }
    }

    @Nested
    @DisplayName("G3: Recupero Omissioni Indebite su Campi Codificati")
    class GuardG3Tests {

        @Test
        @DisplayName("G3: Recupero dataRilascio dal punto 4a patente UE")
        void testG3RecuperoDataRilascioPatente() throws Exception {
            String json = "{\"dataRilascio\": null, \"dataScadenza\": \"10/01/2030\"}";
            String payload = "PATENTE UE 4a. 10/01/2020 4b. 10/01/2030 5. U1900092K";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG3OmissionRecovery(root, payload);

            assertTrue(modified);
            assertEquals("10/01/2020", root.get("dataRilascio").asString());
        }

        @Test
        @DisplayName("G3: Integrazione sigla provinciale su luogoNascita (es. 'BOLOGNA' -> 'BOLOGNA (BO)')")
        void testG3IntegrazioneSiglaProvincia() throws Exception {
            String json = "{\"luogoNascita\": \"BOLOGNA\"}";
            String payload = "Nascita: 4 febbraio 1986 a BOLOGNA (BO) - Rilascio: 10/05/2019";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG3OmissionRecovery(root, payload);

            assertTrue(modified);
            assertEquals("BOLOGNA (BO)", root.get("luogoNascita").asString());
        }

        @Test
        @DisplayName("G3: Nessuna modifica se la sigla provinciale è già presente")
        void testG3SiglaProvinciaGiaPresente() throws Exception {
            String json = "{\"luogoNascita\": \"ROMA (RM)\"}";
            String payload = "Nascita: 12/03/1985 ROMA (RM)";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            boolean modified = PostProcessingGuards.guardG3OmissionRecovery(root, payload);

            assertFalse(modified);
            assertEquals("ROMA (RM)", root.get("luogoNascita").asString());
        }
    }

    @Nested
    @DisplayName("G4: Normalizzazione Enum tipoDocumento")
    class GuardG4Tests {

        @Test
        @DisplayName("G4: Normalizzazione sinonimi sul set canonico")
        void testG4NormalizzazioneSinonimi() throws Exception {
            String[][] cases = {
                    {"PATENTE_GUIDA", "PATENTE"},
                    {"PATENTE_DI_GUIDA", "PATENTE"},
                    {"CIE", "CARTA_IDENTITA"},
                    {"CARTA_D_IDENTITA", "CARTA_IDENTITA"},
                    {"DOCUMENTO_IDENTITA", "CARTA_IDENTITA"},
                    {"CF", "TESSERA_SANITARIA"},
                    {"CERTIFICATO_ANAGRAFICO", "NON_SPECIFICATO"},
                    {"ALTRO", "NON_SPECIFICATO"}
            };

            for (String[] pair : cases) {
                ObjectNode root = mapper.createObjectNode();
                root.put("tipoDocumento", pair[0]);
                boolean modified = PostProcessingGuards.guardG4EnumTipoDocumento(root);
                assertTrue(modified, "Attesa normalizzazione per " + pair[0]);
                assertEquals(pair[1], root.get("tipoDocumento").asString());
            }
        }
    }

    @Nested
    @DisplayName("Validatore di Coerenza Anagrafica (V1, V2)")
    class CoherenceValidatorTests {

        @Test
        @DisplayName("V1: Rilevamento scambio nome/cognome su layout numerato UE (1. Cognome, 2. Nome)")
        void testV1LayoutUeSwap() throws Exception {
            String payload = """
                    REPUBBLICA ITALIANA
                    1. BIANCHI
                    2. MARIO
                    3. 12/03/1985 ROMA (RM)
                    """;
            // Modello inverte: nome="BIANCHI", cognome="MARIO"
            String json = "{\"nome\":\"BIANCHI\",\"cognome\":\"MARIO\"}";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            var res = AnagraficaCoherenceValidator.validate(root, payload);

            assertFalse(res.isValid());
            assertEquals("FIELD_SWAP_DETECTED", res.errorCode());
            assertTrue(res.errorMessage().contains("numerazione UE"));
        }

        @Test
        @DisplayName("V1: Ordine corretto 1. Cognome 2. Nome non genera errore")
        void testV1LayoutUeCorretto() throws Exception {
            String payload = """
                    REPUBBLICA ITALIANA
                    1. BIANCHI
                    2. MARIO
                    3. 12/03/1985 ROMA (RM)
                    """;
            String json = "{\"nome\":\"MARIO\",\"cognome\":\"BIANCHI\"}";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            var res = AnagraficaCoherenceValidator.validate(root, payload);

            assertTrue(res.isValid());
        }

        @Test
        @DisplayName("V2: Rilevamento scambio nome/cognome rispetto al Codice Fiscale")
        void testV2CrossFieldCfSwap() throws Exception {
            // Cognome Bianchi -> BNC, Nome Mario -> MRA -> CF atteso BNCMRA...
            // Se invertito: Cognome Mario -> MRA, Nome Bianchi -> BNC -> CF calcolato MRABNC...
            String cf = "BNCMRA85C12H501Z";
            // Modello assegna cognome="Mario", nome="Bianchi"
            String json = "{\"nome\":\"Bianchi\",\"cognome\":\"Mario\",\"codiceFiscale\":\"" + cf + "\"}";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            var res = AnagraficaCoherenceValidator.validate(root, "Documento generico");

            assertFalse(res.isValid());
            assertEquals("FIELD_SWAP_DETECTED", res.errorCode());
            assertTrue(res.errorMessage().contains("Codice Fiscale"));
        }

        @Test
        @DisplayName("V2: CF sintetici di baseline esclusi dalla validazione cross-field")
        void testV2EsclusioneCfSintetici() throws Exception {
            String json = "{\"nome\":\"Mario\",\"cognome\":\"Rossi\",\"codiceFiscale\":\"CFXUSR71A03H501A\"}";
            ObjectNode root = (ObjectNode) mapper.readTree(json);

            var res = AnagraficaCoherenceValidator.validate(root, "Documento");

            assertTrue(res.isValid());
        }

        @Test
        @DisplayName("V0: Rilevamento presenza di cifre nel campo nome o cognome (slittamento codice)")
        void testCifreInNomeOCognome() throws Exception {
            String jsonNome = "{\"nome\":\"U1D123456X\",\"cognome\":\"ROSSI\",\"codiceFiscale\":\"RSSMRA85C12D086Q\"}";
            ObjectNode rootNome = (ObjectNode) mapper.readTree(jsonNome);
            var resNome = AnagraficaCoherenceValidator.validate(rootNome, "Test");
            assertFalse(resNome.isValid());
            assertEquals("FIELD_SWAP_DETECTED", resNome.errorCode());
            assertTrue(resNome.errorMessage().contains("cifre numeriche non ammesse"));

            String jsonCog = "{\"nome\":\"MARIO\",\"cognome\":\"ROSSI123\",\"codiceFiscale\":\"RSSMRA85C12D086Q\"}";
            ObjectNode rootCog = (ObjectNode) mapper.readTree(jsonCog);
            var resCog = AnagraficaCoherenceValidator.validate(rootCog, "Test");
            assertFalse(resCog.isValid());
            assertEquals("FIELD_SWAP_DETECTED", resCog.errorCode());
        }

        @Test
        @DisplayName("V0b: Rilevamento etichette di layout o Codice Fiscale in numeroDocumento")
        void testNumeroDocumentoInvalido() throws Exception {
            // Etichetta di layout tipo "4a"
            String jsonLabel = "{\"nome\":\"MARIO\",\"cognome\":\"ROSSI\",\"numeroDocumento\":\"4a\"}";
            ObjectNode rootLabel = (ObjectNode) mapper.readTree(jsonLabel);
            var resLabel = AnagraficaCoherenceValidator.validate(rootLabel, "Test");
            assertFalse(resLabel.isValid());
            assertEquals("FIELD_SWAP_DETECTED", resLabel.errorCode());
            assertTrue(resLabel.errorMessage().contains("etichetta di layout"));

            // Codice fiscale inserito in numeroDocumento
            String jsonCfInDoc = "{\"nome\":\"MARIO\",\"cognome\":\"ROSSI\",\"numeroDocumento\":\"RSSMRA85C12D086Q\"}";
            ObjectNode rootCfInDoc = (ObjectNode) mapper.readTree(jsonCfInDoc);
            var resCfInDoc = AnagraficaCoherenceValidator.validate(rootCfInDoc, "Test");
            assertFalse(resCfInDoc.isValid());
            assertEquals("FIELD_SWAP_DETECTED", resCfInDoc.errorCode());
            assertTrue(resCfInDoc.errorMessage().contains("Codice Fiscale"));
        }

        @Test
        @DisplayName("V2: Rilevamento scambio parziale: cognome assegnato al nome presente nel CF")
        void testNomeNonCorrispondenteACf() throws Exception {
            // Modello assegna cognome="Mario" (che è il nome nel CF RSSMRA) e nome="Giuseppe"
            String json = "{\"nome\":\"Giuseppe\",\"cognome\":\"Mario\",\"codiceFiscale\":\"RSSMRA85C12D086Q\"}";
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            var res = AnagraficaCoherenceValidator.validate(root, "Test");
            assertFalse(res.isValid());
            assertEquals("FIELD_SWAP_DETECTED", res.errorCode());
        }

        @Test
        @DisplayName("Whole-Word Check: Rilevamento refuso/troncamento (Bianch vs BIANCHI)")
        void testWholeWordEvidenceRefuso() throws Exception {
            String payload = "Cognome: BIANCHI\nNome: ALESSANDRO\nCodice Fiscale: BNCLSN90L14F205I";
            // Modello tronca "Bianch"
            String jsonErr = "{\"nome\":\"ALESSANDRO\",\"cognome\":\"Bianch\",\"codiceFiscale\":\"BNCLSN90L14F205I\"}";
            ObjectNode rootErr = (ObjectNode) mapper.readTree(jsonErr);
            var resErr = AnagraficaCoherenceValidator.validate(rootErr, payload);
            System.out.println("[TEST UNITARIO] Iniettato cognome tronco 'Bianch' contro testo sorgente 'BIANCHI':");
            System.out.println("  -> isValid: " + resErr.isValid());
            System.out.println("  -> Violazioni: " + resErr.violations());
            assertFalse(resErr.isValid());
            assertTrue(resErr.violations().stream().anyMatch(v -> v.path().equals("cognome") && (v.errorMessage().contains("troncamento") || v.errorMessage().contains("non attestato"))));

            // Modello estrae forma integra "Bianchi"
            String jsonOk = "{\"nome\":\"ALESSANDRO\",\"cognome\":\"Bianchi\",\"codiceFiscale\":\"BNCLSN90L14F205I\"}";
            ObjectNode rootOk = (ObjectNode) mapper.readTree(jsonOk);
            var resOk = AnagraficaCoherenceValidator.validate(rootOk, payload);
            System.out.println("[TEST UNITARIO] Cognome integro 'Bianchi' contro testo sorgente: isValid = " + resOk.isValid());
            assertTrue(resOk.isValid());
        }

        @Test
        @DisplayName("Generalizzazione: Supporto nomi composti, apostrofi e accenti (D'ANGELO, DE ROSSI, NICOLÒ)")
        void testGeneralizzazioneNomiApostrofiAccenti() throws Exception {
            // 1. Cognome con apostrofo: D'ANGELO MARCO (CF atteso: DNGMRC...)
            String payload1 = "Cognome: D'ANGELO\nNome: MARCO\nCodice Fiscale: DNGMRC85C12H501Z";
            String json1 = "{\"nome\":\"MARCO\",\"cognome\":\"D'ANGELO\",\"codiceFiscale\":\"DNGMRC85C12H501Z\"}";
            ObjectNode root1 = (ObjectNode) mapper.readTree(json1);
            var res1 = AnagraficaCoherenceValidator.validate(root1, payload1);
            System.out.println("[TEST UNITARIO] Test cognome con apostrofo D'ANGELO: isValid = " + res1.isValid() + ", violazioni = " + res1.violations());
            assertTrue(res1.isValid(), "D'ANGELO deve essere accettato senza falsi scarti");

            // 2. Cognome doppio: DE ROSSI ANNA (CF atteso: DRSNNA...)
            String payload2 = "Cognome: DE ROSSI\nNome: ANNA\nCodice Fiscale: DRSNNA85C12H501Z";
            String json2 = "{\"nome\":\"ANNA\",\"cognome\":\"DE ROSSI\",\"codiceFiscale\":\"DRSNNA85C12H501Z\"}";
            ObjectNode root2 = (ObjectNode) mapper.readTree(json2);
            var res2 = AnagraficaCoherenceValidator.validate(root2, payload2);
            System.out.println("[TEST UNITARIO] Test cognome doppio DE ROSSI: isValid = " + res2.isValid() + ", violazioni = " + res2.violations());
            assertTrue(res2.isValid(), "DE ROSSI deve essere accettato senza falsi scarti");

            // 3. Nome accentato: BIANCHI NICOLÒ (CF atteso: BNCNCL...)
            String payload3 = "Cognome: BIANCHI\nNome: NICOLÒ\nCodice Fiscale: BNCNCL85C12H501Z";
            String json3 = "{\"nome\":\"NICOLÒ\",\"cognome\":\"BIANCHI\",\"codiceFiscale\":\"BNCNCL85C12H501Z\"}";
            ObjectNode root3 = (ObjectNode) mapper.readTree(json3);
            var res3 = AnagraficaCoherenceValidator.validate(root3, payload3);
            System.out.println("[TEST UNITARIO] Test nome accentato NICOLÒ: isValid = " + res3.isValid() + ", violazioni = " + res3.violations());
            assertTrue(res3.isValid(), "NICOLÒ deve essere normalizzato e accettato senza errori");
        }

        @Test
        @DisplayName("Date Cronologiche: Rilevamento scambio tra data di nascita, data di rilascio e scadenza")
        void testIncoerenzaCronologicaDate() throws Exception {
            // Nascita (2020) posteriore a rilascio (1985) come nello scenario B3 corrotto
            String jsonSwapped = "{\"nome\":\"MARIO\",\"cognome\":\"ROSSI\",\"dataNascita\":\"10/05/2020\",\"dataRilascio\":\"12/03/1985\",\"dataScadenza\":\"12/03/2035\"}";
            ObjectNode rootSwapped = (ObjectNode) mapper.readTree(jsonSwapped);
            var resSwapped = AnagraficaCoherenceValidator.validate(rootSwapped, "Documento");
            assertFalse(resSwapped.isValid());
            assertTrue(resSwapped.violations().stream().anyMatch(v -> v.path().equals("dataNascita") && v.errorMessage().contains("posteriore alla data di rilascio")));

            // Date corrette (1985 < 2020 < 2030)
            String jsonOk = "{\"nome\":\"MARIO\",\"cognome\":\"ROSSI\",\"dataNascita\":\"12/03/1985\",\"dataRilascio\":\"10/05/2020\",\"dataScadenza\":\"10/05/2030\"}";
            ObjectNode rootOk = (ObjectNode) mapper.readTree(jsonOk);
            var resOk = AnagraficaCoherenceValidator.validate(rootOk, "Documento");
            assertTrue(resOk.isValid());
        }

        @Test
        @DisplayName("Generalizzazione: Patente UE inedita con nomi diversi (1. VERDI, 2. GIUSEPPE)")
        void testPatenteUeNomiInediti() throws Exception {
            String payload = """
                    PATENTE DI GUIDA DELLA REPUBBLICA ITALIANA
                    1. VERDI
                    2. GIUSEPPE
                    3. 15/06/1980 NAPOLI (NA)
                    4a. 01/04/2015 4c. MIT-UCO 4d. VRDGPP80H15F839Z
                    5. U1X987654Y
                    """;
            // Modello inverte (nome="VERDI", cognome="GIUSEPPE")
            String jsonInverted = "{\"nome\":\"VERDI\",\"cognome\":\"GIUSEPPE\",\"codiceFiscale\":\"VRDGPP80H15F839Z\"}";
            ObjectNode rootInverted = (ObjectNode) mapper.readTree(jsonInverted);
            var resInverted = AnagraficaCoherenceValidator.validate(rootInverted, payload);
            assertFalse(resInverted.isValid());

            // Modello assegna correttamente
            String jsonCorrect = "{\"nome\":\"GIUSEPPE\",\"cognome\":\"VERDI\",\"codiceFiscale\":\"VRDGPP80H15F839Z\"}";
            ObjectNode rootCorrect = (ObjectNode) mapper.readTree(jsonCorrect);
            var resCorrect = AnagraficaCoherenceValidator.validate(rootCorrect, payload);
            assertTrue(resCorrect.isValid());
        }

        @Test
        @DisplayName("Accumulo Errori: Più violazioni distinte catturate contemporaneamente nel report di coerenza")
        void testAccumuloErroriMultipli() throws Exception {
            // Nodo con:
            // 1. numero documento nel nome ("U1D123456X") -> anti-cifre
            // 2. etichetta "4a" in numeroDocumento -> anti-label
            // 3. data di nascita scambiata (2020 vs 1985) -> cronologico
            String json = """
                    {
                      "nome": "U1D123456X",
                      "cognome": "ROSSI",
                      "numeroDocumento": "4a",
                      "dataNascita": "10/05/2020",
                      "dataRilascio": "12/03/1985"
                    }
                    """;
            ObjectNode root = (ObjectNode) mapper.readTree(json);
            var res = AnagraficaCoherenceValidator.validate(root, "Documento di test");

            assertFalse(res.isValid());
            List<String> paths = res.violations().stream().map(AnagraficaCoherenceValidator.CoherenceViolation::path).toList();
            assertTrue(paths.contains("nome"), "Deve contenere errore su campo 'nome'");
            assertTrue(paths.contains("numeroDocumento"), "Deve contenere errore su campo 'numeroDocumento'");
            assertTrue(paths.contains("dataNascita"), "Deve contenere errore su campo 'dataNascita'");
            assertEquals(3, res.violations().size(), "Tutte e 3 le anomalie devono essere collezionate contemporaneamente");
        }

        @Test
        @DisplayName("Regressione Dataset 60: Verifica assenza falsi scarti sulle 20 anagrafiche reali (Dev + Test)")
        void testRegressionDataset60AnagraficaZeroFalseRejections() throws Exception {
            java.io.File datasetFile = new java.io.File("data/dataset_60.json");
            if (!datasetFile.exists()) {
                System.out.println("[REGRESSIONE] data/dataset_60.json non presente. Salto test.");
                return;
            }
            tools.jackson.databind.JsonNode rootNode = mapper.readTree(datasetFile);
            int testedDocs = 0;
            int passedDocs = 0;

            for (tools.jackson.databind.JsonNode doc : rootNode) {
                if ("ANAGRAFICA".equals(doc.path("type").asText())) {
                    testedDocs++;
                    String docId = doc.path("id").asText();
                    String text = doc.path("text").asText();
                    tools.jackson.databind.JsonNode gt = doc.path("ground_truth");
                    tools.jackson.databind.node.ObjectNode gtClone = (tools.jackson.databind.node.ObjectNode) gt.deepCopy();

                    // Applica guardie e validazione di coerenza
                    PostProcessingGuards.applyGuards(gtClone, text, DocumentType.ANAGRAFICA);
                    var res = AnagraficaCoherenceValidator.validate(gtClone, text);

                    if (res.isValid()) {
                        passedDocs++;
                    } else {
                        System.err.println("[FALSO SCARTO] Fallimento su " + docId + ": " + res.violations());
                    }
                }
            }

            System.out.println("[REGRESSIONE DATASET 60] Anagrafiche verificate: " + testedDocs + " | Valide: " + passedDocs + " (Falsi scarti: " + (testedDocs - passedDocs) + ")");
            assertEquals(testedDocs, passedDocs, "Tutte le 20 anagrafiche reali del dataset devono passare la coerenza senza falsi scarti!");
        }
    }

    @Nested
    @DisplayName("Feedback Mirato RF7 (Zero Iniezione Valori Rifiutati)")
    class FeedbackRF7Tests {

        @Test
        @DisplayName("RF7: Template di correzione mirato per FIELD_SWAP_DETECTED conforme e sicuro")
        void testFeedbackSwapTemplateSicuro() {
            FieldError err = new FieldError("nome", "coherence", ValidationErrorCode.FIELD_SWAP_DETECTED, "Inversione rilevata");

            String feedback = ValidationFeedbackBuilder.buildFeedbackMessage(List.of(err));

            assertTrue(feedback.contains("<feedback>") || feedback.contains("ATTENZIONE: Rilevata inversione tra i campi 'nome' e 'cognome'"));
            assertTrue(feedback.contains("1."));
            assertTrue(feedback.contains("COGNOME"));
            assertTrue(feedback.contains("2."));
            assertTrue(feedback.contains("NOME"));
            assertTrue(feedback.contains("Codice Fiscale"));
            assertFalse(feedback.contains("Bianchi"));
            assertFalse(feedback.contains("Mario"));
        }
    }

    @Nested
    @DisplayName("Integrazione End-to-End in OutputValidationStep")
    class OutputValidationStepIntegrationTests {

        @Test
        @DisplayName("Pipeline ANAGRAFICA: OutputValidationStep intercetta inversione nome/cognome e applica guardie")
        void testOutputValidationStepIntercettaInversioneEApplicaGuardie() {
            OutputValidationStep step = new OutputValidationStep("val_anagrafica", List.of(), Collections.emptyList());

            String payload = """
                    REPUBBLICA ITALIANA
                    PATENTE DI GUIDA
                    1. BIANCHI
                    2. MARIO
                    4a. 15/06/2021
                    """;
            // Modello estrae nome/cognome invertiti, enteRilascio allucinato, dataRilascio null, tipoDocumento non canonico
            String rawOutput = """
                    {
                        "tipoDocumento": "PATENTE_GUIDA",
                        "nome": "BIANCHI",
                        "cognome": "MARIO",
                        "enteRilascio": "05/05/2020",
                        "dataRilascio": null
                    }
                    """;

            PipelineContext ctx = new PipelineContext(payload, DocumentType.ANAGRAFICA);
            ctx.setSanitizedInput(payload);
            ctx.setModelOutput(rawOutput);

            ValidationResult res = step.execute(ctx);

            // Validazione deve fallire per FIELD_SWAP_DETECTED
            assertFalse(res.isValid());
            assertTrue(res.fieldErrors().stream().anyMatch(e -> e.code() == ValidationErrorCode.FIELD_SWAP_DETECTED));

            // Ma le guardie simboliche hanno normalizzato l'output nel context:
            // - tipoDocumento normalizzato a PATENTE
            // - dataRilascio recuperata dal punto 4a.
            // - enteRilascio annullato a null
            String normalizedJson = ctx.getModelOutput();
            assertTrue(normalizedJson.contains("\"PATENTE\""));
            assertTrue(normalizedJson.contains("15/06/2021"));
            assertTrue(normalizedJson.contains("\"enteRilascio\":null"));
        }
    }
}
