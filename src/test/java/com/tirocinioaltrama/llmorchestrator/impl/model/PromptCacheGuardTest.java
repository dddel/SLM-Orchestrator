package com.tirocinioaltrama.llmorchestrator.impl.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PromptCacheGuardTest {

    private PromptCacheGuard cacheGuard;

    @BeforeEach
    void setUp() {
        cacheGuard = new PromptCacheGuard();
    }

    @Test
    @DisplayName("La prima invocazione restituisce il userPrompt invariato")
    void primaInvocazioneRestituisceUserPromptInvariato() {
        String model = "qwen2.5:3b";
        String systemPrompt = "Sei un modulo di estrazione.";
        String userPrompt = "<payload>Dati fattura</payload>";

        String result = cacheGuard.apply(model, systemPrompt, userPrompt);
        assertEquals(userPrompt, result);
    }

    @Test
    @DisplayName("Due invocazioni consecutive identiche attivano il marcatore cache-buster sul solo userPrompt")
    void dueInvocazioniConsecutiveIdenticheAttivanoCacheBuster() {
        String model = "qwen2.5:3b";
        String systemPrompt = "Sei un modulo di estrazione.";
        String userPrompt = "<payload>Dati fattura identici</payload>";

        String first = cacheGuard.apply(model, systemPrompt, userPrompt);
        assertEquals(userPrompt, first);

        String second = cacheGuard.apply(model, systemPrompt, userPrompt);
        assertTrue(second.startsWith(PromptCacheGuard.MARKER_PREFIX));
        assertTrue(second.contains(PromptCacheGuard.MARKER_SUFFIX));
        assertTrue(second.endsWith(userPrompt));
        assertTrue(second.contains("<request_id>1</request_id>"));

        String third = cacheGuard.apply(model, systemPrompt, userPrompt);
        assertTrue(third.contains("<request_id>2</request_id>"));
    }

    @Test
    @DisplayName("Il marcatore viene inserito all'INIZIO del messaggio utente prima di <feedback> e <payload>")
    void marcatoreInseritoAllInizioPrimaDiFeedbackEPayload() {
        String model = "qwen2.5:3b-instruct-q8_0";
        String systemPrompt = "Sei un estrattore.";
        String userPrompt = "<feedback>\nCorreggi importo\n</feedback>\n<payload>\nFattura 123\n</payload>";

        String first = cacheGuard.apply(model, systemPrompt, userPrompt);
        assertEquals(userPrompt, first);

        String second = cacheGuard.apply(model, systemPrompt, userPrompt);
        assertTrue(second.startsWith("<request_id>1</request_id>\n"));
        int markerIdx = second.indexOf("<request_id>");
        int feedbackIdx = second.indexOf("<feedback>");
        int payloadIdx = second.indexOf("<payload>");
        assertTrue(markerIdx < feedbackIdx, "Il marcatore deve precedere <feedback>");
        assertTrue(feedbackIdx < payloadIdx, "<feedback> deve precedere <payload>");
    }

    @Test
    @DisplayName("Una richiesta con prompt utente diverso non attiva il cache-buster")
    void richiestaDiversaNonAttivaCacheBuster() {
        String model = "qwen2.5:3b";
        String systemPrompt = "Sei un modulo di estrazione.";
        String userPrompt1 = "<payload>Dati fattura 1</payload>";
        String userPrompt2 = "<payload>Dati fattura 2</payload>";

        assertEquals(userPrompt1, cacheGuard.apply(model, systemPrompt, userPrompt1));
        assertEquals(userPrompt2, cacheGuard.apply(model, systemPrompt, userPrompt2));
    }

    @Test
    @DisplayName("Una richiesta con system prompt diverso o modello diverso non attiva il cache-buster")
    void richiestaConSystemPromptOModelloDiversoNonAttivaCacheBuster() {
        String model1 = "qwen2.5:3b";
        String model2 = "qwen2.5:7b";
        String systemPrompt1 = "System prompt 1";
        String systemPrompt2 = "System prompt 2";
        String userPrompt = "<payload>Dati</payload>";

        assertEquals(userPrompt, cacheGuard.apply(model1, systemPrompt1, userPrompt));
        assertEquals(userPrompt, cacheGuard.apply(model1, systemPrompt2, userPrompt));
        assertEquals(userPrompt, cacheGuard.apply(model2, systemPrompt2, userPrompt));
    }

    @Test
    @DisplayName("Reset azzera lo stato della guardia")
    void resetAzzeraStato() {
        String model = "qwen2.5:3b";
        String systemPrompt = "System prompt";
        String userPrompt = "<payload>Dati</payload>";

        assertEquals(userPrompt, cacheGuard.apply(model, systemPrompt, userPrompt));
        cacheGuard.reset();
        assertEquals(userPrompt, cacheGuard.apply(model, systemPrompt, userPrompt));
    }
}
