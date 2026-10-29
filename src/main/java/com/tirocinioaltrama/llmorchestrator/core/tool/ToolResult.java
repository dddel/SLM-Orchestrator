package com.tirocinioaltrama.llmorchestrator.core.tool;

/**
 * Risultato immutabile dell'esecuzione di un tool.
 *
 * @param success indica se l'operazione ha avuto successo
 * @param message eventuale messaggio esplicativo o di errore
 */
public record ToolResult(
        boolean success,
        String message
) {

    public static ToolResult ok() {
        return new ToolResult(true, null);
    }

    public static ToolResult ok(String message) {
        return new ToolResult(true, message);
    }

    public static ToolResult failure(String message) {
        return new ToolResult(false, message);
    }
}
