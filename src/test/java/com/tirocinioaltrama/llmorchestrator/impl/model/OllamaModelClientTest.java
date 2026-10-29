package com.tirocinioaltrama.llmorchestrator.impl.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tirocinioaltrama.llmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.llmorchestrator.core.model.ModelResponse;

@SpringBootTest
@Tag("ollama")
@Tag("live")
class OllamaModelClientTest {

    private static final String ANAGRAFICA_SCHEMA_PATH = "/schemas/anagrafica_documento.json";
    private static final String EMAIL_SCHEMA_PATH = "/schemas/email_info.json";

    private static final String TESTO_ANAGRAFICA = """
            Buongiorno, scrivo per aggiornare i miei dati. Sono Mario Rossi, nato a Cosenza
            il 12/03/1985. Il mio codice fiscale è RSSMRA85C12D086Q e abito in via Roma 15,
            87100 Cosenza. Cordiali saluti.
            """;

    private static final String TESTO_EMAIL = """
            Da: Laura Bianchi <laura.bianchi@acme.it>
            Oggetto: Richiesta preventivo per workshop aziendale
            Data: 15/10/2026

            Gentile Team,
            Vi scrivo a nome di Acme Srl per richiedere la disponibilità di organizzare un workshop per 40 partecipanti
            nella città di Milano in data 12/11/2026.
            Abbiamo a disposizione un budget stimato di 5000 euro.
            Potete ricontattarmi via email oppure al recapito telefonico +39 02 9876543.

            Cordiali saluti,
            Laura Bianchi
            Acme Srl
            """;

    @Autowired
    private ModelClient modelClient;

    @Autowired
    private OllamaProperties properties;

    @Test
    void estraeAnagraficaDaTestoFinto() throws IOException {
        assumeTrue(ollamaRaggiungibile(), "Ollama non raggiungibile su " + properties.baseUrl());

        ModelRequest request = new ModelRequest(
                properties.model(),
                "<payload>\n" + TESTO_ANAGRAFICA + "</payload>",
                leggiSchema(ANAGRAFICA_SCHEMA_PATH));

        ModelResponse response = modelClient.generate(request);

        System.out.println("Latenza anagrafica: " + response.latencyMs() + " ms");
        System.out.println("Risposta anagrafica:\n" + response.content());

        assertFalse(response.content().isBlank());
        assertTrue(response.content().strip().startsWith("{"));
        assertTrue(response.content().contains("Rossi") || response.content().contains("RSSMRA85C12D086Q"));
    }

    @Test
    void estraeDatiDaEmail() throws IOException {
        assumeTrue(ollamaRaggiungibile(), "Ollama non raggiungibile su " + properties.baseUrl());

        // Test con modello passato esplicitamente come stringa per verificare modularità
        String modelName = properties.model();
        ModelRequest request = new ModelRequest(
                modelName,
                "<payload>\n" + TESTO_EMAIL + "</payload>",
                leggiSchema(EMAIL_SCHEMA_PATH));

        ModelResponse response = modelClient.generate(request);

        System.out.println("Latenza email: " + response.latencyMs() + " ms");
        System.out.println("Risposta estrazione email:\n" + response.content());

        assertFalse(response.content().isBlank());
        assertTrue(response.content().strip().startsWith("{"));
        assertTrue(response.content().contains("laura.bianchi@acme.it"));
        assertTrue(response.content().contains("Acme"));
    }

    @Test
    void estraeDatiConModelloDefaultSeStringaModelloNulla() throws IOException {
        assumeTrue(ollamaRaggiungibile(), "Ollama non raggiungibile su " + properties.baseUrl());

        // Test del fallback sul modello default se la stringa model è null
        ModelRequest request = new ModelRequest(
                null,
                "<payload>\n" + TESTO_EMAIL + "</payload>",
                leggiSchema(EMAIL_SCHEMA_PATH));

        ModelResponse response = modelClient.generate(request);

        assertFalse(response.content().isBlank());
        assertTrue(response.content().strip().startsWith("{"));
    }

    @Test
    void estraeDatiDaFatturaConModelloDedicato() throws IOException {
        assumeTrue(ollamaRaggiungibile(), "Ollama non raggiungibile su " + properties.baseUrl());

        String testoFattura = """
                FATTURA ELETTRONICA N. FT-2026/042 del 28/02/2026
                FORNITORE:
                TechSolutions S.r.l. - Via dell'Innovazione 10, Milano (MI)
                Partita IVA: IT09876543217

                CLIENTE:
                Beta Consulting S.p.A. - Piazza Affari 2, Roma
                Codice Fiscale: 01234567890

                DESCRIZIONE:
                Consulenza sviluppo software architettura SLM
                TOTALE FATTURA: 4270.00 EUR
                Coordinate pagamento IBAN: IT60X0542811101000000123456
                """;

        String modelName = properties.invoiceModel();
        ModelRequest request = new ModelRequest(
                modelName,
                "<payload>\n" + testoFattura + "</payload>",
                leggiSchema("/schemas/fattura_info.json"));

        ModelResponse response = modelClient.generate(request);

        System.out.println("Latenza fattura: " + response.latencyMs() + " ms");
        System.out.println("Risposta estrazione fattura:\n" + response.content());

        assertFalse(response.content().isBlank());
        assertTrue(response.content().strip().startsWith("{"));
        assertTrue(response.content().contains("FT-2026/042"));
        assertTrue(response.content().contains("TechSolutions"));
    }

    @Test
    void estraeDatiDaDocumentoIdentitaConModelloAnagrafica() throws IOException {
        assumeTrue(ollamaRaggiungibile(), "Ollama non raggiungibile su " + properties.baseUrl());

        String testoDocumento = """
                REPUBBLICA ITALIANA - COMUNE DI MILANO
                CARTA D'IDENTITÀ ELETTRONICA N. CA98765AB
                Cognome: ROSSI
                Nome: MARIO GIUSEPPE
                Luogo e data di nascita: ROMA (RM) 15/07/1982
                Sesso: M
                Codice Fiscale: RSSMGS82L15H501U
                Data di emissione: 10/01/2022
                Data di scadenza: 15/07/2032
                Rilasciata da: COMUNE DI MILANO
                """;

        String modelName = properties.anagraficaModel();
        ModelRequest request = new ModelRequest(
                modelName,
                "<payload>\n" + testoDocumento + "</payload>",
                leggiSchema("/schemas/anagrafica_documento.json"));

        ModelResponse response = modelClient.generate(request);

        System.out.println("Latenza anagrafica: " + response.latencyMs() + " ms");
        System.out.println("Risposta estrazione anagrafica documento:\n" + response.content());

        assertFalse(response.content().isBlank());
        assertTrue(response.content().strip().startsWith("{"));
        assertTrue(response.content().contains("ROSSI") || response.content().contains("MARIO"));
        assertTrue(response.content().contains("RSSMGS82L15H501U"));
    }

    private String leggiSchema(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Schema non trovato: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private boolean ollamaRaggiungibile() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/api/tags"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        try {
            HttpResponse<Void> resp = client.send(request, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}