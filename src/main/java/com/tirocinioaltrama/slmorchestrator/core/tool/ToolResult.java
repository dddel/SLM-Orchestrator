package com.tirocinioaltrama.slmorchestrator.core.tool;

/**
 * Risultato immutabile dell'esecuzione di un tool.
 *
 * @param success indica se l'operazione ha avuto successo
 * @param message eventuale messaggio esplicativo o di errore
 * @param data    eventuale payload strutturato prodotto dal tool
 */
public record ToolResult(
        boolean success,
        String message,
        Object data
) {

    public ToolResult(boolean success, String message) {
        this(success, message, null);
    }

    public static ToolResult ok() {
        return new ToolResult(true, null, null);
    }

    public static ToolResult ok(String message) {
        return new ToolResult(true, message, null);
    }

    public static ToolResult ok(String message, Object data) {
        return new ToolResult(true, message, data);
    }

    public static ToolResult failure(String message) {
        return new ToolResult(false, message, null);
    }
}
