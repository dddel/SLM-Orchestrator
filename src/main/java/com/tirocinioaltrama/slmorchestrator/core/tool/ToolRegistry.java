package com.tirocinioaltrama.slmorchestrator.core.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tirocinioaltrama.slmorchestrator.impl.tool.CompanyRolesTool;
import org.springframework.stereotype.Component;

/**
 * Registro centrale dei Tool disponibili nell'orchestratore.
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        registerDefaultTools();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public ToolRegistry(List<Tool> initialTools) {
        registerDefaultTools();
        if (initialTools != null) {
            for (Tool t : initialTools) {
                register(t);
            }
        }
    }

    private void registerDefaultTools() {
        register(new Tool() {
            @Override
            public String getName() {
                return "save_to_db";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.ok("Mock save_to_db eseguito con successo");
            }
        });

        register(new Tool() {
            @Override
            public String getName() {
                return "contact_operator";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.ok("Mock contact_operator eseguito con successo");
            }
        });

        try {
            register(new CompanyRolesTool());
        } catch (Exception ignored) {
        }
    }

    public synchronized void register(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Il tool non può essere nullo.");
        }
        if (tool.getName() == null || tool.getName().isBlank()) {
            throw new IllegalArgumentException("Il nome del tool non può essere nullo o vuoto.");
        }
        tools.put(tool.getName().strip().toLowerCase(), tool);
    }

    public synchronized Optional<Tool> get(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name.strip().toLowerCase()));
    }

    public synchronized boolean contains(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return tools.containsKey(name.strip().toLowerCase());
    }

    public synchronized Collection<String> getRegisteredNames() {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(tools.keySet()));
    }
}
