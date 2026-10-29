package com.tirocinioaltrama.llmorchestrator.impl.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tirocinioaltrama.llmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.Pipeline;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.llmorchestrator.core.pipeline.PipelineResult;

/**
 * Test di parità e baseline del comportamento delle pipeline.
 * Fissa i risultati attesi (validità, errori specifici, gestione campi opzionali/vuoti)
 * su payload mock predefiniti, garantendo che la migrazione a YAML mantenga
 * esattamente la stessa semantica.
 */
class PipelineParityBaselineTest {

    private ModelClient modelClient;
    private Pipeline emailPipeline;
    private Pipeline fatturaPipeline;
    private Pipeline anagraficaPipeline;
    private Pipeline altroPipeline;

    @BeforeEach
    void setUp() {
        modelClient = mock(ModelClient.class);
        OllamaProperties properties = new OllamaProperties(
                "http://localhost:11434",
                "default-model",
                "email-worker-model",
                "invoice-worker-model",
                "anagrafica-worker-model",
                null,
                null
        );

        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setProperty("ollama.model", "default-model");
        env.setProperty("ollama.email-model", "email-worker-model");
        env.setProperty("ollama.invoice-model", "invoice-worker-model");
        env.setProperty("ollama.anagrafica-model", "anagrafica-worker-model");

        com.tirocinioaltrama.llmorchestrator.core.validation.ValidatorRegistry validatorRegistry = new com.tirocinioaltrama.llmorchestrator.core.validation.ValidatorRegistry();
        com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry toolRegistry = new com.tirocinioaltrama.llmorchestrator.core.tool.ToolRegistry();
        org.springframework.core.io.DefaultResourceLoader resourceLoader = new org.springframework.core.io.DefaultResourceLoader();
        com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.PipelineConfigValidator configValidator = 
                new com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.PipelineConfigValidator(validatorRegistry, toolRegistry, resourceLoader);
        com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.YamlPipelineLoader loader = 
                new com.tirocinioaltrama.llmorchestrator.impl.pipeline.config.YamlPipelineLoader(env, validatorRegistry, configValidator);

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

    // ==========================================
    // PIPELINE EMAIL
    // ==========================================

    @Test
    @DisplayName("Email - payload valido con email corretta")
    void emailPayloadValido() {
        String json = """
                {"mittente":"Mario Rossi","email":"mario.rossi@example.com","oggetto":"Richiesta"}
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 50));

        PipelineContext context = new PipelineContext("Input email", DocumentType.EMAIL);
        PipelineResult result = emailPipeline.execute(context);

        assertTrue(result.success());
        assertEquals(json, result.output());
        assertTrue(result.validationResult().isValid());
        assertTrue(result.validationResult().errors().isEmpty());
    }

    @Test
    @DisplayName("Email - payload con email non valida produce errore")
    void emailPayloadEmailInvalida() {
        String json = """
                {"mittente":"Mario Rossi","email":"non-una-email-valida","oggetto":"Richiesta"}
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 50));

        PipelineContext context = new PipelineContext("Input email", DocumentType.EMAIL);
        PipelineResult result = emailPipeline.execute(context);

        assertFalse(result.success());
        assertEquals(1, result.validationResult().errors().size());
        String err = result.validationResult().errors().get(0);
        assertTrue(err.contains("email"), () -> "Messaggio effettivo: " + err);
    }

    @Test
    @DisplayName("Email - campo email (required nello schema) estratto vuoto genera errore di validazione")
    void emailPayloadEmailVuota() {
        String json = """
                {"mittente":"Mario Rossi","email":"","oggetto":"Richiesta"}
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 50));

        PipelineContext context = new PipelineContext("Input email", DocumentType.EMAIL);
        PipelineResult result = emailPipeline.execute(context);

        // Nuovo comportamento voluto: campo required estratto come stringa vuota o blank fallisce
        assertFalse(result.success());
        assertEquals(1, result.validationResult().errors().size());
        assertTrue(result.validationResult().errors().get(0).contains("presente ma vuoto"));
    }

    // ==========================================
    // PIPELINE FATTURA
    // ==========================================

    @Test
    @DisplayName("Fattura - payload valido con P.IVA, CF e IBAN corretti")
    void fatturaPayloadValido() {
        String json = """
                {
                    "numeroFattura": "FAT-2026-01",
                    "dataFattura": "2026-03-15",
                    "fornitore": {
                        "denominazione": "Acme Srl",
                        "partitaIva": "06363391001"
                    },
                    "cliente": {
                        "denominazione": "Mario Rossi",
                        "codiceFiscale": "RSSMRA85M01H501Q"
                    },
                    "importoTotale": 1500.50,
                    "iban": "IT60X0542811101000000123456"
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 60));

        PipelineContext context = new PipelineContext("Input fattura", DocumentType.FATTURA);
        PipelineResult result = fatturaPipeline.execute(context);

        assertTrue(result.success(), () -> "Errori inattesi: " + result.validationResult().errors());
        assertTrue(result.validationResult().errors().isEmpty());
    }

    @Test
    @DisplayName("Fattura - P.IVA errata, CF errato e IBAN errato generano errori specifici")
    void fatturaPayloadValoriErrati() {
        String json = """
                {
                    "numeroFattura": "FAT-01",
                    "dataFattura": "2026-10-07",
                    "fornitore": {
                        "denominazione": "Acme Srl",
                        "partitaIva": "123"
                    },
                    "cliente": {
                        "denominazione": "Mario Rossi",
                        "codiceFiscale": "CODICEFISCALEERRATO"
                    },
                    "importoTotale": 100.0,
                    "iban": "IBANERRATO"
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 60));

        PipelineContext context = new PipelineContext("Input fattura", DocumentType.FATTURA);
        PipelineResult result = fatturaPipeline.execute(context);

        assertFalse(result.success());
        assertEquals(3, result.validationResult().errors().size());
        assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("partita IVA") || e.contains("Partita IVA")));
        assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("codice fiscale") || e.contains("Codice Fiscale")));
        assertTrue(result.validationResult().errors().stream().anyMatch(e -> e.contains("IBAN")));
    }

    @Test
    @DisplayName("Fattura - campi opzionali assenti o null non causano errori di validazione formato")
    void fatturaPayloadCampiOpzionaliAssenti() {
        String json = """
                {
                    "numeroFattura": "FAT-02",
                    "dataFattura": null,
                    "fornitore": {
                        "denominazione": "Acme Srl",
                        "partitaIva": null
                    },
                    "cliente": null,
                    "importoTotale": 50.0,
                    "iban": null
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 40));

        PipelineContext context = new PipelineContext("Input fattura", DocumentType.FATTURA);
        PipelineResult result = fatturaPipeline.execute(context);

        assertTrue(result.success());
        assertTrue(result.validationResult().isValid());
        assertTrue(result.validationResult().errors().isEmpty());
    }

    @Test
    @DisplayName("Fattura - campi con validatori presenti ma null vengono ignorati dai validatori di formato")
    void fatturaPayloadCampiValidabiliVuotiONull() {
        String json = """
                {
                    "numeroFattura": "FAT-03",
                    "dataFattura": null,
                    "fornitore": {
                        "denominazione": "Acme Srl",
                        "partitaIva": null
                    },
                    "cliente": {
                        "denominazione": "Cliente",
                        "codiceFiscale": null
                    },
                    "importoTotale": 50.0,
                    "iban": null
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 40));

        PipelineContext context = new PipelineContext("Input fattura", DocumentType.FATTURA);
        PipelineResult result = fatturaPipeline.execute(context);

        assertTrue(result.success(), () -> "Errori inattesi: " + result.validationResult().errors());
        assertTrue(result.validationResult().isValid());
        assertTrue(result.validationResult().errors().isEmpty());
    }

    // ==========================================
    // PIPELINE ANAGRAFICA
    // ==========================================

    @Test
    @DisplayName("Anagrafica - payload valido con CF corretto")
    void anagraficaPayloadValido() {
        String json = """
                {
                    "tipoDocumento": "Carta d'identita",
                    "numeroDocumento": null,
                    "nome": "Mario",
                    "cognome": "Rossi",
                    "codiceFiscale": "RSSMRA85M01H501Q",
                    "dataNascita": null,
                    "luogoNascita": null,
                    "sesso": null,
                    "dataRilascio": null,
                    "dataScadenza": null,
                    "enteRilascio": null
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 40));

        PipelineContext context = new PipelineContext("Input anagrafica", DocumentType.ANAGRAFICA);
        PipelineResult result = anagraficaPipeline.execute(context);

        assertTrue(result.success(), () -> "Errori inattesi: " + result.validationResult().errors());
        assertTrue(result.validationResult().isValid());
        assertTrue(result.validationResult().errors().isEmpty());
    }

    @Test
    @DisplayName("Anagrafica - CF con carattere di controllo errato fallisce")
    void anagraficaPayloadCfErrato() {
        String json = """
                {
                    "tipoDocumento": "Carta d'identita",
                    "numeroDocumento": null,
                    "nome": "Mario",
                    "cognome": "Rossi",
                    "codiceFiscale": "RSSMRA85M01H501A",
                    "dataNascita": null,
                    "luogoNascita": null,
                    "sesso": null,
                    "dataRilascio": null,
                    "dataScadenza": null,
                    "enteRilascio": null
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 40));

        PipelineContext context = new PipelineContext("Input anagrafica", DocumentType.ANAGRAFICA);
        PipelineResult result = anagraficaPipeline.execute(context);

        assertFalse(result.success());
        assertEquals(1, result.validationResult().errors().size());
        assertTrue(result.validationResult().errors().get(0).contains("codice fiscale"));
    }

    // ==========================================
    // PIPELINE ALTRO
    // ==========================================

    @Test
    @DisplayName("Altro - payload valido senza validatori specialistici")
    void altroPayloadValido() {
        String json = """
                {
                    "categoriaRilevata": "Richiesta generica",
                    "argomentoPrincipale": "Informazioni"
                }
                """;
        when(modelClient.generate(any(ModelRequest.class))).thenReturn(new ModelResponse(json, 30));

        PipelineContext context = new PipelineContext("Input altro", DocumentType.ALTRO);
        PipelineResult result = altroPipeline.execute(context);

        assertTrue(result.success());
        assertTrue(result.validationResult().isValid());
        assertTrue(result.validationResult().errors().isEmpty());
    }
}
