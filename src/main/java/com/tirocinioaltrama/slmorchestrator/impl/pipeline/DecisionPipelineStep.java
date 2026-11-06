package com.tirocinioaltrama.slmorchestrator.impl.pipeline;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.slmorchestrator.core.model.ModelClient;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelRequest;
import com.tirocinioaltrama.slmorchestrator.core.model.ModelResponse;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineContext;
import com.tirocinioaltrama.slmorchestrator.core.pipeline.PipelineStep;
import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard.CatalogMembershipGuard;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Passo di pipeline specializzato in decisioni discrete categoriche (Single-Token Readout)
 * ad altissima velocità e zero allucinazioni tramite modelli decisionali locali (es. APUS Reflex 4B).
 * <p>
 * Mappa dinamicamente i candidati estratti dal catalogo (es. da company_roles_lookup) nelle opzioni
 * discrete A..P, invoca il modello decisionale con vincolo a 1 token (num_predict: 1, temp: 0.0) e
 * inietta la scelta simbolica garantita nel contesto JSON della pipeline.
 */
public class DecisionPipelineStep implements PipelineStep {

    private static final Logger log = LoggerFactory.getLogger(DecisionPipelineStep.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final String DEFAULT_REFLEX_MODEL = "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M";
    private static final char[] OPTION_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private final String id;
    private final ModelClient modelClient;
    private final String model;
    private final String candidatesSource;
    private final String targetField;
    private final String systemPrompt;
    private final boolean shuffleCandidates;
    private final java.util.Random random;
    private final int kPermutations;
    private final double minAgreementRatio;

    public record CandidateOption(
            char letter,
            String key,
            String role,
            String person,
            String email,
            String label
    ) {}

    public DecisionPipelineStep(String id, ModelClient modelClient, String model) {
        this(id, modelClient, model, "company_roles", "destinatario", null, false, null, 1, 0.5);
    }

    public DecisionPipelineStep(
            String id,
            ModelClient modelClient,
            String model,
            String candidatesSource,
            String targetField,
            String systemPrompt
    ) {
        this(id, modelClient, model, candidatesSource, targetField, systemPrompt, false, null, 1, 0.5);
    }

    public DecisionPipelineStep(
            String id,
            ModelClient modelClient,
            String model,
            String candidatesSource,
            String targetField,
            String systemPrompt,
            boolean shuffleCandidates,
            java.util.Random random
    ) {
        this(id, modelClient, model, candidatesSource, targetField, systemPrompt, shuffleCandidates, random, 1, 0.5);
    }

    public DecisionPipelineStep(
            String id,
            ModelClient modelClient,
            String model,
            String candidatesSource,
            String targetField,
            String systemPrompt,
            boolean shuffleCandidates,
            java.util.Random random,
            int kPermutations,
            double minAgreementRatio
    ) {
        this.id = (id != null && !id.isBlank()) ? id : "decision_step";
        this.modelClient = modelClient;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_REFLEX_MODEL;
        this.candidatesSource = (candidatesSource != null && !candidatesSource.isBlank()) ? candidatesSource : "company_roles";
        this.targetField = (targetField != null && !targetField.isBlank()) ? targetField : "destinatario";
        this.systemPrompt = systemPrompt;
        this.shuffleCandidates = shuffleCandidates;
        this.random = random != null ? random : new java.util.Random();
        this.kPermutations = Math.max(1, kPermutations);
        this.minAgreementRatio = (minAgreementRatio > 0.0 && minAgreementRatio <= 1.0) ? minAgreementRatio : 0.5;
    }

    public int getKPermutations() {
        return kPermutations;
    }

    public double getMinAgreementRatio() {
        return minAgreementRatio;
    }

    public boolean isShuffleCandidates() {
        return shuffleCandidates;
    }

    public String getId() {
        return id;
    }

    public String getCandidatesSource() {
        return candidatesSource;
    }

    public String getTargetField() {
        return targetField;
    }

    @Override
    public String getName() {
        return "DecisionPipelineStep[" + id + "]";
    }

    @Override
    public ValidationResult execute(PipelineContext context) {
        if (context == null) {
            String msg = "PipelineContext nullo per lo step decisionale '" + id + "'.";
            return ValidationResult.withFieldErrors(List.of(new FieldError(id, "decision", ValidationErrorCode.INFERENCE_ERROR, msg)), List.of());
        }

        Object rawCandidates = context.getAttribute(candidatesSource);
        if (rawCandidates == null) {
            rawCandidates = context.getAttribute("company_roles_lookup");
        }

        if (rawCandidates == null) {
            String msg = "Sorgente candidati '" + candidatesSource + "' non presente nel contesto per lo step decisionale '" + id + "'.";
            log.error(msg);
            return ValidationResult.withFieldErrors(List.of(new FieldError(id, "decision", ValidationErrorCode.INFERENCE_ERROR, msg)), List.of());
        }

        int effectiveK = Math.max(1, kPermutations);
        java.util.Map<String, Integer> voteCounts = new java.util.LinkedHashMap<>();
        java.util.Map<String, CandidateOption> keyToOption = new java.util.HashMap<>();
        long totalInferenceLatencyMs = 0;
        int totalInvocations = 0;
        CandidateOption lastSelected = null;

        for (int p = 0; p < effectiveK; p++) {
            boolean shouldShuffle = (effectiveK > 1) || shuffleCandidates;
            List<CandidateOption> candidates = buildCandidateOptions(rawCandidates, shouldShuffle, random);
            if (candidates.isEmpty()) {
                String msg = "Nessun candidato valido estratto dalla sorgente '" + candidatesSource + "'.";
                log.error(msg);
                return ValidationResult.withFieldErrors(List.of(new FieldError(id, "decision", ValidationErrorCode.INFERENCE_ERROR, msg)), List.of());
            }

            String prompt = buildPrompt(context, candidates);
            log.info("Esecuzione step decisionale '{}' con modello '{}' su {} candidati (perm {}/{})...",
                    id, model, candidates.size(), (p + 1), effectiveK);

            ModelRequest request = new ModelRequest(
                    model,
                    prompt,
                    null,
                    Map.of("num_predict", 1, "temperature", 0.0, "repeat_penalty", 1.0),
                    systemPrompt,
                    Boolean.FALSE
            );

            long callStart = System.currentTimeMillis();
            String answer;
            try {
                ModelResponse response = modelClient.generate(request);
                totalInferenceLatencyMs += (System.currentTimeMillis() - callStart);
                totalInvocations++;
                if (response == null || response.content() == null || response.content().isBlank()) {
                    throw new IllegalStateException("Risposta vuota ricevuta dal modello decisionale.");
                }
                answer = response.content().strip();
                log.debug("Step decisionale '{}' risposta grezza perm {}: '{}'", id, p, answer);
            } catch (Exception e) {
                String msg = "Errore durante l'interrogazione del modello decisionale nello step '" + id + "' (perm " + p + "): " + e.getMessage();
                log.error(msg, e);
                return ValidationResult.withFieldErrors(List.of(new FieldError(id, "decision", ValidationErrorCode.INFERENCE_ERROR, msg)), List.of());
            }

            CandidateOption selected = resolveCandidate(answer, candidates);
            if (selected == null) {
                log.warn("Step decisionale '{}': token non ammesso ('{}'). Esecuzione retry con permutazione alternativa delle opzioni...", id, answer);
                totalInvocations++;
                long retryStart = System.currentTimeMillis();
                try {
                    // Costruzione permutazione alternativa ciclica (shift = 1) per cambiare sostanzialmente l'ordine delle opzioni
                    List<CandidateOption> retryCandidates = new ArrayList<>(candidates.size());
                    int shift = 1;
                    for (int i = 0; i < candidates.size(); i++) {
                        char newLetter = (char) ('A' + i);
                        CandidateOption orig = candidates.get((i + shift) % candidates.size());
                        retryCandidates.add(new CandidateOption(newLetter, orig.key(), orig.role(), orig.person(), orig.email(), orig.label()));
                    }

                    String retryPrompt = buildPrompt(context, retryCandidates);
                    ModelRequest retryRequest = new ModelRequest(
                            model,
                            retryPrompt,
                            null,
                            Map.of("num_predict", 1, "temperature", 0.0, "repeat_penalty", 1.0),
                            systemPrompt,
                            Boolean.FALSE
                    );

                    ModelResponse retryResp = modelClient.generate(retryRequest);
                    totalInferenceLatencyMs += (System.currentTimeMillis() - retryStart);
                    if (retryResp != null && retryResp.content() != null && !retryResp.content().isBlank()) {
                        String retryAnswer = retryResp.content().strip();
                        log.debug("Step decisionale '{}' risposta retry con permutazione alternativa: '{}'", id, retryAnswer);
                        selected = resolveCandidate(retryAnswer, retryCandidates);
                        if (selected == null) {
                            answer = retryAnswer;
                        }
                    }
                } catch (Exception retryEx) {
                    log.warn("Retry step decisionale '{}' fallito con eccezione: {}", id, retryEx.getMessage());
                }

                if (selected == null) {
                    String msg = String.format(Locale.ROOT,
                            "Token non ammesso dal modello decisionale ('%s') non corrispondente ad alcuna opzione valida tra A e %c anche dopo retry con permutazione alternativa.",
                            answer, candidates.get(candidates.size() - 1).letter());
                    log.warn(msg);
                    return ValidationResult.withFieldErrors(
                            List.of(new FieldError(targetField != null ? targetField : id, id, ValidationErrorCode.INDETERMINATE, msg)),
                            List.of(msg)
                    );
                }
            }

            voteCounts.put(selected.key(), voteCounts.getOrDefault(selected.key(), 0) + 1);
            keyToOption.put(selected.key(), selected);
            lastSelected = selected;
        }

        java.util.Map.Entry<String, Integer> topEntry = java.util.Collections.max(
                voteCounts.entrySet(), java.util.Map.Entry.comparingByValue());
        double agreementRatio = (double) topEntry.getValue() / effectiveK;

        // Tracciamento osservabilità e metriche
        context.recordStepMetric(new com.tirocinioaltrama.slmorchestrator.core.model.StepMetric(
                id, "DECISION_VOTING", model, totalInferenceLatencyMs, 0, totalInvocations,
                agreementRatio >= minAgreementRatio ? "CONSENSUS" : "INDETERMINATE"));
        context.setAttribute("decision." + id + ".votes", voteCounts);
        context.setAttribute("decision." + id + ".invocations", totalInvocations);
        context.setAttribute("decision." + id + ".latencyMs", totalInferenceLatencyMs);
        context.setAttribute("decision." + id + ".agreementRatio", agreementRatio);

        if (agreementRatio < minAgreementRatio) {
            String msg = String.format(Locale.ROOT,
                    "Decisione indeterminata su step '%s': accordo insufficiente tra %d permutazioni (max: %.1f%% su '%s', richiesta: %.1f%%). Distribuzione voti: %s",
                    id, effectiveK, (agreementRatio * 100.0), topEntry.getKey(), (minAgreementRatio * 100.0), voteCounts);
            log.warn(msg);
            return ValidationResult.withFieldErrors(
                    List.of(new FieldError(targetField, id, ValidationErrorCode.INDETERMINATE, msg)),
                    List.of(msg)
            );
        }

        CandidateOption selected = keyToOption.get(topEntry.getKey());
        if (selected == null) {
            selected = lastSelected;
        }

        log.info("Step decisionale '{}' ha selezionato opzione {}: {} (ruolo: '{}', persona: '{}')",
                id, selected.letter(), selected.key(), selected.role(), selected.person());

        // Integrazione simbolica della decisione nel JSON di output
        try {
            String currentJson = context.getModelOutput();
            ObjectNode root = (currentJson != null && !currentJson.isBlank())
                    ? (ObjectNode) OBJECT_MAPPER.readTree(currentJson)
                    : OBJECT_MAPPER.createObjectNode();

            String valToSet = (selected.person() != null && !selected.person().isBlank())
                    ? selected.person()
                    : selected.role();

            root.put(targetField, valToSet);
            if (selected.role() != null && !selected.role().isBlank()) {
                root.put("ruoloDestinatario", selected.role());
            }
            if (selected.email() != null && !selected.email().isBlank()) {
                root.put("emailDestinatario", selected.email());
            }
            root.put("motivoInstradamento", "Assegnazione deterministica ad " + selected.role() + " (" + selected.person() + ")");

            context.setModelOutput(OBJECT_MAPPER.writeValueAsString(root));
        } catch (Exception e) {
            String msg = "Impossibile aggiornare l'output JSON con la decisione presa nello step '" + id + "': " + e.getMessage();
            log.error(msg, e);
            return ValidationResult.withFieldErrors(List.of(new FieldError(id, "decision", ValidationErrorCode.INFERENCE_ERROR, msg)), List.of());
        }

        context.setAttribute("decision." + id, selected.key());
        context.setAttribute("decision." + id + ".choice", String.valueOf(selected.letter()));
        context.setAttribute("decision." + id + ".candidate", selected);

        return ValidationResult.valid();
    }

    private String buildPrompt(PipelineContext context, List<CandidateOption> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Analizza il seguente contesto e seleziona l'opzione più idonea tra quelle indicate.\n\n");
        sb.append("<payload>\n");
        if (context.getModelOutput() != null && !context.getModelOutput().isBlank()) {
            sb.append(context.getModelOutput()).append("\n");
        } else if (context.getSanitizedInput() != null && !context.getSanitizedInput().isBlank()) {
            sb.append(context.getSanitizedInput()).append("\n");
        } else {
            sb.append(context.getRawInput() != null ? context.getRawInput() : "").append("\n");
        }
        sb.append("</payload>\n\n");
        sb.append("Opzioni disponibili:\n");
        for (CandidateOption c : candidates) {
            sb.append(c.letter()).append(": ").append(c.label()).append("\n");
        }
        sb.append("\nRispondi tassativamente ed esclusivamente con la sola lettera dell'opzione scelta (es. A, B, C...). Non aggiungere spiegazioni.\n");
        sb.append("Scelta:");
        return sb.toString();
    }

    public static CandidateOption resolveCandidate(String answer, List<CandidateOption> candidates) {
        if (answer == null || answer.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }

        String clean = answer.strip().toUpperCase(Locale.ROOT);
        if (clean.startsWith("CHOICE:") || clean.startsWith("OPTION:") || clean.startsWith("SCELTA:")) {
            clean = clean.substring(clean.indexOf(':') + 1).strip();
        } else if (clean.startsWith("CHOICE ") || clean.startsWith("OPTION ") || clean.startsWith("SCELTA ")) {
            clean = clean.substring(clean.indexOf(' ') + 1).strip();
        }

        char letter = ' ';
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (Character.isLetter(c)) {
                letter = c;
                break;
            }
        }

        for (CandidateOption candidate : candidates) {
            if (candidate.letter() == letter) {
                return candidate;
            }
        }

        // Fallback per corrispondenza esatta del ruolo o persona nel testo della risposta
        for (CandidateOption candidate : candidates) {
            if (candidate.role() != null && clean.contains(candidate.role().toUpperCase(Locale.ROOT))) {
                return candidate;
            }
            if (candidate.person() != null && clean.contains(candidate.person().toUpperCase(Locale.ROOT))) {
                return candidate;
            }
            if (candidate.key() != null && clean.contains(candidate.key().toUpperCase(Locale.ROOT))) {
                return candidate;
            }
        }

        return null;
    }

    public static List<CandidateOption> buildCandidateOptions(Object rawData) {
        return buildCandidateOptions(rawData, false, null);
    }

    public static List<CandidateOption> buildCandidateOptions(Object rawData, boolean shuffle, java.util.Random rng) {
        List<CandidateOption> list = new ArrayList<>();
        if (rawData == null) {
            return list;
        }

        record RawTuple(String key, String role, String person, String email, String label) {}
        List<RawTuple> rawTuples = new ArrayList<>();

        try {
            JsonNode root = (rawData instanceof JsonNode node)
                    ? node
                    : (rawData instanceof String str)
                    ? OBJECT_MAPPER.readTree(str)
                    : OBJECT_MAPPER.valueToTree(rawData);

            if (root.isObject()) {
                for (var entry : ((ObjectNode) root).properties()) {
                    String key = entry.getKey();
                    JsonNode val = entry.getValue();

                    String role = val.has("ruolo") ? val.get("ruolo").asText() : key;
                    String person = val.has("persona") ? val.get("persona").asText() : "";
                    String email = val.has("email") ? val.get("email").asText() : "";

                    StringBuilder desc = new StringBuilder();
                    if (val.has("ambiti") && val.get("ambiti").isArray()) {
                        List<String> ambiti = new ArrayList<>();
                        for (JsonNode a : val.get("ambiti")) {
                            ambiti.add(a.asText());
                        }
                        desc.append(" [Ambiti: ").append(String.join(", ", ambiti)).append("]");
                    }

                    String label = role + (!person.isBlank() ? " (" + person + ")" : "") + desc;
                    rawTuples.add(new RawTuple(key, role, person, email, label));
                }
            } else if (root.isArray()) {
                for (JsonNode item : root) {
                    String text = item.isTextual() ? item.asText() : item.toString();
                    rawTuples.add(new RawTuple(text, text, text, "", text));
                }
            }

            if (shuffle && !rawTuples.isEmpty()) {
                java.util.Collections.shuffle(rawTuples, rng != null ? rng : new java.util.Random());
            }

            int index = 0;
            for (RawTuple t : rawTuples) {
                if (index >= OPTION_LETTERS.length) break;
                char letter = OPTION_LETTERS[index++];
                list.add(new CandidateOption(letter, t.key(), t.role(), t.person(), t.email(), t.label()));
            }
        } catch (Exception e) {
            log.warn("Impossibile estrarre CandidateOptions dalla sorgente dati: {}", e.getMessage());
        }

        return list;
    }
}
