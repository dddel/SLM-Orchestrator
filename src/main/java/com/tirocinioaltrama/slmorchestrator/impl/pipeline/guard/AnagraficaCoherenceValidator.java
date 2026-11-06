package com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Validatore deterministico di coerenza semantica anagrafica (Fase 4):
 * - V1: Coerenza Layout Numerato UE (1. Cognome, 2. Nome).
 * - V2: Coerenza Cross-Field Codice Fiscale <-> (Cognome, Nome).
 * In caso di violazione, genera l'errore FIELD_SWAP_DETECTED per innescare
 * il loop di retry RF7 a zero iniezione di valori rifiutati.
 */
public final class AnagraficaCoherenceValidator {

    private static final Set<Character> VOWELS = Set.of('A', 'E', 'I', 'O', 'U');

    private static final Pattern UE_LICENCE_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*1\\.\\s*([A-Za-z\\s'\\-]+?)\\s+2\\.\\s*([A-Za-z\\s'\\-]+?)(?:\\n|\\b3\\.|\\b4a\\.|$)",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[] EXCLUDED_CF_PREFIXES = {"COGNOM", "CFXUSR"};

    public record CoherenceViolation(String path, String errorCode, String errorMessage) {}

    public record CoherenceCheckResult(boolean isValid, List<CoherenceViolation> violations) {
        public static CoherenceCheckResult valid() {
            return new CoherenceCheckResult(true, List.of());
        }

        public static CoherenceCheckResult invalid(List<CoherenceViolation> violations) {
            return new CoherenceCheckResult(false, List.copyOf(violations));
        }

        public static CoherenceCheckResult invalid(String path, String errorCode, String errorMessage) {
            return new CoherenceCheckResult(false, List.of(new CoherenceViolation(path, errorCode, errorMessage)));
        }

        public String errorCode() {
            return violations.isEmpty() ? null : violations.get(0).errorCode();
        }

        public String errorMessage() {
            return violations.isEmpty() ? null : violations.get(0).errorMessage();
        }
    }

    private AnagraficaCoherenceValidator() {
    }

    public static CoherenceCheckResult validate(JsonNode root, String payload) {
        if (root == null || !root.isObject()) {
            return CoherenceCheckResult.valid();
        }

        List<CoherenceViolation> violations = new ArrayList<>();

        String nome = root.has("nome") && root.get("nome").isTextual() ? root.get("nome").asString().strip() : null;
        String cognome = root.has("cognome") && root.get("cognome").isTextual() ? root.get("cognome").asString().strip() : null;
        String cf = root.has("codiceFiscale") && root.get("codiceFiscale").isTextual() ? root.get("codiceFiscale").asString().strip() : null;
        String numDoc = root.has("numeroDocumento") && root.get("numeroDocumento").isTextual() ? root.get("numeroDocumento").asString().strip() : null;

        // 0. Verifica formale che nome e cognome non contengano cifre numeriche (es. numero documento finito nel nome)
        // I placeholder sintetici di baseline (Nome1, Cognome1) sono consentiti per retrocompatibilità testuale
        if (nome != null && nome.matches(".*\\d.*") && !nome.matches("(?i)^(?:Nome|Cognome)\\d+$")) {
            violations.add(new CoherenceViolation(
                    "nome",
                    "FIELD_SWAP_DETECTED",
                    "Il campo 'nome' contiene cifre numeriche non ammesse ('" + nome + "'), indice di slittamento di codici documento."
            ));
        }
        if (cognome != null && cognome.matches(".*\\d.*") && !cognome.matches("(?i)^(?:Nome|Cognome)\\d+$")) {
            violations.add(new CoherenceViolation(
                    "cognome",
                    "FIELD_SWAP_DETECTED",
                    "Il campo 'cognome' contiene cifre numeriche non ammesse ('" + cognome + "'), indice di slittamento di codici documento."
            ));
        }

        // 0b. Verifica che numeroDocumento non contenga etichette di layout (es. '4a', '4a.') o un Codice Fiscale
        // Le etichette di layout sono marcatori brevi (es. 4a, 5., 1.) e non codici numerici estesi (come i 20 caratteri della Tessera Sanitaria)
        if (numDoc != null && !numDoc.isBlank()) {
            if (numDoc.matches("(?i)^[0-9]{1,2}[a-z]?\\.?$") || (numDoc.length() < 3 && !numDoc.matches("^[0-9]+$"))) {
                violations.add(new CoherenceViolation(
                        "numeroDocumento",
                        "FIELD_SWAP_DETECTED",
                        "Il campo 'numeroDocumento' contiene un'etichetta di layout ('" + numDoc + "') anziché un codice identificativo valido."
                ));
            } else if (numDoc.matches("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPR-T][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$")) {
                violations.add(new CoherenceViolation(
                        "numeroDocumento",
                        "FIELD_SWAP_DETECTED",
                        "Il campo 'numeroDocumento' contiene un Codice Fiscale ('" + numDoc + "') anziché il numero del documento."
                ));
            }
        }

        // 0c. Controllo di attestazione lessicale a parole intere (Whole-Word Evidence) su nome e cognome
        if (payload != null && !payload.isBlank()) {
            if (nome != null && !nome.isBlank() && !nome.matches(".*\\d.*")) {
                String cleanNome = stripNumberingPrefix(nome);
                checkWholeWordEvidence("nome", cleanNome, payload, violations);
            }
            if (cognome != null && !cognome.isBlank() && !cognome.matches(".*\\d.*")) {
                String cleanCognome = stripNumberingPrefix(cognome);
                checkWholeWordEvidence("cognome", cleanCognome, payload, violations);
            }
        }

        // 0d. Controllo di coerenza cronologica date: dataNascita < dataRilascio <= dataScadenza
        checkChronologicalDateOrder(root, violations);

        // V1: Coerenza Layout UE
        if (checkUeLicenceSwap(payload, nome, cognome)) {
            violations.add(new CoherenceViolation(
                    "nome",
                    "FIELD_SWAP_DETECTED",
                    "Rilevata inversione nome/cognome rispetto alla numerazione UE (1. Cognome, 2. Nome)."
            ));
        }

        // V2: Coerenza Cross-Field Codice Fiscale
        if (checkCfCrossFieldSwap(cf, nome, cognome)) {
            violations.add(new CoherenceViolation(
                    "codiceFiscale",
                    "FIELD_SWAP_DETECTED",
                    "Rilevata incoerenza o inversione nome/cognome rispetto alla struttura dei primi 6 caratteri del Codice Fiscale."
            ));
        }

        if (violations.isEmpty()) {
            return CoherenceCheckResult.valid();
        }
        return CoherenceCheckResult.invalid(violations);
    }

    public static String stripNumberingPrefix(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceFirst("^[0-9]+[a-z]?\\.(?!\\d)\\s*", "").strip();
    }

    public static boolean checkUeLicenceSwap(String payload, String nome, String cognome) {
        if (payload == null || nome == null || cognome == null || nome.isBlank() || cognome.isBlank()) {
            return false;
        }

        Matcher m = UE_LICENCE_PATTERN.matcher(payload);
        if (!m.find()) {
            return false;
        }

        String f1 = stripNumberingPrefix(m.group(1)).toUpperCase();
        String f2 = stripNumberingPrefix(m.group(2)).toUpperCase();
        String nUp = stripNumberingPrefix(nome).toUpperCase();
        String cUp = stripNumberingPrefix(cognome).toUpperCase();

        return nUp.equals(f1) && cUp.equals(f2) && !f1.equals(f2);
    }

    public static boolean checkCfCrossFieldSwap(String cf, String nome, String cognome) {
        if (cf == null || cf.length() < 6 || nome == null || cognome == null || nome.isBlank() || cognome.isBlank()) {
            return false;
        }

        String cf6 = cf.substring(0, 6).toUpperCase();
        for (String prefix : EXCLUDED_CF_PREFIXES) {
            if (cf6.startsWith(prefix)) {
                return false;
            }
        }

        String cleanNome = stripNumberingPrefix(nome);
        String cleanCognome = stripNumberingPrefix(cognome);

        String expCog = computeCfSurnameCode(cleanCognome);
        String expNom = computeCfNameCode(cleanNome);
        String expCf6 = expCog + expNom;

        String invCog = computeCfSurnameCode(cleanNome);
        String invNom = computeCfNameCode(cleanCognome);
        String invCf6 = invCog + invNom;

        // 1. Swap speculare esatto (CF = codice_da_nome + codice_da_cognome)
        if (cf6.equals(invCf6) && !cf6.equals(expCf6)) {
            return true;
        }

        // 2. Controllo di coerenza incrociata / inversione parziale:
        String cfSurname3 = cf6.substring(0, 3);
        String cfName3 = cf6.substring(3, 6);
        boolean cognomeIsActuallyName = expCog.equals(cfName3) && !expCog.equals(cfSurname3);
        boolean nomeIsActuallySurname = expNom.equals(cfSurname3) && !expNom.equals(cfName3);

        if (cognomeIsActuallyName || nomeIsActuallySurname) {
            return true;
        }

        return false;
    }

    public static String computeCfSurnameCode(String surname) {
        if (surname == null || surname.isBlank()) {
            return "XXX";
        }
        Letters letters = extractConsonantsVowels(surname);
        List<Character> all = new ArrayList<>(letters.consonants);
        all.addAll(letters.vowels);
        all.add('X');
        all.add('X');
        all.add('X');

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(all.get(i));
        }
        return sb.toString();
    }

    public static String computeCfNameCode(String name) {
        if (name == null || name.isBlank()) {
            return "XXX";
        }
        Letters letters = extractConsonantsVowels(name);
        List<Character> selected = new ArrayList<>();
        if (letters.consonants.size() >= 4) {
            selected.add(letters.consonants.get(0));
            selected.add(letters.consonants.get(2));
            selected.add(letters.consonants.get(3));
        } else {
            selected.addAll(letters.consonants);
            selected.addAll(letters.vowels);
            selected.add('X');
            selected.add('X');
            selected.add('X');
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            sb.append(selected.get(i));
        }
        return sb.toString();
    }

    private static void checkWholeWordEvidence(String fieldName, String value, String payload, List<CoherenceViolation> violations) {
        if (value == null || value.isBlank() || payload == null || payload.isBlank()) {
            return;
        }
        // Attiva il controllo solo se il payload contiene attestazioni o etichette anagrafiche nel testo
        String lowerPayload = payload.toLowerCase();
        boolean hasAnagraficaLabels = lowerPayload.contains("cognome") || lowerPayload.contains("nome");
        if (!hasAnagraficaLabels) {
            return;
        }

        String[] tokens = value.split("\\s+");
        for (String token : tokens) {
            String cleanToken = token.replaceAll("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$", "").strip();
            if (cleanToken.length() >= 3) {
                // 1. Rileva troncamento o refuso generativo (es. "Bianch" quando nel testo compare "BIANCHI")
                if (Pattern.compile("(?i)(?U)\\b" + Pattern.quote(cleanToken) + "\\p{L}+\\b").matcher(payload).find()) {
                    violations.add(new CoherenceViolation(
                            fieldName,
                            "FIELD_SWAP_DETECTED",
                            "Il campo '" + fieldName + "' ('" + value + "') presenta un troncamento o refuso rispetto al termine originale presente nel documento."
                    ));
                    continue;
                }
                // 2. Rileva token non attestato come parola intera nel testo
                if (!isWordTokenPresentInPayload(cleanToken, payload)) {
                    violations.add(new CoherenceViolation(
                            fieldName,
                            "FIELD_SWAP_DETECTED",
                            "Il campo '" + fieldName + "' ('" + value + "') contiene il termine '" + cleanToken + "' non attestato come parola intera nel testo sorgente."
                    ));
                }
            }
        }
    }

    private static boolean isWordTokenPresentInPayload(String token, String payload) {
        if (token == null || token.isBlank() || payload == null) {
            return false;
        }
        // Verifica token intero circondato da delimitatori non alfabetici o inizio/fine stringa
        String regex = "(?i)(?U)(?:^|[^\\p{L}\\p{N}])" + Pattern.quote(token) + "(?:[^\\p{L}\\p{N}]|$)";
        if (Pattern.compile(regex).matcher(payload).find()) {
            return true;
        }
        // Se il token contiene apostrofo (es. D'ANGELO), verifica anche le singole parti di lunghezza >= 2
        if (token.contains("'") || token.contains("’")) {
            String[] parts = token.split("['’]");
            for (String part : parts) {
                String p = part.strip();
                if (p.length() >= 2) {
                    String subRegex = "(?i)(?U)(?:^|[^\\p{L}\\p{N}])" + Pattern.quote(p) + "(?:[^\\p{L}\\p{N}]|$)";
                    if (Pattern.compile(subRegex).matcher(payload).find()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void checkChronologicalDateOrder(JsonNode root, List<CoherenceViolation> violations) {
        LocalDate birth = parseDate(root.has("dataNascita") ? root.get("dataNascita").asText(null) : null);
        LocalDate release = parseDate(root.has("dataRilascio") ? root.get("dataRilascio").asText(null) : null);
        LocalDate expiry = parseDate(root.has("dataScadenza") ? root.get("dataScadenza").asText(null) : null);

        if (birth != null && release != null && birth.isAfter(release)) {
            violations.add(new CoherenceViolation(
                    "dataNascita",
                    "FIELD_SWAP_DETECTED",
                    "Incoerenza cronologica: la data di nascita (" + birth + ") è posteriore alla data di rilascio (" + release + ")."
            ));
        }
        if (release != null && expiry != null && release.isAfter(expiry)) {
            violations.add(new CoherenceViolation(
                    "dataRilascio",
                    "FIELD_SWAP_DETECTED",
                    "Incoerenza cronologica: la data di rilascio (" + release + ") è posteriore alla data di scadenza (" + expiry + ")."
            ));
        }
        if (birth != null && expiry != null && birth.isAfter(expiry)) {
            violations.add(new CoherenceViolation(
                    "dataNascita",
                    "FIELD_SWAP_DETECTED",
                    "Incoerenza cronologica: la data di nascita (" + birth + ") è posteriore alla data di scadenza (" + expiry + ")."
            ));
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("null")) {
            return null;
        }
        String clean = stripNumberingPrefix(text).strip();
        if (clean.contains("T")) {
            clean = clean.substring(0, clean.indexOf('T')).strip();
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/yyyy"),
                DateTimeFormatter.ofPattern("d-M-yyyy"),
                DateTimeFormatter.ofPattern("yyyy/M/d")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(clean, fmt);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private record Letters(List<Character> consonants, List<Character> vowels) {}

    private static Letters extractConsonantsVowels(String s) {
        String normalized = Normalizer.normalize(s.toUpperCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String cleaned = normalized.replaceAll("[^A-Z]", "");
        List<Character> cons = new ArrayList<>();
        List<Character> vows = new ArrayList<>();
        for (char c : cleaned.toCharArray()) {
            if (VOWELS.contains(c)) {
                vows.add(c);
            } else {
                cons.add(c);
            }
        }
        return new Letters(cons, vows);
    }
}
