package com.tirocinioaltrama.slmorchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Tag("live")
public class ComparisonWorkflowMockLiveTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Verifica integrità SHA-256 del dataset dev data/dataset_60_v2.json")
    void testDatasetSha256Integrity() throws Exception {
        Path datasetPath = Path.of("data/dataset_60_v2.json");
        assertTrue(Files.exists(datasetPath), "Dataset dev non trovato!");
        String sha = ComparisonRunner.computeSha256(datasetPath);
        assertEquals(ComparisonRunner.EXPECTED_DEV_DATASET_SHA256, sha, "SHA-256 non corrispondente!");
    }

    @Test
    @DisplayName("Test completo del flusso runner con client finti: esecuzione B, derivazione C, formato file e adversarial")
    void testFullWorkflowWithMockClients() throws Exception {
        Path testResultsDir = Path.of("results/confronti/test_mock");
        ComparisonRunner runner = new ComparisonRunner(mapper, testResultsDir);

        // 1. Mock dataset di 3 documenti (Fattura, Email, Anagrafica)
        Map<String, Object> doc1 = new LinkedHashMap<>();
        doc1.put("id", "MOCK_FAT_01");
        doc1.put("type", "FATTURA");
        doc1.put("template", "MOCK_T1");
        doc1.put("split", "dev");
        doc1.put("payload", "Fattura n. 100 del 01/01/2026 Fornitore Alpha P.IVA 01234567890 Cliente Beta CF RSSMRA85C12D086Q Totale 100 euro IBAN IT60X0542811101000000123456");
        doc1.put("ground_truth", Map.of(
                "numeroFattura", "100",
                "dataFattura", "2026-01-01",
                "fornitore", Map.of("denominazione", "Alpha", "partitaIva", "01234567890"),
                "cliente", Map.of("denominazione", "Beta", "codiceFiscale", "RSSMRA85C12D086Q"),
                "importoTotale", 100.0,
                "iban", "IT60X0542811101000000123456"
        ));

        Map<String, Object> doc2 = new LinkedHashMap<>();
        doc2.put("id", "MOCK_EML_01");
        doc2.put("type", "EMAIL");
        doc2.put("template", "MOCK_T2");
        doc2.put("split", "dev");
        doc2.put("payload", "Da: Mario <mario@test.com> A: info@test.com Oggetto: Test");
        doc2.put("ground_truth", Map.of(
                "mittente", "Mario",
                "email", "mario@test.com"
        ));

        List<Map<String, Object>> mockDocs = List.of(doc1, doc2);

        // 2. Mock output grezzo monolitico (Busta JSON)
        String mockRawOutputFat = """
                {
                  "tipoDocumento": "FATTURA",
                  "fattura": {
                    "numeroFattura": "100",
                    "dataFattura": "01/01/2026",
                    "fornitore": { "denominazione": "Alpha", "partitaIva": "01234567890" },
                    "cliente": { "denominazione": "Beta", "codiceFiscale": "RSSMRA85C12D086Q" },
                    "importoTotale": 100.0,
                    "iban": "IT60X0542811101000000123456"
                  },
                  "email": null,
                  "anagrafica": null,
                  "altro": null
                }
                """;

        String mockRawOutputEml = """
                {
                  "tipoDocumento": "EMAIL",
                  "fattura": null,
                  "email": {
                    "mittente": "Mario",
                    "email": "mario@test.com",
                    "telefono": null,
                    "azienda": null,
                    "oggetto": "Test",
                    "citta": null,
                    "numeroPartecipanti": null,
                    "budgetEuro": null,
                    "dataEvento": null
                  },
                  "anagrafica": null,
                  "altro": null
                }
                """;

        // Simula Cella B (monolitico senza guardrail)
        List<Map<String, Object>> cellBDocs = new ArrayList<>();
        Map<String, Object> evalB1 = runner.evaluateDocument(
                "MOCK_FAT_01", "FATTURA", "MOCK_T1", "dev", "OK",
                GroundTruthComparator.flattenJson(mapper.readTree(mockRawOutputFat).get("fattura")),
                (Map<String, Object>) doc1.get("ground_truth"),
                Map.of("result", mockRawOutputFat, "latencyMs", 1500),
                1
        );
        Map<String, Object> evalB2 = runner.evaluateDocument(
                "MOCK_EML_01", "EMAIL", "MOCK_T2", "dev", "OK",
                GroundTruthComparator.flattenJson(mapper.readTree(mockRawOutputEml).get("email")),
                (Map<String, Object>) doc2.get("ground_truth"),
                Map.of("result", mockRawOutputEml, "latencyMs", 1200),
                1
        );
        cellBDocs.add(evalB1);
        cellBDocs.add(evalB2);

        Map<String, Object> metaB = new LinkedHashMap<>();
        metaB.put("cella", "B");
        metaB.put("ordine", "naturale");
        metaB.put("model_id", "mock-qwen-7b");
        metaB.put("git_commit", ComparisonRunner.getGitHeadCommit());
        metaB.put("evaluated_at", Instant.now().toString());

        runner.saveRunResults("B", "naturale", metaB, cellBDocs);

        Path bSummary = testResultsDir.resolve("B/naturale/summary.json");
        assertTrue(Files.exists(bSummary), "summary.json per cella B non generato!");
        assertTrue(Files.exists(testResultsDir.resolve("B/naturale/docs/MOCK_FAT_01.json")));

        // 3. Simula derivazione a posteriori di C (da B)
        runner.derivePostHocRun("B", "C", "naturale", mockDocs);

        Path cSummary = testResultsDir.resolve("C/naturale/summary.json");
        assertTrue(Files.exists(cSummary), "summary.json per cella C non generato!");
        assertTrue(Files.exists(testResultsDir.resolve("C/naturale/docs/MOCK_FAT_01.json")));

        JsonNode cSummaryNode = mapper.readTree(cSummary.toFile());
        assertEquals("C", cSummaryNode.path("metadata").path("cella").asText());
        assertEquals("B", cSummaryNode.path("metadata").path("derived_from_cella").asText());
        assertEquals("2/2 (100.0%)", cSummaryNode.path("metadata").path("type_classification_accuracy").asText());

        // 4. Test metriche avversariali
        var advMetrics = runner.evaluateAdversarialAcceptance(cellBDocs);
        assertEquals(2, advMetrics.totalAdversarialDocs());
        assertEquals(2, advMetrics.acceptedFalseDocs()); // Entrambi OK = 100% false accepted
        assertEquals(100.0, advMetrics.acceptanceRate());
    }
}
