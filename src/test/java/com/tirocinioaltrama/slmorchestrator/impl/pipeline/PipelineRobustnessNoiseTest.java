package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.tirocinioaltrama.slmorchestrator.core.tool.ToolRegistry;
import com.tirocinioaltrama.slmorchestrator.core.validation.field.CodiceFiscaleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidatorRegistry;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.PipelineConfigValidator;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.config.YamlPipelineLoader;

/**
 * Suite di test di robustezza e affidabilità ("Stress & Noise Test Suite").
 * Sottopone il sistema a payload con forte rumore OCR, formattazioni insolite,
 * tentativi di prompt injection incorporati, caratteri di controllo, JSON non compatti,
 * spazi multipli, e discrepanze di formati reali tipiche del mondo contabile e documentale.
 */
class PipelineRobustnessNoiseTest {

    private ModelClient modelClient;
    private Pipeline emailPipeline;
    private Pipeline fatturaPipeline;
    private Pipeline anagraficaPipeline;
    private Pipeline altroPipeline;

    @BeforeEach
    void setUp() {
        modelClient = mock(ModelClient.class);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ollama.model", "default-model");
        env.setProperty("ollama.email-model", "email-worker-model");
        env.setProperty("ollama.invoice-model", "invoice-worker-model");
        env.setProperty("ollama.anagrafica-model", "anagrafica-worker-model");

        ValidatorRegistry validatorRegistry = new ValidatorRegistry();
        ToolRegistry toolRegistry = new ToolRegistry();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        PipelineConfigValidator configValidator = new PipelineConfigValidator(validatorRegistry, toolRegistry, resourceLoader);
        YamlPipelineLoader loader = new YamlPipelineLoader(env, validatorRegistry, configValidator);

        List<Pipeline> loaded = loader.loadPipelines(modelClient);
        for (Pipeline p : loaded) {
            switch (p.getSupportedType()) {
                case EMAIL -> emailPipeline = p;
                case FATTURA -> fatturaPipeline = p;
                case ANAGRAFICA -> anagraficaPipeline = p;
                case ALTRO -> altroPipeline = p;
            }
        }
    }

    @Nested
    @DisplayName("1. Robustezza Fattura con Rumore & Formattazioni Insolite")
    class FatturaNoiseTests {

        @Test
        @DisplayName("Fattura con P.IVA con prefisso 'IT', spazi interni e IBAN formattato a blocchi di 4 caratteri")
        void fatturaConSpaziEPrefissi() {
            // Test di tolleranza su output modello che estrae formati con formattazione visiva umana
            String jsonOutput = """
                    {
                        "numeroFattura": "   FATT-2026/099-BIS   ",
                        "dataFattura": "2026-10-07",
                        "fornitore": {
                            "denominazione": "Acme Corporation S.r.l.",
                            "partitaIva": " IT 06363391001 "
                        },
                        "cliente": {
                            "denominazione": "Mario Rossi",
                            "codiceFiscale": " RSS MRA 85M01 H501Q "
                        },
                        "importoTotale": 4999.99,
                        "iban": " IT60 X054 2811 1010 0000 0123 456 "
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 120));

            PipelineContext ctx = new PipelineContext("Input fattura con rumore", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertTrue(result.success(), () -> "Validazione fallita: " + result.validationResult().errors());
            assertTrue(result.validationResult().errors().isEmpty());
        }

        @Test
        @DisplayName("Fattura con caratteri speciali, accenti, simboli XML e ritorni a capo nei valori")
        void fatturaConCaratteriSpecialiENewlines() {
            String jsonOutput = """
                    {
                        "numeroFattura": "FAT/2026#01\\nREV-2",
                        "dataFattura": "2026-10-07",
                        "fornitore": {
                            "denominazione": "Société & Müller <Tech> S.à.r.l. - C&D",
                            "partitaIva": "06363391001"
                        },
                        "cliente": {
                            "denominazione": "Ente Pubblico / Dipartimento 3",
                            "codiceFiscale": null
                        },
                        "importoTotale": 0.0,
                        "iban": "IT60X0542811101000000123456"
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 80));

            PipelineContext ctx = new PipelineContext("Input con testo speciale", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertTrue(result.success(), () -> "Errori: " + result.validationResult().errors());
        }

        @Test
        @DisplayName("Fattura con P.IVA con checksum Luhn corretto ma codice paese estero non supportato")
        void fatturaPivaEsteraNonValida() {
            String jsonOutput = """
                    {
                        "numeroFattura": "INV-DE-1",
                        "fornitore": {
                            "denominazione": "Berlin Gmbh",
                            "partitaIva": "DE123456789"
                        },
                        "importoTotale": 100.0
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 60));

            PipelineContext ctx = new PipelineContext("Input fattura estera", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertFalse(result.success());
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("partita IVA")));
        }

        @Test
        @DisplayName("Fattura con IBAN contenente caratteri non alfanumerici casuali da errore OCR")
        void fatturaIbanRumorosoOcr() {
            String jsonOutput = """
                    {
                        "numeroFattura": "FAT-01",
                        "fornitore": {
                            "denominazione": "Fornitore Srl",
                            "partitaIva": "06363391001"
                        },
                        "importoTotale": 150.0,
                        "iban": "IT60X054281110100000012345?"
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 50));

            PipelineContext ctx = new PipelineContext("Input OCR rumoroso", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertFalse(result.success());
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("IBAN")));
        }
    }

    @Nested
    @DisplayName("2. Robustezza Anagrafica con Casi Limite & Omocodia")
    class AnagraficaEdgeCasesTests {

        @Test
        @DisplayName("Anagrafica valida con omocodia (cifre sostituite da lettere della tabella ministeriale)")
        void anagraficaConOmocodiaValida() {
            // Codice fiscale base: RSSMRA85M01H501Q
            // Sostituzione omocodica della penultima cifra (0 -> N o 1 -> P, calcolato ufficialmente)
            // Usiamo un CF omocodico valido e verificato da CodiceFiscaleValidator
            char cinOmocodico = CodiceFiscaleValidator.calculateCin("RSSMRA8RM01H501");
            String cfOmocodico = "RSSMRA8RM01H501" + cinOmocodico;

            String jsonOutput = """
                    {
                        "tipoDocumento": "CARTA_IDENTITA",
                        "nome": "Mario",
                        "cognome": "Rossi",
                        "codiceFiscale": "%s",
                        "dataNascita": null,
                        "luogoNascita": null,
                        "sesso": null,
                        "numeroDocumento": null,
                        "dataRilascio": null,
                        "dataScadenza": null,
                        "enteRilascio": null
                    }
                    """.formatted(cfOmocodico);

            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 70));

            PipelineContext ctx = new PipelineContext("Input CIE", DocumentType.ANAGRAFICA);
            PipelineResult result = anagraficaPipeline.execute(ctx);

            assertTrue(result.success(), () -> "Omocodia rifiutata: " + result.validationResult().errors());
        }

        @Test
        @DisplayName("Anagrafica con CF donna (giorno +40) e minuscolo")
        void anagraficaDonnaMinuscolo() {
            String jsonOutput = """
                    {
                        "tipoDocumento": "PATENTE",
                        "nome": "Francesca",
                        "cognome": "Rossi",
                        "codiceFiscale": "   rssfnc85m41h501b   ",
                        "dataNascita": null,
                        "luogoNascita": null,
                        "sesso": null,
                        "numeroDocumento": null,
                        "dataRilascio": null,
                        "dataScadenza": null,
                        "enteRilascio": null
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 50));

            PipelineContext ctx = new PipelineContext("Input patente", DocumentType.ANAGRAFICA);
            PipelineResult result = anagraficaPipeline.execute(ctx);

            assertTrue(result.success());
        }

        @Test
        @DisplayName("Anagrafica con CF errato nel mese (lettera 'Z' non esistente nei mesi fiscali)")
        void anagraficaMeseInesistente() {
            String jsonOutput = """
                    {
                        "tipoDocumento": "PASSAPORTO",
                        "nome": "Mario",
                        "cognome": "Rossi",
                        "codiceFiscale": "RSSMRA85Z01H501Q",
                        "dataNascita": null,
                        "luogoNascita": null,
                        "sesso": null,
                        "numeroDocumento": null,
                        "dataRilascio": null,
                        "dataScadenza": null,
                        "enteRilascio": null
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 40));

            PipelineContext ctx = new PipelineContext("Input passaporto", DocumentType.ANAGRAFICA);
            PipelineResult result = anagraficaPipeline.execute(ctx);

            assertFalse(result.success());
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("codice fiscale")));
        }

        @Test
        @DisplayName("Anagrafica con CF di lunghezza 15 o 17 (troncato o allungato da allucinazione)")
        void anagraficaLunghezzaErrata() {
            String jsonOutput = """
                    {
                        "tipoDocumento": "CARTA_IDENTITA",
                        "nome": "Mario",
                        "cognome": "Rossi",
                        "codiceFiscale": "RSSMRA85M01H501",
                        "dataNascita": null,
                        "luogoNascita": null,
                        "sesso": null,
                        "numeroDocumento": null,
                        "dataRilascio": null,
                        "dataScadenza": null,
                        "enteRilascio": null
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonOutput, 40));

            PipelineContext ctx = new PipelineContext("Input", DocumentType.ANAGRAFICA);
            PipelineResult result = anagraficaPipeline.execute(ctx);

            assertFalse(result.success());
            assertEquals(1, result.validationResult().errors().size());
        }
    }

    @Nested
    @DisplayName("3. Robustezza Email con RFC 5322 & Domini Moderni")
    class EmailRobustnessTests {

        @ParameterizedTest(name = "Email valida con indirizzo non convenzionale: {0}")
        @ValueSource(strings = {
                "user.name+tag@sub.domain.co.uk",
                "developer@startup.technology",
                "custom-support_123@studio.photography",
                "first.last-part@domain.software",
                "user!name#testing@domain.org"
        })
        void emailIndirizziComplessiValidi(String emailValida) {
            String json = """
                    {
                        "mittente": "User Test",
                        "email": "%s"
                    }
                    """.formatted(emailValida);
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 40));

            PipelineContext ctx = new PipelineContext("Email text", DocumentType.EMAIL);
            PipelineResult result = emailPipeline.execute(ctx);

            assertTrue(result.success(), () -> "Email ritenuta invalida: " + emailValida + ", errori: " + result.validationResult().errors());
        }

        @ParameterizedTest(name = "Email invalida con difetti sintattici tipici: {0}")
        @ValueSource(strings = {
                "@no-user.com",
                "user@",
                "user..doublepoint@domain.com",
                ".leadingdot@domain.com",
                "trailingdot.@domain.com",
                "user@-domain.com",
                "user@domain-.com",
                "plainaddress-without-at",
                "user@domain..com"
        })
        void emailSintatticamenteNonValide(String emailInvalida) {
            String json = """
                    {
                        "mittente": "User Test",
                        "email": "%s"
                    }
                    """.formatted(emailInvalida);
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 40));

            PipelineContext ctx = new PipelineContext("Email text", DocumentType.EMAIL);
            PipelineResult result = emailPipeline.execute(ctx);

            assertFalse(result.success(), () -> "Email invalida accettata erroneamente: " + emailInvalida);
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("email") || e.contains("Email")));
        }
    }

    @Nested
    @DisplayName("4. Robustezza su Risposte JSON Degradate o Allucinate")
    class DegradedJsonTests {

        @Test
        @DisplayName("Modello restituisce JSON sintatticamente rotto (stringa troncata a metà)")
        void jsonSintatticamenteRotto() {
            String brokenJson = "{\"numeroFattura\":\"FAT-2026-01\",\"importoTotale\": 500.0, \"fornitore\": {";
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(brokenJson, 30));

            PipelineContext ctx = new PipelineContext("Input fattura", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertFalse(result.success());
            assertNotNull(result.validationResult());
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("JSON non valido sintatticamente")));
        }

        @Test
        @DisplayName("Modello restituisce testo naturale invece di JSON (ignora il vincolo)")
        void modelloRestituisceTestoNaturale() {
            String naturalText = "Ecco la fattura richiesta: Numero 123 del 01/01/2026 da Rossi Srl per 100 euro.";
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(naturalText, 45));

            PipelineContext ctx = new PipelineContext("Input fattura", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertFalse(result.success());
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("JSON non valido")));
        }

        @Test
        @DisplayName("Modello restituisce oggetto con campi aggiuntivi allucinati non previsti")
        void jsonConCampiAllucinatiExtra() {
            // Campi extra non devono rompere la pipeline a meno che non violino regole specifiche
            String jsonWithExtras = """
                    {
                        "numeroFattura": "FAT-01",
                        "dataFattura": "2026-10-07",
                        "fornitore": {
                            "denominazione": "Acme Srl",
                            "partitaIva": "06363391001",
                            "noteInterneAllucinate": "Attenzione cliente insolvente"
                        },
                        "cliente": null,
                        "importoTotale": 100.0,
                        "iban": null,
                        "meteoPrevisto": "Soleggiato a Milano"
                    }
                    """;
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(jsonWithExtras, 50));

            PipelineContext ctx = new PipelineContext("Input", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            assertTrue(result.success(), () -> "Campi extra hanno rotto la pipeline: " + result.validationResult().errors());
        }

        @Test
        @DisplayName("Modello restituisce array JSON invece di oggetto JSON principale")
        void jsonArrayInveceDiObject() {
            String arrayJson = "[{\"numeroFattura\":\"FAT-01\"}]";
            when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(arrayJson, 30));

            PipelineContext ctx = new PipelineContext("Input", DocumentType.FATTURA);
            PipelineResult result = fatturaPipeline.execute(ctx);

            // La radice di fattura richiede numeroFattura, fornitore, importoTotale come oggetto radice
            assertFalse(result.success());
            assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("mancante") || e.contains("obbligatorio")));
        }
    }
}
