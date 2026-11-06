package com.tirocinioaltrama.slmorchestrator.impl.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Guardia anti-"Cache Piena" per chiamate consecutive identiche al runner Ollama.
 * <p>
 * Quando due richieste consecutive hanno esattamente lo stesso prefisso
 * {@code (model + systemPrompt + userPrompt)} — ad esempio la stessa pipeline invocata due volte
 * di fila con lo stesso documento saltando il router — il runner troverebbe l'intero prompt
 * già presente nella KV cache (stato di "Cache Piena"), condizione che su alcuni runner
 * provoca loop di generazione degeneri.
 * <p>
 * La guardia antepone al solo messaggio utente un marcatore di richiesta monotono: il System Prompt
 * resta byte-identico (e quindi riutilizzato dalla KV cache), mentre la cache del payload viene
 * invalidata a partire dal primo token del messaggio utente, garantendo sempre lo stato di
 * <b>Cache Parziale</b>.
 * <p>
 * Thread-safe: lo stato (impronta dell'ultima richiesta e contatore) è protetto da lock intrinseco;
 * l'impronta è un digest SHA-256 a dimensione costante, indipendente dalla lunghezza del payload.
 */
public final class PromptCacheGuard {

    public static final String MARKER_PREFIX = "<request_id>";
    public static final String MARKER_SUFFIX = "</request_id>\n";

    private String lastFingerprint;
    private long busterCounter;

    /**
     * Restituisce il messaggio utente effettivo da inviare: invariato se la richiesta differisce
     * dalla precedente, altrimenti preceduto da un marcatore univoco di cache-busting.
     */
    public synchronized String apply(String model, String systemPrompt, String userPrompt) {
        String fingerprint = fingerprint(model, systemPrompt, userPrompt);
        boolean identicalToPrevious = fingerprint.equals(lastFingerprint);
        lastFingerprint = fingerprint;
        if (!identicalToPrevious) {
            return userPrompt;
        }
        busterCounter++;
        return MARKER_PREFIX + busterCounter + MARKER_SUFFIX + (userPrompt != null ? userPrompt : "");
    }

    /** Azzera lo stato della guardia. */
    public synchronized void reset() {
        lastFingerprint = null;
    }

    private static String fingerprint(String model, String systemPrompt, String userPrompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, model);
            update(digest, systemPrompt);
            update(digest, userPrompt);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile nella JVM", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        // Separatore + lunghezza per evitare collisioni per concatenazione ambigua
        String safe = value != null ? value : "\u0000null";
        digest.update((safe.length() + ":").getBytes(StandardCharsets.UTF_8));
        digest.update(safe.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0x1F);
    }
}
