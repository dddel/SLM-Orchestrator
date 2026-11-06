package com.tirocinioaltrama.slmorchestrator.core.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FieldPathTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testValidPathParsing() {
        FieldPath path = FieldPath.of("utente.indirizzo.via");
        assertEquals("utente.indirizzo.via", path.getOriginalPath());
        assertEquals("utente.indirizzo.via", path.toString());
        assertEquals(List.of("utente", "indirizzo", "via"), path.getSegments());
        assertEquals("via", path.getLastSegment());
        assertEquals(FieldPath.of("utente.indirizzo.via"), path);
        assertEquals(path.hashCode(), FieldPath.of("utente.indirizzo.via").hashCode());
        assertNotEquals(path, "other");
    }

    @Test
    void testInvalidPathParsing() {
        assertThrows(IllegalArgumentException.class, () -> FieldPath.of(null));
        assertThrows(IllegalArgumentException.class, () -> FieldPath.of("  "));
        assertThrows(IllegalArgumentException.class, () -> FieldPath.of("utente..via"));
    }

    @Test
    void testExistsInJsonSchema() throws Exception {
        String schemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "utente": {
                      "type": "object",
                      "properties": {
                        "nome": { "type": "string" }
                      }
                    },
                    "righe": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "codice": { "type": "string" }
                        }
                      }
                    }
                  }
                }
                """;
        JsonNode root = mapper.readTree(schemaJson);

        assertTrue(FieldPath.of("utente.nome").existsInJsonSchema(root));
        assertTrue(FieldPath.of("righe.codice").existsInJsonSchema(root));
        assertFalse(FieldPath.of("utente.cognome").existsInJsonSchema(root));
        assertFalse(FieldPath.of("inesistente").existsInJsonSchema(root));
        assertFalse(FieldPath.of("utente").existsInJsonSchema(null));
    }

    @Test
    void testUnsupportedSchemaConstructs() throws Exception {
        JsonNode refSchema = mapper.readTree("{\"properties\": {\"a\": {\"$ref\": \"#/defs/A\"}}}");
        assertThrows(IllegalArgumentException.class, () -> FieldPath.of("a.b").existsInJsonSchema(refSchema));

        JsonNode oneOfSchema = mapper.readTree("{\"properties\": {\"a\": {\"oneOf\": []}}}");
        assertThrows(IllegalArgumentException.class, () -> FieldPath.of("a.b").existsInJsonSchema(oneOfSchema));

        JsonNode arrayOfArray = mapper.readTree("{\"properties\": {\"a\": {\"items\": {\"type\": \"array\"}}}}");
        assertThrows(IllegalArgumentException.class, () -> FieldPath.of("a.b").existsInJsonSchema(arrayOfArray));
    }

    @Test
    void testExtractNodesAndValues() throws Exception {
        String json = """
                {
                  "nome": "Mario",
                  "cognome": null,
                  "recapiti": [
                    { "tipo": "email", "valore": "mario@test.it" },
                    { "tipo": "tel", "valore": "12345" },
                    { "tipo": "fax", "valore": null }
                  ]
                }
                """;
        JsonNode root = mapper.readTree(json);

        FieldPath nomePath = FieldPath.of("nome");
        assertTrue(nomePath.existsAndNonNull(root));
        assertEquals(List.of("Mario"), nomePath.extractValues(root));

        FieldPath cognomePath = FieldPath.of("cognome");
        assertFalse(cognomePath.existsAndNonNull(root));
        assertTrue(cognomePath.extractValues(root).isEmpty());

        FieldPath valoriPath = FieldPath.of("recapiti.valore");
        assertTrue(valoriPath.existsAndNonNull(root));
        assertEquals(List.of("mario@test.it", "12345"), valoriPath.extractValues(root));

        FieldPath missingPath = FieldPath.of("inesistente");
        assertFalse(missingPath.existsAndNonNull(root));
        assertTrue(missingPath.extractNodes(null).isEmpty());
    }
}
