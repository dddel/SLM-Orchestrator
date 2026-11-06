package com.tirocinioaltrama.slmorchestrator.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    @Test
    void testToolRegistryOperations() {
        ToolRegistry registry = new ToolRegistry();

        // Default tools presenti
        assertTrue(registry.contains("save_to_db"));
        assertTrue(registry.contains("contact_operator"));
        assertTrue(registry.contains("company_roles_lookup"));
        assertTrue(registry.get("save_to_db").isPresent());
        assertTrue(registry.get("SAVE_TO_DB").isPresent()); // case-insensitive

        assertFalse(registry.contains("non_existent"));
        assertFalse(registry.contains(null));
        assertFalse(registry.contains("  "));
        assertTrue(registry.get(null).isEmpty());
        assertTrue(registry.get("  ").isEmpty());

        // Registrazione custom tool
        Tool customTool = new Tool() {
            @Override
            public String getName() {
                return "custom_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.ok("custom executed");
            }
        };

        registry.register(customTool);
        assertTrue(registry.contains("custom_tool"));
        assertTrue(registry.getRegisteredNames().contains("custom_tool"));

        // Validazione argomenti non validi
        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Tool() {
            @Override
            public String getName() {
                return null;
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return null;
            }
        }));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Tool() {
            @Override
            public String getName() {
                return "   ";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return null;
            }
        }));
    }

    @Test
    void testConstructorWithInitialTools() {
        Tool extra = new Tool() {
            @Override
            public String getName() {
                return "extra_tool";
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.ok("extra");
            }
        };

        ToolRegistry registry = new ToolRegistry(List.of(extra));
        assertTrue(registry.contains("extra_tool"));
        assertTrue(registry.contains("save_to_db"));
    }
}
