package com.tirocinioaltrama.llmorchestrator.core.validation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Validatore sintattico per output JSON prodotto dai modelli SLM.
 */
public class JsonOutputValidator implements Validator<String> {

    private final ObjectMapper objectMapper;

    public JsonOutputValidator() {
        this(new ObjectMapper());
    }

    public JsonOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public ValidationResult validate(String json) {
        if (json == null || json.isBlank()) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("$", "json", ValidationErrorCode.VALUE_EMPTY, "L'output JSON del modello non può essere nullo o vuoto.")),
                    java.util.List.of());
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                return ValidationResult.withFieldErrors(
                        java.util.List.of(new FieldError("$", "json", ValidationErrorCode.JSON_SYNTAX_INVALID, "L'output non produce un albero JSON valido.")),
                        java.util.List.of());
            }
            return ValidationResult.valid();
        } catch (JacksonException e) {
            return ValidationResult.withFieldErrors(
                    java.util.List.of(new FieldError("$", "json", ValidationErrorCode.JSON_SYNTAX_INVALID, "JSON non valido sintatticamente: " + e.getOriginalMessage())),
                    java.util.List.of());
        }
    }
}
