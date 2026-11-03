package com.tirocinioaltrama.slmorchestrator.eval;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tirocinioaltrama.slmorchestrator.core.RoutingDecision;
import com.tirocinioaltrama.slmorchestrator.impl.router.LocalOllamaRouter;

/**
 * Runner per la Batteria 3: Valutazione del Router locale a 4 classi (FATTURA, EMAIL, ANAGRAFICA, ALTRO).
 * Esegue {@link LocalOllamaRouter} su:
 * - Held-Out (50 doc: 42 held-out + 8 ALTRO)
 * - Dev (60 doc: 20 FATTURA, 20 ANAGRAFICA, 20 EMAIL)
 */
public class RouterFrozenRunner {

    private static final String WARMUP_DOC = "Gentile supporto, confermo la ricezione della fattura e procedo al saldo.";

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of("risultati/test2/batteria3_router_4_classi");
        Path dsDir = Path.of("risultati/test2/datasets");
        Files.createDirectories(outDir);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ComparisonRunner runner = new ComparisonRunner(mapper, outDir);

        String model = args.length > 0 ? args[0] : "qwen2.5:3b-instruct-q8_0";
        LocalOllamaRouter router = new LocalOllamaRouter("http://localhost:11434", model, Duration.ofSeconds(30));

        System.out.printf("=== AVVIO BATTERIA 3: VALUTAZIONE ROUTER A 4 CLASSI (Modello: %s) ===%n", model);
        OllamaMonolithicClient.defaultInstance(mapper).stopOtherModels(model);
        router.routeWithDecision(WARMUP_DOC);

        // 1. Held-Out: 42 doc + 8 altro = 50 doc
        Path heldoutPath = dsDir.resolve("heldout_dataset_v1.json");
        Path altroPath = dsDir.resolve("heldout_router_altro_v1.json");
        ComparisonRunner.verifyDatasetSha256(heldoutPath, ComparisonRunner.EXPECTED_HELDOUT_DATASET_SHA256);
        ComparisonRunner.verifyDatasetSha256(altroPath, ComparisonRunner.EXPECTED_HELDOUT_ROUTER_ALTRO_SHA256);

        List<Map<String, Object>> heldoutDocs = runner.loadDataset(heldoutPath);
        List<Map<String, Object>> altroDocs = runner.loadDataset(altroPath);
        List<Map<String, Object>> allHeldoutRouterDocs = new ArrayList<>(50);
        allHeldoutRouterDocs.addAll(heldoutDocs);
        allHeldoutRouterDocs.addAll(altroDocs);

        Map<String, Object> heldoutResults = evaluateRouterSplit("heldout", allHeldoutRouterDocs, router, mapper, outDir.resolve("heldout"));

        // 2. Dev: 60 doc
        Path devPath = dsDir.resolve("dataset_60_v2.json");
        ComparisonRunner.verifyDatasetSha256(devPath, ComparisonRunner.EXPECTED_DEV_DATASET_SHA256);
        List<Map<String, Object>> devDocs = runner.loadDataset(devPath);

        Map<String, Object> devResults = evaluateRouterSplit("dev", devDocs, router, mapper, outDir.resolve("dev"));

        // 3. Genera tabella unificata TABELLA_BATTERIA3.md
        generateMarkdownTable(outDir, model, heldoutResults, devResults);
        System.out.println("=== BATTERIA 3 COMPLETATA CON SUCCESSO ===");
    }

    private static Map<String, Object> evaluateRouterSplit(
            String splitName,
            List<Map<String, Object>> docs,
            LocalOllamaRouter router,
            ObjectMapper mapper,
            Path splitOutDir
    ) throws Exception {
        Files.createDirectories(splitOutDir);
        List<String> categories = List.of("FATTURA", "EMAIL", "ANAGRAFICA", "ALTRO");
        Map<String, Map<String, Integer>> confusionMatrix = new LinkedHashMap<>();
        for (String actual : categories) {
            Map<String, Integer> row = new LinkedHashMap<>();
            for (String pred : categories) {
                row.put(pred, 0);
            }
            confusionMatrix.put(actual, row);
        }

        int correctCount = 0;
        List<Long> latenciesMs = new ArrayList<>();
        List<Map<String, Object>> itemDetails = new ArrayList<>();

        int i = 0;
        for (Map<String, Object> doc : docs) {
            i++;
            String docId = (String) doc.get("id");
            String payload = (String) doc.get("payload");
            String expectedType = ((String) doc.get("type")).toUpperCase(Locale.ROOT);

            long start = System.currentTimeMillis();
            RoutingDecision decision = router.routeWithDecision(payload);
            long latency = System.currentTimeMillis() - start;
            latenciesMs.add(latency);

            String predictedType = decision.documentType().name();
            boolean match = expectedType.equalsIgnoreCase(predictedType);
            if (match) {
                correctCount++;
            }

            if (confusionMatrix.containsKey(expectedType) && confusionMatrix.get(expectedType).containsKey(predictedType)) {
                int curr = confusionMatrix.get(expectedType).get(predictedType);
                confusionMatrix.get(expectedType).put(predictedType, curr + 1);
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", docId);
            detail.put("expected", expectedType);
            detail.put("predicted", predictedType);
            detail.put("match", match);
            detail.put("router_name", decision.routerName());
            detail.put("latency_ms", latency);
            itemDetails.add(detail);

            System.out.printf("  [ROUTER | %s] %d/%d (%s): atteso=%s, predetto=%s, match=%b (%d ms)%n",
                    splitName, i, docs.size(), docId, expectedType, predictedType, match, latency);
        }

        Collections.sort(latenciesMs);
        double meanLatency = latenciesMs.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long medianLatency = latenciesMs.get(latenciesMs.size() / 2);
        int p95Idx = (int) Math.ceil(0.95 * latenciesMs.size()) - 1;
        long p95Latency = latenciesMs.get(Math.max(0, Math.min(p95Idx, latenciesMs.size() - 1)));

        Map<String, Object> latencyStats = new LinkedHashMap<>();
        latencyStats.put("median_ms", medianLatency);
        latencyStats.put("mean_ms", Math.round(meanLatency * 10.0) / 10.0);
        latencyStats.put("p95_ms", p95Latency);

        double accuracyPct = ((double) correctCount / docs.size()) * 100.0;
        String wilson = WilsonScore.format(correctCount, docs.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("metadata", Map.of(
                "split", splitName,
                "total_documents", docs.size(),
                "model", router.getModel(),
                "evaluated_at", Instant.now().toString(),
                "git_commit", ComparisonRunner.getGitHeadCommit()
        ));
        summary.put("summary", Map.of(
                "total", docs.size(),
                "correct", correctCount,
                "accuracy_pct", Math.round(accuracyPct * 10.0) / 10.0,
                "accuracy_wilson", wilson
        ));
        summary.put("confusion_matrix", confusionMatrix);
        summary.put("latency_stats", latencyStats);
        summary.put("documents", itemDetails);

        Path sumFile = splitOutDir.resolve("router_summary.json");
        mapper.writeValue(sumFile.toFile(), summary);
        return summary;
    }

    @SuppressWarnings("unchecked")
    private static void generateMarkdownTable(
            Path outDir,
            String model,
            Map<String, Object> heldoutResults,
            Map<String, Object> devResults
    ) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# Batteria 3: Accuratezza e Latenza Router Locale a 4 Classi (`LocalOllamaRouter`)");
        lines.add("");
        lines.add("- **Commit Git (`freeze-1`)**: `" + ComparisonRunner.getGitHeadCommit() + "`");
        lines.add("- **Modello Router**: `" + model + "`");
        lines.add("- **Architettura**: Invocazione del router leggero a 4 classi (`FATTURA`, `EMAIL`, `ANAGRAFICA`, `ALTRO`) con prompt di sistema dedicato (`prompts/router-system.txt`).");
        lines.add("");

        appendSplitSection(lines, "1. Set Held-Out Out-of-Sample (50 documenti: 14 FATTURA, 14 ANAGRAFICA, 14 EMAIL, 8 ALTRO)", heldoutResults);
        appendSplitSection(lines, "2. Set di Sviluppo (60 documenti: 20 FATTURA, 20 ANAGRAFICA, 20 EMAIL)", devResults);

        Path tableFile = outDir.resolve("TABELLA_BATTERIA3.md");
        Files.write(tableFile, lines);
        System.out.println("Salvata " + tableFile.toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static void appendSplitSection(List<String> lines, String title, Map<String, Object> results) {
        Map<String, Object> sum = (Map<String, Object>) results.get("summary");
        Map<String, Object> lat = (Map<String, Object>) results.get("latency_stats");
        Map<String, Map<String, Integer>> cm = (Map<String, Map<String, Integer>>) results.get("confusion_matrix");

        lines.add("## " + title);
        lines.add("");
        lines.add("| Metrica | Valore |");
        lines.add("| :--- | :---: |");
        lines.add(String.format(Locale.ROOT, "| Documenti Totali | %s |", sum.get("total")));
        lines.add(String.format(Locale.ROOT, "| Instradamenti Corretti | %s / %s |", sum.get("correct"), sum.get("total")));
        lines.add(String.format(Locale.ROOT, "| Accuratezza di Routing (Wilson 95%%) | %s |", sum.get("accuracy_wilson")));
        lines.add(String.format(Locale.ROOT, "| Latenza Mediana | %s ms |", lat.get("median_ms")));
        lines.add(String.format(Locale.ROOT, "| Latenza Media | %s ms |", lat.get("mean_ms")));
        lines.add(String.format(Locale.ROOT, "| Latenza P95 | %s ms |", lat.get("p95_ms")));
        lines.add("");
        lines.add("### Matrice di Confusione");
        lines.add("");
        lines.add("| Tipo Effettivo \\ Predetto | FATTURA | EMAIL | ANAGRAFICA | ALTRO | Accuratezza per Classe |");
        lines.add("| :--- | :---: | :---: | :---: | :---: | :---: |");

        for (String actual : cm.keySet()) {
            Map<String, Integer> row = cm.get(actual);
            int tot = row.values().stream().mapToInt(Integer::intValue).sum();
            int corr = row.getOrDefault(actual, 0);
            String pct = tot > 0 ? String.format(Locale.ROOT, "%.1f%% (%d/%d)", (corr * 100.0 / tot), corr, tot) : "-";
            lines.add(String.format(Locale.ROOT, "| **%s** | %d | %d | %d | %d | %s |",
                    actual,
                    row.getOrDefault("FATTURA", 0),
                    row.getOrDefault("EMAIL", 0),
                    row.getOrDefault("ANAGRAFICA", 0),
                    row.getOrDefault("ALTRO", 0),
                    pct));
        }
        lines.add("");
    }
}
