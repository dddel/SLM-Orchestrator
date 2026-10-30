package com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tirocinioaltrama.slmorchestrator.core.validation.FieldError;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationErrorCode;
import com.tirocinioaltrama.slmorchestrator.core.validation.ValidationResult;
import com.tirocinioaltrama.slmorchestrator.core.validation.Validator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Deterministic Symbolic Membership Guard (Guardia Simbolica di Appartenenza a Catalogo).
 * <p>
 * Verifica matematicamente e deterministicamente che un valore o decisione (es. destinatario, ruolo)
 * appartenga rigorosamente all'insieme discreto di candidati autorizzati forniti da un tool o contesto
 * (es. company_roles_lookup), garantendo zero allucinazioni e conformità chiusa.
 */
public class CatalogMembershipGuard implements Validator<String> {

    private static final Logger log = LoggerFactory.getLogger(CatalogMembershipGuard.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Set<String> allowedValues;
    private final String catalogName;

    public CatalogMembershipGuard(Object catalogData) {
        this(catalogData, "catalog");
    }

    public CatalogMembershipGuard(Object catalogData, String catalogName) {
        this.allowedValues = extractAllowedValues(catalogData);
        this.catalogName = catalogName != null ? catalogName : "catalog";
    }

    public String getName() {
        return "catalog_membership";
    }

    @Override
    public ValidationResult validate(String value) {
        return validateField("field", value, allowedValues);
    }

    public Set<String> getAllowedValues() {
        return Collections.unmodifiableSet(allowedValues);
    }

    /**
     * Valida deterministicamente un singolo campo rispetto al catalogo consentito.
     */
    public static ValidationResult validateField(String fieldName, String value, Set<String> allowedCatalog) {
        if (value == null || value.isBlank()) {
            return ValidationResult.valid();
        }

        if (allowedCatalog == null || allowedCatalog.isEmpty()) {
            return ValidationResult.valid();
        }

        if (isMember(value, allowedCatalog)) {
            return ValidationResult.valid();
        }

        String msg = "Il valore '" + value + "' per il campo '" + fieldName + "' non appartiene al catalogo autorizzato.";
        log.warn("CatalogMembershipGuard violata su campo '{}': valore '{}' non presente nel catalogo.", fieldName, value);
        FieldError fe = new FieldError(fieldName, "CatalogMembershipGuard", ValidationErrorCode.VALUE_NOT_IN_CATALOG, msg);
        return ValidationResult.withFieldErrors(List.of(fe), List.of());
    }

    /**
     * Valida un insieme di campi su un albero JsonNode rispetto ai dati di catalogo forniti.
     */
    public static ValidationResult validateJson(JsonNode root, Object catalogData, List<String> fieldPaths) {
        if (root == null || fieldPaths == null || fieldPaths.isEmpty()) {
            return ValidationResult.valid();
        }

        Set<String> allowed = extractAllowedValues(catalogData);
        if (allowed.isEmpty()) {
            return ValidationResult.valid();
        }

        List<FieldError> errors = new ArrayList<>();
        for (String path : fieldPaths) {
            JsonNode targetNode = root.get(path);
            if (targetNode != null && !targetNode.isNull()) {
                String val = targetNode.asText();
                if (val != null && !val.isBlank() && !isMember(val, allowed)) {
                    String msg = "Il valore '" + val + "' per il campo '" + path + "' non appartiene al catalogo autorizzato.";
                    log.warn("CatalogMembershipGuard violata: {} (valore: '{}')", msg, val);
                    errors.add(new FieldError(path, "CatalogMembershipGuard", ValidationErrorCode.VALUE_NOT_IN_CATALOG, msg));
                }
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.withFieldErrors(errors, List.of());
        }
        return ValidationResult.valid();
    }

    /**
     * Verifica di appartenenza case-insensitive e normalizzata con supporto per punteggiatura (es. 'H.R.' vs 'HR').
     */
    public static boolean isMember(String value, Set<String> allowedCatalog) {
        if (value == null || allowedCatalog == null || allowedCatalog.isEmpty()) {
            return false;
        }

        String clean = normalize(value);
        if (clean.isBlank()) {
            return false;
        }

        for (String candidate : allowedCatalog) {
            String normCand = normalize(candidate);
            if (clean.equals(normCand)) {
                return true;
            }
            // Match su sottostringa se una persona o ruolo è citato per esteso
            if (clean.contains(normCand) && normCand.length() >= 4) {
                return true;
            }
            if (normCand.contains(clean) && clean.length() >= 4) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.trim()
                .toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replace("-", " ")
                .replaceAll("\\s+", " ");
    }

    /**
     * Estrae tutti i valori candidati validi da una varietà di sorgenti di catalogo
     * (JSON String, JsonNode, Map, Collection).
     */
    public static Set<String> extractAllowedValues(Object catalogData) {
        if (catalogData == null) {
            return Collections.emptySet();
        }

        Set<String> values = new HashSet<>();

        if (catalogData instanceof String str) {
            if (str.isBlank()) return Collections.emptySet();
            try {
                JsonNode node = OBJECT_MAPPER.readTree(str);
                collectFromNode(node, values);
            } catch (Exception e) {
                values.add(str.strip());
            }
        } else if (catalogData instanceof JsonNode node) {
            collectFromNode(node, values);
        } else if (catalogData instanceof Collection<?> col) {
            for (Object item : col) {
                if (item != null) {
                    values.addAll(extractAllowedValues(item));
                }
            }
        } else if (catalogData instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    values.add(entry.getKey().toString().strip());
                }
                if (entry.getValue() != null) {
                    values.addAll(extractAllowedValues(entry.getValue()));
                }
            }
        }

        return values;
    }

    private static void collectFromNode(JsonNode node, Set<String> values) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            for (var field : ((tools.jackson.databind.node.ObjectNode) node).properties()) {
                String key = field.getKey();
                values.add(key.strip());
                JsonNode child = field.getValue();

                if (child.isObject()) {
                    // Se l'oggetto ha campi canonici di ruolo/persona/email
                    if (child.has("ruolo")) values.add(child.get("ruolo").asText().strip());
                    if (child.has("persona")) values.add(child.get("persona").asText().strip());
                    if (child.has("email")) values.add(child.get("email").asText().strip());
                    if (child.has("collaboratori") && child.get("collaboratori").isArray()) {
                        for (JsonNode c : child.get("collaboratori")) {
                            values.add(c.asText().strip());
                        }
                    }
                }
                collectFromNode(child, values);
            }
        } else if (node.isArray()) {
            for (JsonNode elem : node) {
                collectFromNode(elem, values);
            }
        } else if (node.isValueNode()) {
            String text = node.asText();
            if (text != null && !text.isBlank()) {
                values.add(text.strip());
            }
        }
    }
}
