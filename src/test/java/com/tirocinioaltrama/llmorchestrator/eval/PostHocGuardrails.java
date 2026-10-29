package com.tirocinioaltrama.llmorchestrator.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.tirocinioaltrama.llmorchestrator.core.DocumentType;
import com.tirocinioaltrama.llmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.CodiceFiscaleValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.EmailFormatValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.IbanValidator;
import com.tirocinioaltrama.llmorchestrator.core.validation.field.PartitaIvaValidator;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.guard.AnagraficaCoherenceValidator;
import com.tirocinioaltrama.llmorchestrator.impl.pipeline.guard.PostProcessingGuards;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Applicazione a posteriori dei guardrail (Celle C ed E):
 * PostProcessingGuards.applyGuards + Coerenza Anagrafica + Validatori Formali (CF, P.IVA, IBAN, Email).
 * Nessun retry, nessun cortocircuito. Se un validatore fallisce l'esito è INDETERMINATE.
 */
public class PostHocGuardrails {

    private final ObjectMapper mapper;
    private final PartitaIvaValidator pivaValidator = new PartitaIvaValidator();
    private final CodiceFiscaleValidator cfValidator = new CodiceFiscaleValidator();
    private final IbanValidator ibanValidator = new IbanValidator();
    private final EmailFormatValidator emailValidator = new EmailFormatValidator();

    public record PostHocResult(
            String classifiedType,
            boolean classificationMatch,
            String status,
            JsonNode processedNode,
            Map<String, Object> flatExtracted,
            long guardrailsLatencyMs,
            List<String> validationErrors
    ) {}

    public PostHocGuardrails() {
        this.mapper = new ObjectMapper();
    }

    public PostHocGuardrails(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    public PostHocResult apply(String rawOutput, String rawPayload, String trueType) {
        long start = System.nanoTime();
        List<String> errors = new ArrayList<>();

        if (rawOutput == null || rawOutput.isBlank()) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new PostHocResult(
                    "SCONOSCIUTO", false, "INDETERMINATE", null,
                    Collections.emptyMap(), latencyMs, List.of("Output grezzo vuoto o non pervenuto")
            );
        }

        JsonNode rootNode;
        try {
            rootNode = mapper.readTree(rawOutput);
        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new PostHocResult(
                    "SCONOSCIUTO", false, "INDETERMINATE", null,
                    Collections.emptyMap(), latencyMs, List.of("JSON non valido sintatticamente: " + e.getMessage())
            );
        }

        String classifiedType = rootNode.path("tipoDocumento").asText("ALTRO").toUpperCase(Locale.ROOT);
        boolean typeMatch = classifiedType.equalsIgnoreCase(trueType);

        String subKey = classifiedType.toLowerCase(Locale.ROOT);
        JsonNode subNode = rootNode.get(subKey);

        if (subNode == null || subNode.isNull() || !subNode.isObject()) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            return new PostHocResult(
                    classifiedType, typeMatch, "INDETERMINATE", null,
                    Collections.emptyMap(), latencyMs, List.of("Sotto-oggetto '" + subKey + "' assente o nullo nella busta")
            );
        }

        JsonNode workingNode = subNode.deepCopy();

        DocumentType docType;
        try {
            docType = DocumentType.valueOf(classifiedType);
        } catch (IllegalArgumentException e) {
            docType = DocumentType.ALTRO;
        }

        // 1. PostProcessingGuards.applyGuards
        if (docType != DocumentType.ALTRO) {
            try {
                PostProcessingGuards.applyGuards(workingNode, rawPayload != null ? rawPayload : "", docType);
            } catch (Exception e) {
                errors.add("Errore durante applyGuards: " + e.getMessage());
            }
        }

        // 2. Validatori formali dei campi e coerenza anagrafica
        if (docType == DocumentType.FATTURA) {
            String piva = workingNode.path("fornitore").path("partitaIva").asText(null);
            if (piva != null && !piva.isBlank() && !piva.equalsIgnoreCase("null")) {
                ValidationResult vr = pivaValidator.validate(piva);
                if (!vr.isValid()) {
                    errors.addAll(vr.errors());
                }
            }

            String cf = workingNode.path("cliente").path("codiceFiscale").asText(null);
            if (cf != null && !cf.isBlank() && !cf.equalsIgnoreCase("null")) {
                ValidationResult vr = cfValidator.validate(cf);
                if (!vr.isValid()) {
                    errors.addAll(vr.errors());
                }
            }

            String iban = workingNode.path("iban").asText(null);
            if (iban != null && !iban.isBlank() && !iban.equalsIgnoreCase("null")) {
                ValidationResult vr = ibanValidator.validate(iban);
                if (!vr.isValid()) {
                    errors.addAll(vr.errors());
                }
            }
        } else if (docType == DocumentType.EMAIL) {
            String email = workingNode.path("email").asText(null);
            if (email != null && !email.isBlank() && !email.equalsIgnoreCase("null")) {
                ValidationResult vr = emailValidator.validate(email);
                if (!vr.isValid()) {
                    errors.addAll(vr.errors());
                }
            }
        } else if (docType == DocumentType.ANAGRAFICA) {
            String cf = workingNode.path("codiceFiscale").asText(null);
            if (cf != null && !cf.isBlank() && !cf.equalsIgnoreCase("null")) {
                ValidationResult vr = cfValidator.validate(cf);
                if (!vr.isValid()) {
                    errors.addAll(vr.errors());
                }
            }

            try {
                var cr = AnagraficaCoherenceValidator.validate(workingNode, rawPayload != null ? rawPayload : "");
                if (!cr.isValid()) {
                    for (var v : cr.violations()) {
                        errors.add(v.path() + ": " + v.errorMessage());
                    }
                }
            } catch (Exception e) {
                errors.add("Errore durante validazione coerenza anagrafica: " + e.getMessage());
            }
        }

        String status = errors.isEmpty() ? "OK" : "INDETERMINATE";
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        // Se il tipo classificato non corrisponde al tipo vero, la valutazione sul tipo vero ha 0 campi corrispondenti
        Map<String, Object> flatExtracted = new LinkedHashMap<>();
        if (typeMatch) {
            flattenToolsJson(workingNode, "", flatExtracted);
        }

        return new PostHocResult(
                classifiedType,
                typeMatch,
                status,
                workingNode,
                flatExtracted,
                latencyMs,
                errors
        );
    }

    private static void flattenToolsJson(JsonNode node, String prefix, Map<String, Object> map) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenToolsJson(field.getValue(), key, map);
            }
        } else if (node.isNull()) {
            map.put(prefix, null);
        } else if (node.isTextual()) {
            map.put(prefix, node.asText());
        } else if (node.isNumber()) {
            map.put(prefix, node.numberValue());
        } else if (node.isBoolean()) {
            map.put(prefix, node.asBoolean());
        } else {
            map.put(prefix, node.asText());
        }
    }
}
