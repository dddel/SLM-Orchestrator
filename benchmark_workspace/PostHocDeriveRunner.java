package com.tirocinioaltrama.slmorchestrator.eval;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Runner puro in Java che invoca direttamente {@link ComparisonRunner#derivePostHocRun}
 * per derivare la Cella C (7B_MONOLITICO_POSTHOC_GUARDRAIL) dalla Cella B (7B_MONOLITICO_SENZA_GUARDRAIL).
 */
public class PostHocDeriveRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Uso: PostHocDeriveRunner <sourceCella> <targetCella> <splitName> <datasetPath> <outputBaseDir>");
            System.exit(1);
        }

        String sourceCella = args[0];
        String targetCella = args[1];
        String splitName = args[2];
        Path datasetPath = Path.of(args[3]);
        Path outputBaseDir = Path.of(args[4]);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ComparisonRunner runner = new ComparisonRunner(mapper, outputBaseDir);

        List<Map<String, Object>> docs = runner.loadDataset(datasetPath);
        System.out.printf("=== DERIVAZIONE POST-HOC [%s -> %s | %s] su %s (%d documenti) ===%n",
                sourceCella, targetCella, splitName, datasetPath, docs.size());

        runner.derivePostHocRun(sourceCella, targetCella, splitName, docs);
    }
}
