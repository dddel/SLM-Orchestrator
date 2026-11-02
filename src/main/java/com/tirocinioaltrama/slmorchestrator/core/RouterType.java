package com.tirocinioaltrama.slmorchestrator.core;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Tipi di router selezionabili dinamicamente per la classificazione dei documenti.
 */
public enum RouterType {
    LOCAL_OLLAMA("local-ollama"),
    REFLEX("reflex");

    private final String key;

    RouterType(String key) {
        this.key = key;
    }

    @JsonValue
    public String getKey() {
        return key;
    }

    @JsonCreator
    public static RouterType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim().toLowerCase(Locale.ROOT);
        for (RouterType t : values()) {
            if (t.key.equalsIgnoreCase(clean) || t.name().equalsIgnoreCase(clean) || clean.replace("-", "_").equalsIgnoreCase(t.name())) {
                return t;
            }
        }
        return null;
    }
}
