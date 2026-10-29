package com.tirocinioaltrama.llmorchestrator.impl.pipeline.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.tirocinioaltrama.llmorchestrator.core.exception.PipelineConfigurationException;

/**
 * Risolutore dei System Prompt dichiarati nelle pipeline YAML (chiave {@code system_prompt})
 * e nella configurazione del router.
 * <p>
 * Il valore può essere:
 * <ul>
 *   <li>un riferimento a risorsa: {@code classpath:...}, {@code file:...} oppure un path relativo
 *       senza spazi con estensione {@code .txt}/{@code .prompt}/{@code .md} (risolto in classpath);</li>
 *   <li>un testo inline.</li>
 * </ul>
 * La risoluzione è Fail-Fast: risorsa inesistente, illeggibile o vuota solleva
 * {@link PipelineConfigurationException}.
 */
public final class SystemPromptResolver {

    private static final Pattern RESOURCE_PATH_PATTERN =
            Pattern.compile("^[\\w./\\-]+\\.(txt|prompt|md)$", Pattern.CASE_INSENSITIVE);

    private SystemPromptResolver() {
    }

    public static boolean isResourceReference(String value) {
        if (value == null) {
            return false;
        }
        String v = value.strip();
        return v.startsWith("classpath:") || v.startsWith("file:") || RESOURCE_PATH_PATTERN.matcher(v).matches();
    }

    /**
     * Risolve il system prompt restituendone il contenuto testuale (strip) non vuoto.
     *
     * @param value          valore dichiarato (path o testo inline)
     * @param resourceLoader loader Spring per le risorse
     * @param context        descrizione del contesto per i messaggi d'errore (es. "step 'x' della pipeline 'y'")
     */
    public static String resolve(String value, ResourceLoader resourceLoader, String context) {
        if (value == null || value.isBlank()) {
            throw new PipelineConfigurationException("System prompt vuoto o mancante per " + context + ".");
        }
        if (!isResourceReference(value)) {
            return value.strip();
        }
        String resolvedPath = value.strip();
        if (!resolvedPath.startsWith("classpath:") && !resolvedPath.startsWith("file:")) {
            resolvedPath = "classpath:" + (resolvedPath.startsWith("/") ? resolvedPath.substring(1) : resolvedPath);
        }
        Resource resource = resourceLoader.getResource(resolvedPath);
        if (!resource.exists()) {
            throw new PipelineConfigurationException("Risorsa system prompt non trovata al percorso '" + value
                    + "' per " + context + ".");
        }
        String content;
        try (InputStream is = resource.getInputStream()) {
            content = new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new PipelineConfigurationException("Impossibile leggere la risorsa system prompt '" + value
                    + "' per " + context + ": " + e.getMessage(), e);
        }
        if (content.isEmpty()) {
            throw new PipelineConfigurationException("La risorsa system prompt '" + value + "' per " + context + " è vuota.");
        }
        return content;
    }
}
