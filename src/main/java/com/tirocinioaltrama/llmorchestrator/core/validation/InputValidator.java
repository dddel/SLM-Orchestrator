package com.tirocinioaltrama.llmorchestrator.core.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validatore e sanificatore dell'input in ingresso all'orchestratore.
 * Protegge da payload nulli/vuoti, superamento limiti di dimensione,
 * injection di tag di isolamento (&lt;payload&gt;) e tentativi di prompt injection noti.
 */
public class InputValidator implements Validator<String> {

    public static final int DEFAULT_MAX_LENGTH = 32_768; // 32.768 caratteri max default

    private static final Pattern INJECTION_PATTERNS = Pattern.compile(
            "(?i)(ignore\\s+all\\s+(previous|prior)\\s+instructions"
            + "|disregard\\s+all\\s+instructions"
            + "|system\\s*prompt\\s*override"
            + "|you\\s+are\\s+now\\s+in\\s+(dan|developer)\\s+mode"
            + "|reveal\\s+(the\\s+)?(system\\s+prompt|instructions)"
            + "|output\\s+the\\s+exact\\s+text\\s+above)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern RESERVED_TAG_PATTERN = Pattern.compile(
            "(?i)<\\s*(/)?\\s*(payload|feedback)\\s*>"
    );

    private final int maxLength;

    public InputValidator() {
        this(DEFAULT_MAX_LENGTH);
    }

    public InputValidator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public ValidationResult validate(String input) {
        if (input == null || input.isBlank()) {
            return ValidationResult.error("Il payload di input non può essere nullo o vuoto.");
        }

        if (input.length() > maxLength) {
            return ValidationResult.error(
                    "Il payload supera la dimensione massima consentita di " + maxLength + " caratteri (ricevuti: " + input.length() + ")."
            );
        }

        List<String> warnings = new ArrayList<>();
        if (INJECTION_PATTERNS.matcher(input).find()) {
            warnings.add("Rilevato possibile pattern di prompt injection nell'input. Il testo verrà comunque incapsulato e isolato.");
        }

        if (RESERVED_TAG_PATTERN.matcher(input).find()) {
            warnings.add("Il payload contiene tag XML di delimitazione riservati che verranno neutralizzati.");
        }

        return warnings.isEmpty() ? ValidationResult.valid() : ValidationResult.withWarnings(warnings);
    }

    /**
     * Sanifica il testo grezzo rimuovendo/sostituendo tag riservati che potrebbero
     * rompere il confinamento &lt;payload&gt;...&lt;/payload&gt; o simulare feedback ingannevole nel Modelfile.
     *
     * @param input testo grezzo
     * @return testo sanificato
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return RESERVED_TAG_PATTERN.matcher(input)
                .replaceAll(mr -> (mr.group(1) != null ? "[/" : "[") + mr.group(2).toLowerCase() + "]")
                .strip();
    }
}
