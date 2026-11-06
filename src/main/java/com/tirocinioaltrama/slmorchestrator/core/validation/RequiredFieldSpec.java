package com.tirocinioaltrama.slmorchestrator.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import tools.jackson.databind.JsonNode;

/**
 * Specifica immutabile di un campo 'required' derivata dallo schema JSON al bootstrap.
 * Contiene il percorso del campo e l'elenco degli antenati (ancestors) per determinare
 * se il ramo è attivo (cioè se tutti gli antenati sono presenti e non nulli).
 */
public record RequiredFieldSpec(
        FieldPath path,
        List<FieldPath> ancestorPaths,
        boolean nullable
) {
    public RequiredFieldSpec {
        Objects.requireNonNull(path, "path cannot be null");
        ancestorPaths = ancestorPaths != null ? Collections.unmodifiableList(new ArrayList<>(ancestorPaths)) : Collections.emptyList();
    }

    /**
     * Ispeziona uno schema JSON e ne ricava ricorsivamente tutte le specifiche dei campi 'required'.
     */
    public static List<RequiredFieldSpec> extractRequiredSpecs(JsonNode schemaRoot) {
        if (schemaRoot == null || !schemaRoot.isObject()) {
            return Collections.emptyList();
        }
        List<RequiredFieldSpec> specs = new ArrayList<>();
        List<FieldPath> currentAncestors = new ArrayList<>();
        collectSpecs(schemaRoot, "", currentAncestors, specs);
        return Collections.unmodifiableList(specs);
    }

    private static void collectSpecs(
            JsonNode currentNode,
            String currentPath,
            List<FieldPath> currentAncestors,
            List<RequiredFieldSpec> specs
    ) {
        if (currentNode == null || !currentNode.isObject()) {
            return;
        }

        // Se siamo su un nodo con properties
        if (currentNode.has("properties")) {
            JsonNode properties = currentNode.get("properties");
            List<String> requiredList = new ArrayList<>();
            if (currentNode.has("required") && currentNode.get("required").isArray()) {
                for (JsonNode reqNode : currentNode.get("required")) {
                    if (reqNode.isTextual()) {
                        requiredList.add(reqNode.asString());
                    }
                }
            }

            properties.forEachEntry((propName, propNode) -> {
                String childPath = currentPath.isEmpty() ? propName : currentPath + "." + propName;
                boolean isReq = requiredList.contains(propName);
                boolean isNullable = false;

                // Controllo nullable ("type": ["string", "null"] o "nullable": true)
                if (propNode.has("nullable") && propNode.get("nullable").asBoolean(false)) {
                    isNullable = true;
                } else if (propNode.has("type") && propNode.get("type").isArray()) {
                    for (JsonNode t : propNode.get("type")) {
                        if ("null".equalsIgnoreCase(t.asString(""))) {
                            isNullable = true;
                            break;
                        }
                    }
                }

                FieldPath childFieldPath = new FieldPath(childPath);

                if (isReq) {
                    specs.add(new RequiredFieldSpec(childFieldPath, new ArrayList<>(currentAncestors), isNullable));
                }

                // Se l'oggetto ha sotto-proprietà o items, aggiungi childFieldPath agli antenati e ricorsione
                if (propNode.has("properties") || propNode.has("items")) {
                    List<FieldPath> nextAncestors = new ArrayList<>(currentAncestors);
                    nextAncestors.add(childFieldPath);
                    collectSpecs(propNode, childPath, nextAncestors, specs);
                }
            });
        } else if (currentNode.has("items") && currentNode.get("items").isObject()) {
            // Se siamo su un array, items eredita il path
            collectSpecs(currentNode.get("items"), currentPath, currentAncestors, specs);
        }
    }
}
