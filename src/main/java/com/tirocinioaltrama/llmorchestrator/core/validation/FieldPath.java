package com.tirocinioaltrama.llmorchestrator.core.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import tools.jackson.databind.JsonNode;

/**
 * Rappresenta un percorso qualificato di un campo all'interno di una struttura JSON o di uno schema JSON.
 * Supporta la notazione a punti (es. "fornitore.partitaIva", "righe.iva").
 * Condiviso sia dal validatore di configurazione al bootstrap (per verificare l'esistenza nello schema)
 * sia dall'esecuzione a runtime (per estrarre i valori da validare).
 */
public final class FieldPath {

    private final String originalPath;
    private final List<String> segments;

    public FieldPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Il percorso del campo non può essere nullo o vuoto.");
        }
        this.originalPath = path.strip();
        String[] parts = this.originalPath.split("\\.");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Segmento non valido nel percorso del campo: '" + path + "'");
            }
            list.add(trimmed);
        }
        this.segments = Collections.unmodifiableList(list);
    }

    public static FieldPath of(String path) {
        return new FieldPath(path);
    }

    public String getOriginalPath() {
        return originalPath;
    }

    public List<String> getSegments() {
        return segments;
    }

    public String getLastSegment() {
        return segments.get(segments.size() - 1);
    }

    /**
     * Verifica se il percorso esiste all'interno di uno schema JSON (struttura properties / items).
     * Se incontra costrutti complessi non supportati ($ref, oneOf, anyOf, allOf), lancia IllegalArgumentException.
     * Se incontra items di tipo array annidato di array, lancia IllegalArgumentException.
     */
    public boolean existsInJsonSchema(JsonNode schemaRoot) {
        if (schemaRoot == null || !schemaRoot.isObject()) {
            return false;
        }
        return checkSchemaSegments(schemaRoot, 0);
    }

    private boolean checkSchemaSegments(JsonNode currentNode, int segmentIndex) {
        if (segmentIndex >= segments.size()) {
            return true;
        }

        // Controllo costrutti non supportati
        if (currentNode.has("$ref")) {
            throw new IllegalArgumentException("Costrutto '$ref' non supportato nello schema JSON al percorso '" + originalPath + "'");
        }
        if (currentNode.has("oneOf") || currentNode.has("anyOf") || currentNode.has("allOf")) {
            throw new IllegalArgumentException("Costrutti polimorfici 'oneOf/anyOf/allOf' non supportati nello schema JSON al percorso '" + originalPath + "'");
        }

        String segment = segments.get(segmentIndex);

        // Se siamo su un tipo object, navighiamo nelle properties
        if (currentNode.has("properties")) {
            JsonNode properties = currentNode.get("properties");
            if (properties != null && properties.has(segment)) {
                JsonNode childProperty = properties.get(segment);
                return checkSchemaSegments(childProperty, segmentIndex + 1);
            }
            return false;
        }

        // Se siamo su un tipo array, navighiamo in items
        if (currentNode.has("items")) {
            JsonNode items = currentNode.get("items");
            if (items == null || !items.isObject()) {
                return false;
            }
            // Array di array non supportato
            if ("array".equalsIgnoreCase(items.path("type").asString(""))) {
                throw new IllegalArgumentException("Array multidimensionali annidati non supportati nello schema JSON al percorso '" + originalPath + "'");
            }
            // Naviga dentro items per lo stesso segmento
            return checkSchemaSegments(items, segmentIndex);
        }

        return false;
    }

    /**
     * Estrae tutti i nodi JsonNode presenti al percorso specificato (inclusi array impliciti).
     * Restituisce una lista di JsonNode (può contenere nodi NullNode se presenti).
     */
    public List<JsonNode> extractNodes(JsonNode root) {
        if (root == null || root.isNull()) {
            return Collections.emptyList();
        }
        List<JsonNode> nodes = new ArrayList<>();
        collectNodes(root, 0, nodes);
        return nodes;
    }

    private void collectNodes(JsonNode currentNode, int segmentIndex, List<JsonNode> result) {
        if (currentNode == null) {
            return;
        }

        if (segmentIndex >= segments.size()) {
            result.add(currentNode);
            return;
        }

        if (currentNode.isArray()) {
            for (JsonNode elem : currentNode) {
                collectNodes(elem, segmentIndex, result);
            }
            return;
        }

        if (currentNode.isObject()) {
            String segment = segments.get(segmentIndex);
            if (currentNode.has(segment)) {
                JsonNode child = currentNode.get(segment);
                collectNodes(child, segmentIndex + 1, result);
            }
        }
    }

    /**
     * Verifica se il percorso è presente e non nullo all'interno del JSON.
     */
    public boolean existsAndNonNull(JsonNode root) {
        List<JsonNode> nodes = extractNodes(root);
        if (nodes.isEmpty()) {
            return false;
        }
        for (JsonNode n : nodes) {
            if (n != null && !n.isNull()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Estrae tutti i valori testuali non vuoti presenti al percorso indicato all'interno di un JsonNode istanza.
     * Se il percorso attraversa un array, itera implicitamente su tutti gli elementi.
     */
    public List<String> extractValues(JsonNode root) {
        List<JsonNode> nodes = extractNodes(root);
        List<String> values = new ArrayList<>();
        for (JsonNode n : nodes) {
            if (n != null && !n.isNull() && n.isValueNode()) {
                String val = n.asString();
                if (val != null && !val.isBlank()) {
                    values.add(val);
                }
            }
        }
        return values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldPath fieldPath)) return false;
        return Objects.equals(originalPath, fieldPath.originalPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalPath);
    }

    @Override
    public String toString() {
        return originalPath;
    }
}
