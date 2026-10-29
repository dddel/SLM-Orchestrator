package com.tirocinioaltrama.llmorchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Tag("live")
public class ParityComparatorLiveTest {

    private static final Logger log = LoggerFactory.getLogger(ParityComparatorLiveTest.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<String> RUNS_16 = List.of(
            "rep1_natural", "rep2_seed1", "rep3_seed2",
            "e1_rep1_natural", "e1_rep2_seed1", "e1_rep3_seed2",
            "e1c_rep1_natural", "e1c_rep2_seed1", "e1c_rep3_seed2",
            "e1_e1c_rep1_natural", "e1_e1c_rep2_seed1", "e1_e1c_rep3_seed2",
            "final_v2_rep1", "final_v2_rep2", "final_v2_rep3",
            "ablation_v2_no_guards"
    );

    record ParityDifference(
            String run,
            String docId,
            String field,
            Object pythonValue,
            Object javaValue
    ) {}

    private Path resolveRunsDir() {
        for (String candidate : List.of("eval/runs", "obsoleti/2026-10-23/eval/runs", "archive/2026-10-23/eval/runs")) {
            Path p = Path.of(candidate);
            if (Files.exists(p)) {
                return p;
            }
        }
        return Path.of("obsoleti/2026-10-23/eval/runs");
    }

    @Test
    @DisplayName("Test di Parità Rigorosa: 16 run storici (960 doc), 0 differenze su campi e categorie C1..C5/C2a..C2c")
    void testParityAcrossAllHistoricalRuns() throws Exception {
        Path runsDir = resolveRunsDir();
        assertTrue(Files.exists(runsDir), "Directory eval/runs non trovata");

        List<ParityDifference> differences = new ArrayList<>();
        int totalDocsChecked = 0;
        int totalFieldsChecked = 0;

        for (String runName : RUNS_16) {
            Path runPath = runsDir.resolve(runName);
            Path docsDir = runPath.resolve("docs");
            assertTrue(Files.exists(docsDir), "Docs dir non trovata per run: " + runName);

            File[] docFiles = docsDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            assertNotNull(docFiles, "Nessun file json in " + docsDir);
            Arrays.sort(docFiles);

            for (File docFile : docFiles) {
                totalDocsChecked++;
                JsonNode root = mapper.readTree(docFile);

                String docId = root.path("id").asText();
                String pyCategory = root.path("category").asText();
                String pyC2Subcode = root.hasNonNull("c2_subcode") ? root.path("c2_subcode").asText() : null;
                String status = root.path("status").asText();
                int totalFields = root.path("total_fields").asInt();
                int pyCorrectFields = root.path("correct_fields").asInt();

                JsonNode fieldMatches = root.path("field_matches");
                if (fieldMatches.isEmpty()) {
                    // Caso C5 (es. syntax error su ANA_19 in rep2_seed1)
                    if (!"C5".equals(pyCategory)) {
                        differences.add(new ParityDifference(runName, docId, "CATEGORY", pyCategory, "C5_EMPTY"));
                    }
                    continue;
                }

                Map<String, GroundTruthComparator.FieldComparisonResult> javaFieldResults = new LinkedHashMap<>();
                int javaCorrectFields = 0;

                var fieldsIter = fieldMatches.fields();
                while (fieldsIter.hasNext()) {
                    var entry = fieldsIter.next();
                    String fieldPath = entry.getKey();
                    JsonNode fmNode = entry.getValue();

                    totalFieldsChecked++;

                    boolean pyMatch = fmNode.path("match").asBoolean();
                    boolean keyPresent = fmNode.path("key_present").asBoolean(true);

                    Object extVal = extractValue(fmNode.get("extracted"));
                    Object gtVal = extractValue(fmNode.get("ground_truth"));

                    GroundTruthComparator.FieldComparisonResult res =
                            GroundTruthComparator.compareField(fieldPath, extVal, gtVal, keyPresent);

                    javaFieldResults.put(fieldPath, res);

                    if (res.match()) {
                        javaCorrectFields++;
                    }

                    if (res.match() != pyMatch) {
                        differences.add(new ParityDifference(
                                runName, docId, fieldPath + ".match", pyMatch, res.match()
                        ));
                    }
                }

                if (javaCorrectFields != pyCorrectFields) {
                    differences.add(new ParityDifference(
                            runName, docId, "correct_fields", pyCorrectFields, javaCorrectFields
                    ));
                }

                GroundTruthComparator.DocumentClassification docClass =
                        GroundTruthComparator.classifyDocument(status, javaFieldResults, totalFields, javaCorrectFields);

                if (!Objects.equals(docClass.category(), pyCategory)) {
                    differences.add(new ParityDifference(
                            runName, docId, "category", pyCategory, docClass.category()
                    ));
                }

                if (root.has("c2_subcode") && !Objects.equals(docClass.c2Subcode(), pyC2Subcode)) {
                    differences.add(new ParityDifference(
                            runName, docId, "c2_subcode", pyC2Subcode, docClass.c2Subcode()
                    ));
                }
            }
        }

        log.info("Test di Parità Completato: Documenti={}/960, Campi={}, Differenze={}",
                totalDocsChecked, totalFieldsChecked, differences.size());

        if (!differences.isEmpty()) {
            System.err.println("=== DIFFERENZE RILEVATE (" + differences.size() + ") ===");
            for (ParityDifference diff : differences) {
                System.err.printf("Run: %s | Doc: %s | Campo: %s | Python: %s | Java: %s%n",
                        diff.run(), diff.docId(), diff.field(), diff.pythonValue(), diff.javaValue());
            }
        }

        assertEquals(0, differences.size(), "Il comparatore Java presenta discrepanze rispetto al comparatore Python!");
    }

    @Test
    @DisplayName("Test di Parità Wilson al 95%: Verifica quadratura con tutti i 16 summary.json")
    void testWilsonScoreAcrossAllSummaries() throws Exception {
        Path runsDir = resolveRunsDir();

        for (String runName : RUNS_16) {
            Path summaryPath = runsDir.resolve(runName).resolve("summary.json");
            assertTrue(Files.exists(summaryPath), "Summary non trovato: " + summaryPath);

            JsonNode root = mapper.readTree(summaryPath.toFile());
            int totalDocs = root.path("metadata").path("total_documents").asInt();

            JsonNode counts = root.path("classification_counts");
            JsonNode wilsons = root.path("classification_wilson");

            var cIter = counts.fields();
            while (cIter.hasNext()) {
                var entry = cIter.next();
                String cat = entry.getKey();
                int count = entry.getValue().asInt();
                String expected = wilsons.path(cat).asText();
                String calculated = WilsonScore.format(count, totalDocs);
                assertEquals(expected, calculated, "Discrepanza Wilson per " + runName + " cat " + cat);
            }

            JsonNode fs = root.path("fields_summary");
            if (fs.has("accuracy_wilson")) {
                int totalFields = fs.path("total_fields").asInt();
                int correctFields = fs.path("correct_fields").asInt();
                String expectedAcc = fs.path("accuracy_wilson").asText();
                String calcAcc = WilsonScore.format(correctFields, totalFields);
                assertEquals(expectedAcc, calcAcc, "Discrepanza Wilson accuracy per " + runName);
            }
        }
    }

    @Test
    @DisplayName("Test di Integrità Held-Out (Passo 5): 0 discrepanze tra summary.json e docs/*.json su tutte le celle")
    void testHeldOutResultsIntegrityAndZeroDiscrepancy() throws Exception {
        List<Path> heldoutRuns = List.of(
                Path.of("results/heldout/A/naturale"),
                Path.of("results/heldout/A_7B/naturale"),
                Path.of("results/heldout/B/naturale"),
                Path.of("results/heldout/C/naturale"),
                Path.of("results/heldout/adversarial/A/adv"),
                Path.of("results/heldout/adversarial/A_7B/adv"),
                Path.of("results/heldout/adversarial/B/adv"),
                Path.of("results/heldout/adversarial/C/adv")
        );

        int totalHeldOutDocs = 0;
        int totalHeldOutFields = 0;

        for (Path runPath : heldoutRuns) {
            Path summaryPath = runPath.resolve("summary.json");
            Path docsDir = runPath.resolve("docs");
            assertTrue(Files.exists(summaryPath), "Summary mancante: " + summaryPath);
            assertTrue(Files.exists(docsDir), "Docs dir mancante: " + docsDir);

            JsonNode summary = mapper.readTree(summaryPath.toFile());
            int expectedDocs = summary.path("documents").size();
            int expectedTotalFields = summary.path("fields_summary").path("total_fields").asInt();
            int expectedCorrectFields = summary.path("fields_summary").path("correct_fields").asInt();

            File[] docFiles = docsDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
            assertNotNull(docFiles, "Nessun file json in " + docsDir);
            Arrays.sort(docFiles);
            assertEquals(expectedDocs, docFiles.length, "Numero documenti errato in " + runPath);

            int sumTotalFields = 0;
            int sumCorrectFields = 0;
            Map<String, Integer> catCounts = new LinkedHashMap<>();
            for (String c : List.of("C1", "C2", "C3", "C4", "C5")) {
                catCounts.put(c, 0);
            }
            Map<String, Integer> c2SubCounts = new LinkedHashMap<>();
            for (String s : List.of("C2a", "C2b", "C2c")) {
                c2SubCounts.put(s, 0);
            }

            for (File docFile : docFiles) {
                totalHeldOutDocs++;
                JsonNode doc = mapper.readTree(docFile);
                int docTotal = doc.path("total_fields").asInt();
                int docCorrect = doc.path("correct_fields").asInt();
                sumTotalFields += docTotal;
                sumCorrectFields += docCorrect;
                totalHeldOutFields += docTotal;

                String cat = doc.path("category").asText();
                catCounts.put(cat, catCounts.getOrDefault(cat, 0) + 1);
                if ("C2".equals(cat) && doc.hasNonNull("c2_subcode")) {
                    String sub = doc.path("c2_subcode").asText();
                    c2SubCounts.put(sub, c2SubCounts.getOrDefault(sub, 0) + 1);
                }

                JsonNode fm = doc.path("field_matches");
                var fieldsIter = fm.fields();
                int recomputedDocCorrect = 0;
                int recomputedDocTotal = 0;
                while (fieldsIter.hasNext()) {
                    var entry = fieldsIter.next();
                    JsonNode fNode = entry.getValue();
                    recomputedDocTotal++;
                    if (fNode.path("match").asBoolean()) {
                        recomputedDocCorrect++;
                    }
                    String fc = fNode.path("field_class").asText();
                    assertTrue(List.of("CORRETTO", "NULL_SU_VALORE_PRESENTE", "VALORE_SU_NULL_ATTESO", "VALORE_ERRATO", "STRINGA_VUOTA").contains(fc),
                            "Classe campo non riconosciuta: " + fc);
                }
                assertEquals(docTotal, recomputedDocTotal,
                        "Discrepanza total_fields nel documento " + docFile.getName());
                assertEquals(docCorrect, recomputedDocCorrect,
                        "Discrepanza correct_fields nel documento " + docFile.getName());
            }

            assertEquals(expectedTotalFields, sumTotalFields, "Discrepanza total_fields in " + runPath);
            assertEquals(expectedCorrectFields, sumCorrectFields, "Discrepanza correct_fields in " + runPath);

            JsonNode summaryCats = summary.path("classification_counts");
            for (var entry : catCounts.entrySet()) {
                assertEquals(summaryCats.path(entry.getKey()).asInt(), entry.getValue(),
                        "Discrepanza categoria " + entry.getKey() + " in " + runPath);
            }

            JsonNode summaryC2Sub = summary.path("c2_subcode_counts");
            for (var entry : c2SubCounts.entrySet()) {
                assertEquals(summaryC2Sub.path(entry.getKey()).asInt(), entry.getValue(),
                        "Discrepanza sottocodice " + entry.getKey() + " in " + runPath);
            }
        }

        Path routerPath = Path.of("results/heldout/router/router_heldout_results.json");
        assertTrue(Files.exists(routerPath), "Risultati router held-out mancanti");
        JsonNode routerJson = mapper.readTree(routerPath.toFile());
        assertEquals(50, routerJson.path("summary").path("total").asInt());
        assertEquals(49, routerJson.path("summary").path("correct").asInt());

        log.info("Test di Integrità Held-Out Completato: {} doc, {} campi, 0 discrepanze!",
                totalHeldOutDocs, totalHeldOutFields);
    }

    private Object extractValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return node.asText();
    }

    private void assertNotNull(Object obj, String msg) {
        assertTrue(obj != null, msg);
    }
}
