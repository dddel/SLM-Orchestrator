package com.tirocinioaltrama.llmorchestrator.eval;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Runner centrale per i confronti sperimentali (Celle A..E):
 * - Verifica rigida SHA-256 del dataset
 * - Gestione ordini (naturale, seed1, seed2)
 * - Scrittura risultati con formato identico a eval/runs (docs/<id>.json e summary.json)
 * - Derivazione deterministica a posteriori di C (da B) ed E (da D)
 * - Misura dell'accettazione codici invalidi sul set avversariale
 */
public class ComparisonRunner {

    private static final Logger log = LoggerFactory.getLogger(ComparisonRunner.class);

    public static final String EXPECTED_DEV_DATASET_SHA256 = "fdb41afcf759c081d14d74144848ce3e4106c5a752441307d96bbc891d9d2290";
    public static final String EXPECTED_HELDOUT_DATASET_SHA256 = "7944defa038f6f6e8d2bf5a564fc7682578029988d00100854da86d053e120d8";
    public static final String EXPECTED_HELDOUT_ADVERSARIAL_SHA256 = "84f48999f475ab41f200279d2fdec6a2d17da586032600b0a53261a1d394dedd";
    public static final String EXPECTED_HELDOUT_ROUTER_ALTRO_SHA256 = "f167219afc1974a270044ad0d076a220521cf9389ab7b5bd6a6230a7ebc5dc64";

    private final ObjectMapper mapper;
    private final Path baseResultsDir;

    public ComparisonRunner(ObjectMapper mapper, Path baseResultsDir) {
        this.mapper = mapper != null ? mapper : new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.baseResultsDir = baseResultsDir != null ? baseResultsDir : Path.of("results/confronti");
    }

    public static String computeSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static void verifyDatasetSha256(Path datasetPath) throws Exception {
        verifyDatasetSha256(datasetPath, EXPECTED_DEV_DATASET_SHA256);
    }

    public static void verifyDatasetSha256(Path datasetPath, String expectedSha256) throws Exception {
        String actual = computeSha256(datasetPath);
        if (!expectedSha256.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("SHA-256 del dataset " + datasetPath + " (" + actual + ") diverso dall'atteso: " + expectedSha256);
        }
    }

    public List<Map<String, Object>> loadDataset(Path datasetPath) throws Exception {
        JsonNode root = mapper.readTree(datasetPath.toFile());
        List<Map<String, Object>> list = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode item : root) {
                list.add(mapper.convertValue(item, Map.class));
            }
        }
        return list;
    }

    public Map<String, List<String>> loadOrdini(Path ordiniPath) throws Exception {
        JsonNode root = mapper.readTree(ordiniPath.toFile());
        Map<String, List<String>> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            var f = fields.next();
            List<String> ids = new ArrayList<>();
            for (JsonNode idNode : f.getValue()) {
                ids.add(idNode.asText());
            }
            map.put(f.getKey(), ids);
        }
        return map;
    }

    public List<Map<String, Object>> orderDocuments(List<Map<String, Object>> docs, List<String> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return new ArrayList<>(docs);
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> d : docs) {
            byId.put((String) d.get("id"), d);
        }
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            if (byId.containsKey(id)) {
                ordered.add(byId.get(id));
            }
        }
        return ordered;
    }

    public static String getGitHeadCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").start();
            try (InputStream in = process.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                return new String(bytes, StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            return "non determinabile";
        }
    }

    public Map<String, Object> evaluateDocument(
            String docId,
            String docType,
            String template,
            String split,
            String status,
            Map<String, Object> extractedFlat,
            Map<String, Object> groundTruthRaw,
            Object rawResponse,
            int inferenceSteps
    ) {
        Map<String, Object> gtFlat = GroundTruthComparator.flattenJson(mapper.valueToTree(groundTruthRaw));

        Map<String, GroundTruthComparator.FieldComparisonResult> fieldMatches = new LinkedHashMap<>();
        List<Map<String, Object>> mismatches = new ArrayList<>();
        int correctCount = 0;

        for (Map.Entry<String, Object> entry : gtFlat.entrySet()) {
            String fieldPath = entry.getKey();
            Object gtVal = entry.getValue();

            boolean keyPresent = extractedFlat.containsKey(fieldPath);
            Object extVal = extractedFlat.get(fieldPath);

            GroundTruthComparator.FieldComparisonResult res =
                    GroundTruthComparator.compareField(fieldPath, extVal, gtVal, keyPresent);

            fieldMatches.put(fieldPath, res);

            if (res.match()) {
                correctCount++;
            } else {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("field", fieldPath);
                mm.put("key_present", keyPresent);
                mm.put("extracted", extVal);
                mm.put("ground_truth", gtVal);
                mm.put("normalized_extracted", res.normalizedExtracted());
                mm.put("normalized_ground_truth", res.normalizedGroundTruth());
                mm.put("field_class", res.fieldClass().name());
                mismatches.add(mm);
            }
        }

        int totalFields = gtFlat.size();
        boolean allMatched = (correctCount == totalFields);

        GroundTruthComparator.DocumentClassification docClass =
                GroundTruthComparator.classifyDocument(status, fieldMatches, totalFields, correctCount);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", docId);
        out.put("type", docType);
        out.put("template", template);
        out.put("split", split != null ? split : (docId != null && docId.startsWith("HO_") ? "heldout" : "dev"));
        out.put("category", docClass.category());
        out.put("c2_subcode", docClass.c2Subcode());
        out.put("c5_subcode", null);
        out.put("category_description", docClass.categoryDescription());
        out.put("status", status);
        out.put("http_status", 200);
        out.put("all_fields_match", allMatched);
        out.put("total_fields", totalFields);
        out.put("correct_fields", correctCount);
        out.put("n_inference_steps", inferenceSteps);

        Map<String, Object> fmJson = new LinkedHashMap<>();
        for (var e : fieldMatches.entrySet()) {
            var f = e.getValue();
            Map<String, Object> fInfo = new LinkedHashMap<>();
            fInfo.put("match", f.match());
            fInfo.put("key_present", f.keyPresent());
            fInfo.put("extracted", f.extracted());
            fInfo.put("normalized_extracted", f.normalizedExtracted());
            fInfo.put("ground_truth", f.groundTruth());
            fInfo.put("normalized_ground_truth", f.normalizedGroundTruth());
            fInfo.put("field_class", f.fieldClass().name());
            fmJson.put(e.getKey(), fInfo);
        }
        out.put("field_matches", fmJson);
        out.put("mismatches", mismatches);
        out.put("raw_response", rawResponse);

        return out;
    }

    public void saveRunResults(
            String cella,
            String ordine,
            Map<String, Object> metadata,
            List<Map<String, Object>> evaluatedDocs
    ) throws Exception {
        Path runDir = baseResultsDir.resolve(cella).resolve(ordine);
        Path docsDir = runDir.resolve("docs");
        Files.createDirectories(docsDir);

        Map<String, Integer> catCounts = new LinkedHashMap<>();
        catCounts.put("C1", 0);
        catCounts.put("C2", 0);
        catCounts.put("C3", 0);
        catCounts.put("C4", 0);
        catCounts.put("C5", 0);

        Map<String, Integer> c2SubCounts = new LinkedHashMap<>();
        c2SubCounts.put("C2a", 0);
        c2SubCounts.put("C2b", 0);
        c2SubCounts.put("C2c", 0);

        int totalFields = 0;
        int correctFields = 0;

        Map<String, Object> docsMap = new LinkedHashMap<>();

        for (Map<String, Object> d : evaluatedDocs) {
            String docId = (String) d.get("id");
            String cat = (String) d.get("category");
            catCounts.put(cat, catCounts.getOrDefault(cat, 0) + 1);

            String sub = (String) d.get("c2_subcode");
            if (sub != null && c2SubCounts.containsKey(sub)) {
                c2SubCounts.put(sub, c2SubCounts.get(sub) + 1);
            }

            int tf = (int) d.get("total_fields");
            int cf = (int) d.get("correct_fields");
            totalFields += tf;
            correctFields += cf;

            docsMap.put(docId, d);

            Path docFile = docsDir.resolve(docId + ".json");
            mapper.writeValue(docFile.toFile(), d);
        }

        int totalDocs = evaluatedDocs.size();

        Map<String, String> wilsonMap = new LinkedHashMap<>();
        for (var e : catCounts.entrySet()) {
            wilsonMap.put(e.getKey(), WilsonScore.format(e.getValue(), totalDocs));
        }

        Map<String, Object> fieldsSummary = new LinkedHashMap<>();
        fieldsSummary.put("total_fields", totalFields);
        fieldsSummary.put("correct_fields", correctFields);
        fieldsSummary.put("accuracy_wilson", WilsonScore.format(correctFields, totalFields));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("metadata", metadata);
        summary.put("classification_counts", catCounts);
        summary.put("c2_subcode_counts", c2SubCounts);
        summary.put("classification_wilson", wilsonMap);
        summary.put("fields_summary", fieldsSummary);
        summary.put("documents", docsMap);

        Path summaryFile = runDir.resolve("summary.json");
        mapper.writeValue(summaryFile.toFile(), summary);
        log.info("Run salvato con successo in {}: {} docs, {}/{} campi corretti ({})",
                runDir, totalDocs, correctFields, totalFields, fieldsSummary.get("accuracy_wilson"));
    }

    public void derivePostHocRun(
            String sourceCella, // "B" o "D"
            String targetCella, // "C" o "E"
            String ordine,
            List<Map<String, Object>> datasetDocs
    ) throws Exception {
        Path sourceDir = baseResultsDir.resolve(sourceCella).resolve(ordine);
        Path targetDir = baseResultsDir.resolve(targetCella).resolve(ordine);

        Path sourceSummary = sourceDir.resolve("summary.json");
        if (!Files.exists(sourceSummary)) {
            throw new IllegalStateException("Summary sorgente non trovato per derivazione: " + sourceSummary);
        }

        JsonNode srcSummaryNode = mapper.readTree(sourceSummary.toFile());
        JsonNode srcMeta = srcSummaryNode.path("metadata");

        Map<String, Object> targetMeta = mapper.convertValue(srcMeta, Map.class);
        targetMeta.put("cella", targetCella);
        targetMeta.put("derived_from_cella", sourceCella);
        targetMeta.put("derived_at", Instant.now().toString());
        targetMeta.put("post_processing_guards_enabled", true);
        targetMeta.put("coherence_enabled", true);
        targetMeta.put("formal_validators_enabled", true);

        Map<String, Map<String, Object>> dataMap = new LinkedHashMap<>();
        for (Map<String, Object> d : datasetDocs) {
            dataMap.put((String) d.get("id"), d);
        }

        PostHocGuardrails guardrails = new PostHocGuardrails();
        List<Map<String, Object>> derivedEvaluatedDocs = new ArrayList<>();

        JsonNode srcDocs = srcSummaryNode.path("documents");
        Iterator<Map.Entry<String, JsonNode>> iter = srcDocs.fields();

        int correctClassifications = 0;
        int totalDocs = 0;

        while (iter.hasNext()) {
            totalDocs++;
            var entry = iter.next();
            String docId = entry.getKey();
            JsonNode docNode = entry.getValue();

            Map<String, Object> originalDocData = dataMap.get(docId);
            String trueType = (String) originalDocData.get("type");
            String payload = (String) originalDocData.get("payload");
            Map<String, Object> groundTruth = (Map<String, Object>) originalDocData.get("ground_truth");

            String rawOutput = docNode.path("raw_response").has("result")
                    ? docNode.path("raw_response").path("result").asText("{}")
                    : docNode.path("raw_response").path("raw_output").asText(docNode.path("raw_response").asText("{}"));

            PostHocGuardrails.PostHocResult phRes = guardrails.apply(rawOutput, payload, trueType);

            if (phRes.classificationMatch()) {
                correctClassifications++;
            }

            Map<String, Object> phRawResponse = new LinkedHashMap<>();
            phRawResponse.put("status", phRes.status());
            phRawResponse.put("classifiedType", phRes.classifiedType());
            phRawResponse.put("classificationMatch", phRes.classificationMatch());
            phRawResponse.put("guardrailsLatencyMs", phRes.guardrailsLatencyMs());
            phRawResponse.put("errors", phRes.validationErrors());
            phRawResponse.put("processedOutput", phRes.processedNode());
            phRawResponse.put("originalRawResponse", docNode.get("raw_response"));

            Map<String, Object> evaluated = evaluateDocument(
                    docId,
                    trueType,
                    (String) originalDocData.get("template"),
                    (String) originalDocData.getOrDefault("split", (docId != null && docId.startsWith("HO_") ? "heldout" : "dev")),
                    phRes.status(),
                    phRes.flatExtracted(),
                    groundTruth,
                    phRawResponse,
                    1
            );

            derivedEvaluatedDocs.add(evaluated);
        }

        targetMeta.put("type_classification_accuracy", String.format(Locale.ROOT, "%d/%d (%.1f%%)",
                correctClassifications, totalDocs, ((double) correctClassifications / totalDocs) * 100.0));

        saveRunResults(targetCella, ordine, targetMeta, derivedEvaluatedDocs);
    }

    public record AdversarialMetrics(
            int totalAdversarialDocs,
            int acceptedFalseDocs,
            double acceptanceRate,
            String acceptanceRateWilson
    ) {}

    public AdversarialMetrics evaluateAdversarialAcceptance(List<Map<String, Object>> evaluatedDocs) {
        int total = evaluatedDocs.size();
        int accepted = 0;
        for (Map<String, Object> d : evaluatedDocs) {
            String status = (String) d.get("status");
            if ("OK".equalsIgnoreCase(status)) {
                accepted++;
            }
        }
        double rate = total > 0 ? ((double) accepted / total) * 100.0 : 0.0;
        String wilson = WilsonScore.format(accepted, total);
        return new AdversarialMetrics(total, accepted, rate, wilson);
    }
}
