package com.tirocinioaltrama.slmorchestrator.impl.pipeline.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tirocinioaltrama.slmorchestrator.core.DocumentType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Guardie simboliche deterministiche di post-processing (Fase 3 & Consolidamento):
 * - G1: Normalizzazione etichette descrittive e onorifici su denominazioni fornitore e cliente.
 * - G2: Guardia di evidenza lessicale (soglia di copertura token >= 60%) e vincolo di lunghezza (<= 80) su enteRilascio.
 * - Evidenza Sesso: Annullamento di sesso se nel payload sorgente non compare 'sesso' (GROUND_TRUTH_RULES Sez 1.1).
 * - G3: Recupero omissioni su campi codificati (dataRilascio patente 4a e sigla provincia luogoNascita).
 * - G4: Normalizzazione enum tipoDocumento sul set canonico.
 * - Date: Guardia tipizzata su campi data che annulla a null stringhe discorsive prive di cifre numeriche.
 */
public final class PostProcessingGuards {

    private static final Pattern PREFIX_LABEL_PATTERN = Pattern.compile(
            "^(?:" +
            "societ[àa]\\s+fornitrice\\s*[:\\-–]\\s*|" +
            "(?:destinatario|fornitore|cliente|emittente|committente|professionista|cedente(?:\\s+prestatore)?|cessionario(?:\\s+committente)?)\\s*[:\\-–]\\s*" +
            ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern HONORIFICS_PATTERN = Pattern.compile(
            "^(spett\\.?le|spettabile|egr\\.?|egregio|gent\\.?le|gentile|dott\\.?ssa|dottore|dott\\.?|sig\\.?ra|signora|sig\\.?|signor|avv\\.?|avvocato|ing\\.?|ingegnere|arch\\.?|architetto|rag\\.?|ragioniere|geom\\.?|geometra|prof\\.?ssa|professore|prof\\.?|dr\\.?ssa|dr\\.?)\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern DATE_INCOMPATIBLE_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})\\s*$"
    );

    private static final Pattern PATENTE_4A_PATTERN = Pattern.compile(
            "\\b4a\\.\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SIGNIFICANT_TOKEN_PATTERN = Pattern.compile(
            "[a-zA-ZÀ-ÖØ-öø-ÿ]{3,}"
    );

    private static final Set<String> STOPWORDS = Set.of(
            "del", "della", "dello", "dei", "degli", "delle", "con", "per",
            "dal", "dalla", "dallo", "dai", "dagli", "dalle",
            "nel", "nella", "nello", "nei", "negli", "nelle",
            "sul", "sulla", "sullo", "sui", "sugli", "sulle",
            "all", "alla", "allo", "agli", "alle"
    );

    private static final Pattern SESSO_PATTERN = Pattern.compile(
            "\\bsesso\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Set<String> DATE_FIELDS = Set.of(
            "dataFattura", "dataNascita", "dataRilascio", "dataScadenza", "dataEvento"
    );

    private static final Set<String> CANONICAL_TIPO_DOCUMENTO = Set.of(
            "CARTA_IDENTITA",
            "PASSAPORTO",
            "TESSERA_SANITARIA",
            "PATENTE",
            "NON_SPECIFICATO"
    );

    private static final Map<String, String> TIPO_DOCUMENTO_SYNONYMS = Map.ofEntries(
            Map.entry("PATENTE_GUIDA", "PATENTE"),
            Map.entry("PATENTE_DI_GUIDA", "PATENTE"),
            Map.entry("PATENTE", "PATENTE"),
            Map.entry("CARTA_IDENTITA", "CARTA_IDENTITA"),
            Map.entry("CARTA_D_IDENTITA", "CARTA_IDENTITA"),
            Map.entry("CARTA_DIDENTITA", "CARTA_IDENTITA"),
            Map.entry("CIE", "CARTA_IDENTITA"),
            Map.entry("DOCUMENTO_IDENTITA", "CARTA_IDENTITA"),
            Map.entry("DOCUMENTO_D_IDENTITA", "CARTA_IDENTITA"),
            Map.entry("PASSAPORTO", "PASSAPORTO"),
            Map.entry("TESSERA_SANITARIA", "TESSERA_SANITARIA"),
            Map.entry("CARTA_SANITARIA", "TESSERA_SANITARIA"),
            Map.entry("TESSERA_SANITARIA_TEAM", "TESSERA_SANITARIA"),
            Map.entry("CF", "TESSERA_SANITARIA"),
            Map.entry("CODICE_FISCALE", "TESSERA_SANITARIA"),
            Map.entry("NON_SPECIFICATO", "NON_SPECIFICATO"),
            Map.entry("CERTIFICATO_ANAGRAFICO", "NON_SPECIFICATO"),
            Map.entry("ALTRO", "NON_SPECIFICATO"),
            Map.entry("NON_DEFINITO", "NON_SPECIFICATO")
    );

    private PostProcessingGuards() {
    }

    /**
     * Applica tutte le guardie di post-processing deterministiche sul nodo radice JSON.
     * Restituisce true se almeno un campo è stato modificato, false altrimenti.
     */
    public static boolean applyGuards(JsonNode rootNode, String payload, DocumentType documentType) {
        if (rootNode == null || !rootNode.isObject()) {
            return false;
        }
        ObjectNode root = (ObjectNode) rootNode;
        boolean modified = false;

        // G1: Normalizzazione etichette denominazioni
        if (guardG1StripLabels(root)) {
            modified = true;
        }

        // G2: Evidenza lessicale (copertura >= 60%) e pattern data su enteRilascio
        if (guardG2EnteRilascio(root, payload)) {
            modified = true;
        }

        // Evidenza lessicale sesso (GROUND_TRUTH_RULES Sez 1.1)
        if (guardSessoLexicalEvidence(root, payload)) {
            modified = true;
        }

        // G3: Recupero omissioni codificate (dataRilascio da 4a., provincia in luogoNascita, CF da 4d.)
        if (guardG3OmissionRecovery(root, payload, documentType)) {
            modified = true;
        }

        // G4: Normalizzazione enum tipoDocumento
        if (guardG4EnumTipoDocumento(root)) {
            modified = true;
        }

        // Pulizia prefissi numerici su anagrafica (es. 1. ROSSI, 2. MARIO)
        if (guardStripNumberingPrefixes(root, documentType)) {
            modified = true;
        }

        // Guardia tipizzata sui campi data (dataFattura, dataNascita, dataRilascio, dataScadenza, dataEvento)
        if (guardDateFields(root)) {
            modified = true;
        }

        return modified;
    }

    public static String cleanLabelAndHonorifics(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String orig = value.strip();
        String s = orig;
        boolean matched = true;
        while (matched) {
            matched = false;
            Matcher mHonorific = HONORIFICS_PATTERN.matcher(s);
            if (mHonorific.find() && mHonorific.start() == 0) {
                s = s.substring(mHonorific.end()).strip();
                matched = true;
            }
            Matcher mLabel = PREFIX_LABEL_PATTERN.matcher(s);
            if (mLabel.find() && mLabel.start() == 0) {
                s = s.substring(mLabel.end()).strip();
                matched = true;
            }
            String cleanedLeading = s.replaceAll("^[\\s,.:\\-–]+", "").strip();
            if (!cleanedLeading.equals(s)) {
                s = cleanedLeading;
                matched = true;
            }
        }
        // Fallback anti-svuotamento: non sovrascrivere mai con null o vuoto
        if (s.isBlank()) {
            return orig;
        }
        return s;
    }

    public static boolean guardG1StripLabels(ObjectNode root) {
        boolean changed = false;

        // Struttura annidata fornitore.denominazione
        if (root.has("fornitore") && root.get("fornitore").isObject()) {
            ObjectNode fornitore = (ObjectNode) root.get("fornitore");
            if (fornitore.has("denominazione") && fornitore.get("denominazione").isTextual()) {
                String orig = fornitore.get("denominazione").asString();
                String cleaned = cleanLabelAndHonorifics(orig);
                if (cleaned != null && !cleaned.isBlank() && !cleaned.equals(orig)) {
                    fornitore.put("denominazione", cleaned);
                    changed = true;
                }
            }
        }

        // Struttura annidata cliente.denominazione
        if (root.has("cliente") && root.get("cliente").isObject()) {
            ObjectNode cliente = (ObjectNode) root.get("cliente");
            if (cliente.has("denominazione") && cliente.get("denominazione").isTextual()) {
                String orig = cliente.get("denominazione").asString();
                String cleaned = cleanLabelAndHonorifics(orig);
                if (cleaned != null && !cleaned.isBlank() && !cleaned.equals(orig)) {
                    cliente.put("denominazione", cleaned);
                    changed = true;
                }
            }
        }

        // Struttura piatta
        if (root.has("fornitore.denominazione") && root.get("fornitore.denominazione").isTextual()) {
            String orig = root.get("fornitore.denominazione").asString();
            String cleaned = cleanLabelAndHonorifics(orig);
            if (cleaned != null && !cleaned.isBlank() && !cleaned.equals(orig)) {
                root.put("fornitore.denominazione", cleaned);
                changed = true;
            }
        }
        if (root.has("cliente.denominazione") && root.get("cliente.denominazione").isTextual()) {
            String orig = root.get("cliente.denominazione").asString();
            String cleaned = cleanLabelAndHonorifics(orig);
            if (cleaned != null && !cleaned.isBlank() && !cleaned.equals(orig)) {
                root.put("cliente.denominazione", cleaned);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Guardia G2 su enteRilascio:
     * 1. Vincolo di lunghezza massima <= 80 caratteri (anti-loop degenerativi).
     * 2. Annullamento token null generici ("null", "none", "n/a", "undefined").
     * 3. Pattern incompatibile: data invece di ente.
     * 4. Soglia di copertura lessicale: almeno il 60% dei token significativi (>= 3 char, no stopwords)
     *    deve comparire nel payload sorgente (case-insensitive).
     */
    public static boolean guardG2EnteRilascio(ObjectNode root, String payload) {
        if (!root.has("enteRilascio")) {
            return false;
        }
        JsonNode enteNode = root.get("enteRilascio");
        if (enteNode == null || enteNode.isNull()) {
            return false;
        }
        if (!enteNode.isTextual()) {
            root.putNull("enteRilascio");
            return true;
        }

        String enteStr = enteNode.asString().strip();
        if (enteStr.isBlank()
                || "null".equalsIgnoreCase(enteStr)
                || "none".equalsIgnoreCase(enteStr)
                || "n/a".equalsIgnoreCase(enteStr)
                || "undefined".equalsIgnoreCase(enteStr)) {
            root.putNull("enteRilascio");
            return true;
        }

        // 1. Vincolo di lunghezza massima contro loop generativi infiniti (caso ANA_19)
        if (enteStr.length() > 80) {
            root.putNull("enteRilascio");
            return true;
        }

        // 2. Pattern incompatibile: data invece di ente
        if (DATE_INCOMPATIBLE_PATTERN.matcher(enteStr).matches()) {
            root.putNull("enteRilascio");
            return true;
        }

        // 3. Evidenza lessicale con soglia di copertura token >= 60%
        String cleanPayload = payload != null
                ? payload.replaceFirst("(?i)^\\s*MINISTERO\\s+DELL[''\\s]+INTERNO\\b", "")
                : "";
        String cleanLower = cleanPayload.toLowerCase();

        Matcher matcher = SIGNIFICANT_TOKEN_PATTERN.matcher(enteStr);
        List<String> significantTokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group().toLowerCase();
            if (!STOPWORDS.contains(token)) {
                significantTokens.add(token);
            }
        }

        if (significantTokens.isEmpty()) {
            root.putNull("enteRilascio");
            return true;
        }

        int matchedCount = 0;
        for (String token : significantTokens) {
            if (cleanLower.contains(token)) {
                matchedCount++;
            }
        }

        double coverage = (double) matchedCount / significantTokens.size();
        if (coverage < 0.60) {
            root.putNull("enteRilascio");
            return true;
        }

        return false;
    }

    /**
     * Nuova guardia tipizzata sui campi data:
     * Per tutti i campi data noti (dataFattura, dataNascita, dataRilascio, dataScadenza, dataEvento),
     * se il valore estratto non è nullo ma non contiene alcuna cifra numerica (0-9)
     * (es. stringhe discorsive come "tempo indeterminato", "non specificato", "n/a"),
     * sovrascrive il nodo a null.
     */
    public static boolean guardDateFields(JsonNode node) {
        if (node == null) {
            return false;
        }
        boolean changed = false;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            for (String fieldName : DATE_FIELDS) {
                if (obj.has(fieldName) && !obj.get(fieldName).isNull()) {
                    JsonNode valNode = obj.get(fieldName);
                    if (valNode.isTextual()) {
                        String text = valNode.asString().strip();
                        if (!text.matches(".*\\d.*")) {
                            obj.putNull(fieldName);
                            changed = true;
                        }
                    }
                }
            }
            // Ispezione ricorsiva di eventuali sotto-oggetti o array
            for (JsonNode child : obj) {
                if (child.isObject() || child.isArray()) {
                    if (guardDateFields(child)) {
                        changed = true;
                    }
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                if (guardDateFields(element)) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    public static boolean guardSessoLexicalEvidence(ObjectNode root, String payload) {
        if (!root.has("sesso")) {
            return false;
        }
        JsonNode sessoNode = root.get("sesso");
        if (sessoNode == null || sessoNode.isNull()) {
            return false;
        }

        // Se non compare la parola 'sesso' nel payload sorgente, annulla a null
        if (payload == null || !SESSO_PATTERN.matcher(payload).find()) {
            root.putNull("sesso");
            return true;
        }

        return false;
    }

    public static boolean guardG3OmissionRecovery(ObjectNode root, String payload) {
        return guardG3OmissionRecovery(root, payload, DocumentType.ANAGRAFICA);
    }

    public static boolean guardG3OmissionRecovery(ObjectNode root, String payload, DocumentType documentType) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        boolean changed = false;

        // 1. Recupero dataRilascio da pattern patente UE 4a. (solo per ANAGRAFICA)
        if (documentType == DocumentType.ANAGRAFICA) {
            boolean needDataRilascio = !root.has("dataRilascio")
                    || root.get("dataRilascio").isNull()
                    || (root.get("dataRilascio").isTextual() && root.get("dataRilascio").asString().isBlank());
            if (needDataRilascio) {
                Matcher m = PATENTE_4A_PATTERN.matcher(payload);
                if (m.find()) {
                    root.put("dataRilascio", m.group(1));
                    changed = true;
                }
            }
        }

        // 2. Integrazione sigla provinciale su luogoNascita (solo per ANAGRAFICA)
        if (documentType == DocumentType.ANAGRAFICA && root.has("luogoNascita") && root.get("luogoNascita").isTextual()) {
            String luogo = root.get("luogoNascita").asString().strip();
            if (!luogo.isBlank() && !Pattern.compile("\\([A-Za-z]{2}\\)\\s*$").matcher(luogo).find()) {
                Pattern provPattern = Pattern.compile("\\b" + Pattern.quote(luogo) + "\\s*\\(([A-Za-z]{2})\\)", Pattern.CASE_INSENSITIVE);
                Matcher provMatcher = provPattern.matcher(payload);
                if (provMatcher.find()) {
                    String prov = provMatcher.group(1).toUpperCase();
                    root.put("luogoNascita", luogo + " (" + prov + ")");
                    changed = true;
                }
            }
        }

        // 3. Recupero Codice Fiscale da pattern patente UE 4d. (SOLO per ANAGRAFICA)
        if (documentType == DocumentType.ANAGRAFICA) {
            boolean needCf = !root.has("codiceFiscale")
                    || root.get("codiceFiscale").isNull()
                    || (root.get("codiceFiscale").isTextual() && root.get("codiceFiscale").asString().isBlank());
            if (needCf) {
                Matcher m = Pattern.compile("\\b4d\\.\\s*([A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPR-T][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z])\\b", Pattern.CASE_INSENSITIVE).matcher(payload);
                if (m.find()) {
                    root.put("codiceFiscale", m.group(1).toUpperCase());
                    changed = true;
                }
            }
        }

        return changed;
    }

    public static boolean guardG4EnumTipoDocumento(ObjectNode root) {
        if (!root.has("tipoDocumento") || !root.get("tipoDocumento").isTextual()) {
            return false;
        }
        String orig = root.get("tipoDocumento").asString();
        String td = orig.strip().toUpperCase().replaceAll("[\\s\\-]+", "_");
        String target = null;
        if (TIPO_DOCUMENTO_SYNONYMS.containsKey(td)) {
            target = TIPO_DOCUMENTO_SYNONYMS.get(td);
        } else if (CANONICAL_TIPO_DOCUMENTO.contains(td)) {
            target = td;
        }

        if (target != null && !target.equals(orig)) {
            root.put("tipoDocumento", target);
            return true;
        }
        return false;
    }

    private static final java.util.List<String> ANAGRAFICA_NUMBERED_FIELDS = java.util.List.of(
            "nome", "cognome", "dataNascita", "luogoNascita", "numeroDocumento", "dataRilascio", "dataScadenza", "enteRilascio"
    );

    public static boolean guardStripNumberingPrefixes(ObjectNode root, DocumentType documentType) {
        if (documentType != DocumentType.ANAGRAFICA) {
            return false;
        }
        boolean changed = false;
        for (String field : ANAGRAFICA_NUMBERED_FIELDS) {
            if (root.has(field) && root.get(field).isTextual()) {
                String orig = root.get(field).asString();
                String cleaned = orig.replaceFirst("^[0-9]+[a-z]?\\.(?!\\d)\\s*", "").strip();
                if (!cleaned.equals(orig)) {
                    root.put(field, cleaned);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
