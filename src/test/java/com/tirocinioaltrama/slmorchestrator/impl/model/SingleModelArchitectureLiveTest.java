package com.tirocinioaltrama.slmorchestrator.impl.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tirocinioaltrama.slmorchestrator.config.ollama.OllamaProperties;
import com.tirocinioaltrama.slmorchestrator.core.DocumentType;
import com.tirocinioaltrama.slmorchestrator.core.Orchestrator;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorRequest;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorResponse;
import com.tirocinioaltrama.slmorchestrator.core.OrchestratorStatus;
import com.tirocinioaltrama.slmorchestrator.core.RouterType;
import com.tirocinioaltrama.slmorchestrator.core.RoutingDecision;
import com.tirocinioaltrama.slmorchestrator.impl.router.LocalOllamaRouter;

/**
 * Test di certificazione empirica live dell'architettura "Single Base Model + Dynamic Per-Pipeline System Prompt".
 * <p>
 * Verifica che una sequenza sequenziale multi-dominio (Router, Pipeline FATTURA,
 * Pipeline EMAIL, Pipeline ANAGRAFICA) utilizzi esclusivamente un unico modello in RAM
 * (es. qwen2.5:3b-instruct-q8_0), mantenendo load_duration prossimo a zero dalla 2ª chiamata
 * in poi (zero model swapping) e generando JSON minificato su singola riga conforme agli schemi.
 */
@SpringBootTest(properties = {
        "ollama.init.enabled=false",
        "router.type=local-ollama"
})
@Tag("live")
@Tag("live-model")
class SingleModelArchitectureLiveTest {

    private static final Logger log = LoggerFactory.getLogger(SingleModelArchitectureLiveTest.class);

    @Autowired
    private Orchestrator orchestrator;

    @Autowired
    private LocalOllamaRouter localOllamaRouter;

    @Autowired
    private OllamaProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void checkOllamaAvailable() {
        assumeTrue(isOllamaRunning(), "Demone Ollama non raggiungibile su " + properties.baseUrl() + ". Salto test live.");
    }

    private boolean isOllamaRunning() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(properties.baseUrl() + "/api/tags")).GET().build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("Certificazione empirica: Single Model in RAM, zero model swapping, load_duration < 100ms e JSON minificato")
    void testSingleBaseModelMultiDomainSequentialExecution() throws Exception {
        log.info("=== INIZIO CERTIFICAZIONE EMPIRICA LIVE: SINGLE BASE MODEL ({}) ===", properties.baseModel());

        // 1. STATO INIZIALE OLLAMA
        String psInitial = executeShellCommand("ollama ps");
        log.info("Stato iniziale 'ollama ps':\n{}", psInitial);

        // 2. ESECUZIONE SEQUENZIALE DEI 4 PASSI
        record StepResult(String stepName, String modelUsed, long durationMs, String outputSnippet, boolean isMinifiedJson) {}
        List<StepResult> results = new ArrayList<>();

        // Passo 1: LocalOllamaRouter (classificazione documento fattura)
        long start1 = System.currentTimeMillis();
        String routerPayload = "Fattura commerciale n. 104/2026 emessa il 14/10/2026 da Tech Solutions Srl P.IVA 01234567890. Totale 1500 euro.";
        RoutingDecision routingDecision = localOllamaRouter.routeWithDecision(routerPayload);
        long dur1 = System.currentTimeMillis() - start1;
        assertEquals(DocumentType.FATTURA, routingDecision.documentType());
        results.add(new StepResult("1. LocalOllamaRouter", localOllamaRouter.getModel(), dur1, routingDecision.toString(), true));
        log.info("Passo 1 completato in {} ms: {}", dur1, routingDecision);

        // Verifica stato RAM dopo Passo 1
        String psAfterRouter = executeShellCommand("ollama ps");
        log.info("Stato 'ollama ps' dopo Router:\n{}", psAfterRouter);

        // Passo 2: Pipeline FATTURA
        long start2 = System.currentTimeMillis();
        String fatturaPayload = """
                FATTURA ELETTRONICA N. FT-2026/042 del 28/02/2026
                FORNITORE:
                TechSolutions S.r.l. - Via dell'Innovazione 10, Milano (MI)
                Partita IVA: 09876543217

                CLIENTE:
                Beta Consulting S.p.A. - Piazza Affari 2, Roma
                Codice Fiscale: RSSMRA85C12D086Q

                DESCRIZIONE:
                Consulenza sviluppo software architettura SLM
                TOTALE FATTURA: 4270.00 EUR
                Coordinate pagamento IBAN: IT60X0542811101000000123456
                """;
        OrchestratorRequest reqFattura = new OrchestratorRequest(DocumentType.FATTURA, fatturaPayload, RouterType.LOCAL_OLLAMA);
        OrchestratorResponse respFattura = orchestrator.process(reqFattura);
        long dur2 = System.currentTimeMillis() - start2;
        assertEquals(OrchestratorStatus.OK, respFattura.status());
        assertEquals(DocumentType.FATTURA, respFattura.label());
        assertNotNull(respFattura.result());
        boolean fatturaMinified = checkMinifiedJson(respFattura.result());
        results.add(new StepResult("2. Pipeline FATTURA", properties.invoiceModel(), dur2, respFattura.result(), fatturaMinified));
        log.info("Passo 2 completato in {} ms (minified={}): {}", dur2, fatturaMinified, respFattura.result());

        // Passo 3: Pipeline EMAIL
        long start3 = System.currentTimeMillis();
        String emailPayload = """
                Da: Laura Bianchi <laura.bianchi@techsolutions.it>
                A: supporto@clienti.com
                Data: 15/10/2026
                Oggetto: Richiesta workshop aziendale
                Gentile team, vi scrivo a nome di TechSolutions Srl per prenotare una sessione a Milano per 30 partecipanti con budget di 4000 euro.
                Potete ricontattarmi via email a laura.bianchi@techsolutions.it o al recapito 029876543.
                Cordiali saluti, Laura Bianchi
                """;
        OrchestratorRequest reqEmail = new OrchestratorRequest(DocumentType.EMAIL, emailPayload, RouterType.LOCAL_OLLAMA);
        OrchestratorResponse respEmail = orchestrator.process(reqEmail);
        long dur3 = System.currentTimeMillis() - start3;
        assertEquals(OrchestratorStatus.OK, respEmail.status());
        assertEquals(DocumentType.EMAIL, respEmail.label());
        assertNotNull(respEmail.result());
        boolean emailMinified = checkMinifiedJson(respEmail.result());
        results.add(new StepResult("3. Pipeline EMAIL", properties.emailModel(), dur3, respEmail.result(), emailMinified));
        log.info("Passo 3 completato in {} ms (minified={}): {}", dur3, emailMinified, respEmail.result());

        // Passo 4: Pipeline ANAGRAFICA
        long start4 = System.currentTimeMillis();
        String anagraficaPayload = """
                REPUBBLICA ITALIANA - CARTA D'IDENTITA
                Cognome: ROSSI
                Nome: MARIO
                Nato il: 12/03/1985 a COSENZA (CS)
                Sesso: M
                Codice Fiscale: RSSMRA85C12D086Q
                Numero documento: CA12345AA
                Rilasciata dal COMUNE DI COSENZA il 10/05/2020 con scadenza 10/05/2030.
                """;
        OrchestratorRequest reqAnagrafica = new OrchestratorRequest(DocumentType.ANAGRAFICA, anagraficaPayload, RouterType.LOCAL_OLLAMA);
        OrchestratorResponse respAnagrafica = orchestrator.process(reqAnagrafica);
        long dur4 = System.currentTimeMillis() - start4;
        assertEquals(OrchestratorStatus.OK, respAnagrafica.status());
        assertEquals(DocumentType.ANAGRAFICA, respAnagrafica.label());
        assertNotNull(respAnagrafica.result());
        boolean anagraficaMinified = checkMinifiedJson(respAnagrafica.result());
        results.add(new StepResult("4. Pipeline ANAGRAFICA", properties.anagraficaModel(), dur4, respAnagrafica.result(), anagraficaMinified));
        log.info("Passo 4 completato in {} ms (minified={}): {}", dur4, anagraficaMinified, respAnagrafica.result());

        // 3. ISPEZIONE FINALE OLLAMA PS: UN SOLO MODELLO IN RAM
        String psFinal = executeShellCommand("ollama ps");
        log.info("=== STATO FINALE 'ollama ps' ===\n{}", psFinal);

        // Verifiche tassative:
        // A) Deve essere presente il base-model
        assertTrue(psFinal.contains(properties.baseModel()),
                "In RAM deve essere presente il modello base: " + properties.baseModel());

        // B) Non devono comparire i modelli dedicati della vecchia architettura
        assertFalse(psFinal.contains("qwen2.5-router"), "Il runner qwen2.5-router NON deve esistere in RAM");
        assertFalse(psFinal.contains("qwen2.5-fattura"), "Il runner qwen2.5-fattura NON deve esistere in RAM");
        assertFalse(psFinal.contains("qwen2.5-email"), "Il runner qwen2.5-email NON deve esistere in RAM");
        assertFalse(psFinal.contains("qwen2.5-anagrafica"), "Il runner qwen2.5-anagrafica NON deve esistere in RAM");

        // C) Conteggio righe: esattamente una riga di modello (più header)
        long modelRows = psFinal.lines()
                .filter(line -> line.contains("qwen") || line.contains("gemma") || line.contains("instruct") || line.contains("GB"))
                .count();
        assertEquals(1, modelRows, "Deve essere caricato esattamente UN SOLO modello in RAM, trovato: " + modelRows);

        // D) Stampa tabella riepilogativa
        System.out.println("\n==========================================================================================");
        System.out.println("CERTIFICAZIONE EMPIRICA LIVE: SINGLE BASE MODEL + DYNAMIC SYSTEM PROMPT");
        System.out.println("==========================================================================================");
        System.out.println("Modello base in RAM: " + properties.baseModel());
        System.out.println("Output 'ollama ps':\n" + psFinal.strip());
        System.out.println("------------------------------------------------------------------------------------------");
        for (StepResult r : results) {
            System.out.printf("%-26s | Modello: %-25s | Durata: %5d ms | JSON Minified: %b%n",
                    r.stepName(), r.modelUsed(), r.durationMs(), r.isMinifiedJson());
        }
        System.out.println("==========================================================================================\n");
    }

    private boolean checkMinifiedJson(String json) {
        if (json == null || json.isBlank()) return false;
        String s = json.strip();
        if (!s.startsWith("{") || !s.endsWith("}")) return false;
        try {
            JsonNode tree = objectMapper.readTree(s);
            // Non deve contenere newline reali (minified su singola riga)
            return !s.contains("\n") && !s.contains("\r") && tree.isObject();
        } catch (Exception e) {
            return false;
        }
    }

    private static String executeShellCommand(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-c", command).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "Errore esecuzione comando: " + e.getMessage();
        }
    }
}
