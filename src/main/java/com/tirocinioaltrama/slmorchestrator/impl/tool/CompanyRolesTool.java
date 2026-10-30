package com.tirocinioaltrama.slmorchestrator.impl.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tirocinioaltrama.slmorchestrator.core.tool.Tool;
import com.tirocinioaltrama.slmorchestrator.core.tool.ToolResult;

/**
 * Mock Tool per la consultazione della directory dei ruoli e personale aziendale.
 * Carica i dati fittizi da {@code mock/company_roles.json} per consentire l'associazione
 * del destinatario tramite Small Language Model (SLM).
 */
@Component
public class CompanyRolesTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CompanyRolesTool.class);
    public static final String TOOL_NAME = "company_roles_lookup";
    private static final String DEFAULT_ROLES_PATH = "mock/company_roles.json";

    private final String rolesJson;
    private final JsonNode rolesRoot;

    public CompanyRolesTool() {
        this(DEFAULT_ROLES_PATH);
    }

    public CompanyRolesTool(String resourcePath) {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{}";
        JsonNode root = null;
        try {
            org.springframework.core.io.Resource resource = resourcePath.startsWith("file:")
                    ? new org.springframework.core.io.FileSystemResource(resourcePath.substring(5))
                    : new ClassPathResource(resourcePath);
            try (InputStream is = resource.getInputStream()) {
                json = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
            root = mapper.readTree(json);
            log.info("CompanyRolesTool inizializzato con successo da risorsa '{}'", resourcePath);
        } catch (Exception e) {
            log.error("Impossibile caricare il file mock dei ruoli aziendali da '{}': {}", resourcePath, e.getMessage());
        }
        this.rolesJson = json;
        this.rolesRoot = root;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    public String getRolesJson() {
        return rolesJson;
    }

    public JsonNode getRolesRoot() {
        return rolesRoot;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        if (rolesRoot == null || rolesJson.isBlank()) {
            return ToolResult.failure("Directory dei ruoli aziendali non disponibile o vuota");
        }

        if (args == null || args.isEmpty()) {
            return ToolResult.ok("Directory completa ruoli caricata con successo", rolesJson);
        }

        Object queryObj = args.get("query");
        if (queryObj == null) {
            queryObj = args.get("role");
        }

        if (queryObj == null || queryObj.toString().isBlank()) {
            return ToolResult.ok("Directory completa ruoli caricata con successo", rolesJson);
        }

        String query = queryObj.toString().toLowerCase(Locale.ROOT).strip();
        return ToolResult.ok("Risultati directory per query '" + query + "'", rolesJson);
    }
}
