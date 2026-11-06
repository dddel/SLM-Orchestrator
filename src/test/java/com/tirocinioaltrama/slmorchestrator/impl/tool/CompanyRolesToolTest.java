package com.tirocinioaltrama.slmorchestrator.impl.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import com.tirocinioaltrama.slmorchestrator.impl.tool.CompanyRolesTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;

class CompanyRolesToolTest {

    @Test
    @DisplayName("CompanyRolesTool carica correttamente i ruoli e nomi fittizi dal file mock")
    void testLoadCompanyRoles() {
        CompanyRolesTool tool = new CompanyRolesTool();
        assertEquals("company_roles_lookup", tool.getName());

        JsonNode root = tool.getRolesRoot();
        assertNotNull(root, "La radice JSON dei ruoli non deve essere nulla");

        // Verifica Direttore
        assertTrue(root.has("direzione"));
        assertEquals("Stefano Vena", root.get("direzione").get("persona").asText());
        assertEquals("Direttore", root.get("direzione").get("ruolo").asText());

        // Verifica H.R.
        assertTrue(root.has("risorse_umane"));
        assertEquals("Claudia Frammartino", root.get("risorse_umane").get("persona").asText());
        assertEquals("H.R.", root.get("risorse_umane").get("ruolo").asText());

        // Verifica Reparto Tecnico
        assertTrue(root.has("reparto_tecnico"));
        assertEquals("Marco Rossi", root.get("reparto_tecnico").get("persona").asText());

        // Verifica Testers
        assertTrue(root.has("testers"));
        assertEquals("Luca Bianchi", root.get("testers").get("persona").asText());

        // Verifica Developers
        assertTrue(root.has("developers"));
        assertEquals("Alessandro Ferrari", root.get("developers").get("persona").asText());
    }

    @Test
    @DisplayName("Esecuzione ToolResult restituisce successo e payload non vuoto")
    void testExecuteTool() {
        CompanyRolesTool tool = new CompanyRolesTool();
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.success());
        assertNotNull(result.data());
        assertTrue(result.data().toString().contains("Stefano Vena"));
        assertTrue(result.data().toString().contains("Claudia Frammartino"));
    }
}
