package com.tirocinioaltrama.slmorchestrator.eval;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Comparatore Ground Truth in Java conforme al 100% con scripts/ground_truth_comparator_v2.py
 * e con la logica di classificazione C1..C5 e C2a..C2c di eval/java_harness/run_eval.py.
 */
public final class GroundTruthComparator {

    private static final Pattern HONORIFICS_PATTERN = Pattern.compile(
            "^(?:spett\\.?le|spettabile|egr\\.?|egregio|gent\\.?le|gentile|dott\\.?ssa|dott\\.?|sig\\.?ra|sig\\.?|avv\\.?|ing\\.?)\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern LEADING_PUNCTUATION_PATTERN = Pattern.compile("^[\\s,.:\\-]+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$");
    private static final Pattern IT_DATE_PATTERN = Pattern.compile("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$");
    private static final Pattern NUMBER_EXTRACT_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)*");

    private static final Pattern EU_NUMBER_PATTERN_1 = Pattern.compile("^\\d{1,3}(\\.\\d{3})*,\\d+$");
    private static final Pattern EU_NUMBER_PATTERN_2 = Pattern.compile("^\\d+,\\d+$");
    private static final Pattern US_NUMBER_PATTERN = Pattern.compile("^\\d{1,3}(,\\d{3})+\\.\\d+$");
    private static final Pattern THOUSANDS_ONLY_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{3})+$");

    private static final Map<String, String> MONTHS_IT = new LinkedHashMap<>();
    static {
        MONTHS_IT.put("gennaio", "01");
        MONTHS_IT.put("febbraio", "02");
        MONTHS_IT.put("marzo", "03");
        MONTHS_IT.put("aprile", "04");
        MONTHS_IT.put("maggio", "05");
        MONTHS_IT.put("giugno", "06");
        MONTHS_IT.put("luglio", "07");
        MONTHS_IT.put("agosto", "08");
        MONTHS_IT.put("settembre", "09");
        MONTHS_IT.put("ottobre", "10");
        MONTHS_IT.put("novembre", "11");
        MONTHS_IT.put("dicembre", "12");
    }

    private static final Map<String, String> FIELD_ROUTING_TABLE = new LinkedHashMap<>();
    static {
        // 1. Sesso
        FIELD_ROUTING_TABLE.put("sesso", "gender");

        // 2. Date
        FIELD_ROUTING_TABLE.put("dataFattura", "date");
        FIELD_ROUTING_TABLE.put("dataEvento", "date");
        FIELD_ROUTING_TABLE.put("dataNascita", "date");
        FIELD_ROUTING_TABLE.put("dataRilascio", "date");
        FIELD_ROUTING_TABLE.put("dataScadenza", "date");

        // 3. Numeri
        FIELD_ROUTING_TABLE.put("importoTotale", "number");
        FIELD_ROUTING_TABLE.put("budgetEuro", "number");
        FIELD_ROUTING_TABLE.put("numeroPartecipanti", "number");

        // 4. Telefoni
        FIELD_ROUTING_TABLE.put("telefono", "phone");
        FIELD_ROUTING_TABLE.put("cellulare", "phone");

        // 5. Denominazioni / Onorifici
        FIELD_ROUTING_TABLE.put("cliente.denominazione", "honorifics");
        FIELD_ROUTING_TABLE.put("fornitore.denominazione", "honorifics");
        FIELD_ROUTING_TABLE.put("mittente", "honorifics");
        FIELD_ROUTING_TABLE.put("nome", "honorifics");
        FIELD_ROUTING_TABLE.put("cognome", "honorifics");

        // 6. Testo esplicito
        FIELD_ROUTING_TABLE.put("enteRilascio", "text");
        FIELD_ROUTING_TABLE.put("cliente.codiceFiscale", "text");
        FIELD_ROUTING_TABLE.put("fornitore.partitaIva", "text");
        FIELD_ROUTING_TABLE.put("iban", "text");
        FIELD_ROUTING_TABLE.put("numeroFattura", "text");
        FIELD_ROUTING_TABLE.put("azienda", "text");
        FIELD_ROUTING_TABLE.put("citta", "text");
        FIELD_ROUTING_TABLE.put("email", "text");
        FIELD_ROUTING_TABLE.put("oggetto", "text");
        FIELD_ROUTING_TABLE.put("codiceFiscale", "text");
        FIELD_ROUTING_TABLE.put("luogoNascita", "text");
        FIELD_ROUTING_TABLE.put("numeroDocumento", "text");
        FIELD_ROUTING_TABLE.put("tipoDocumento", "text");

        FIELD_ROUTING_TABLE.put("categoriaRilevata", "text");
        FIELD_ROUTING_TABLE.put("argomentoPrincipale", "text");
        FIELD_ROUTING_TABLE.put("motivoEsclusione", "text");
        FIELD_ROUTING_TABLE.put("entitaRilevate", "text");
    }

    private GroundTruthComparator() {}

    public enum FieldClass {
        CORRETTO,
        NULL_SU_VALORE_PRESENTE,
        VALORE_SU_NULL_ATTESO,
        VALORE_ERRATO,
        STRINGA_VUOTA
    }

    public record FieldComparisonResult(
            String fieldPath,
            boolean match,
            boolean keyPresent,
            Object extracted,
            Object groundTruth,
            Object normalizedExtracted,
            Object normalizedGroundTruth,
            FieldClass fieldClass
    ) {}

    public record DocumentClassification(
            String category,
            String c2Subcode,
            String categoryDescription,
            boolean allMatched,
            int totalFields,
            int correctFields,
            Map<String, FieldComparisonResult> fieldMatches
    ) {}

    public static boolean isNullValue(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof JsonNode jn) {
            if (jn.isNull() || jn.isMissingNode()) {
                return true;
            }
            if (jn.isTextual()) {
                val = jn.asText();
            }
        }
        if (val instanceof String s) {
            String cleaned = s.strip().toLowerCase(Locale.ROOT);
            return cleaned.isEmpty() || cleaned.equals("null") || cleaned.equals("none")
                    || cleaned.equals("n/a") || cleaned.equals("undefined");
        }
        return false;
    }

    public static String cleanHonorifics(Object val) {
        if (isNullValue(val)) {
            return null;
        }
        String s = val.toString().strip();
        while (true) {
            Matcher m = HONORIFICS_PATTERN.matcher(s);
            if (m.find() && m.start() == 0) {
                s = s.substring(m.end()).strip();
            } else {
                break;
            }
        }
        s = LEADING_PUNCTUATION_PATTERN.matcher(s).replaceFirst("").strip();
        return MULTI_SPACE_PATTERN.matcher(s).replaceAll(" ");
    }

    public static String normalizeDate(Object val) {
        if (isNullValue(val)) {
            return null;
        }
        String s = val.toString().strip().toLowerCase(Locale.ROOT);

        // Formato ISO: YYYY-MM-DD
        Matcher mIso = ISO_DATE_PATTERN.matcher(s);
        if (mIso.matches()) {
            int y = Integer.parseInt(mIso.group(1));
            int m = Integer.parseInt(mIso.group(2));
            int d = Integer.parseInt(mIso.group(3));
            return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d);
        }

        // Formato standard IT: DD/MM/YYYY o DD-MM-YYYY
        Matcher mIt = IT_DATE_PATTERN.matcher(s);
        if (mIt.matches()) {
            int d = Integer.parseInt(mIt.group(1));
            int m = Integer.parseInt(mIt.group(2));
            int y = Integer.parseInt(mIt.group(3));
            return String.format(Locale.ROOT, "%04d-%02d-%02d", y, m, d);
        }

        // Formato testuale: "4 febbraio 2026"
        for (Map.Entry<String, String> entry : MONTHS_IT.entrySet()) {
            String mName = entry.getKey();
            if (s.contains(mName)) {
                Pattern pat = Pattern.compile("^(\\d{1,2})\\s+" + Pattern.quote(mName) + "\\s+(\\d{4})$");
                Matcher mText = pat.matcher(s);
                if (mText.matches()) {
                    int d = Integer.parseInt(mText.group(1));
                    int y = Integer.parseInt(mText.group(2));
                    return String.format(Locale.ROOT, "%04d-%s-%02d", y, entry.getValue(), d);
                }
            }
        }

        return val.toString().strip();
    }

    public static Double normalizeNumber(Object val) {
        if (isNullValue(val)) {
            return null;
        }
        if (val instanceof Number n) {
            return n.doubleValue();
        }

        String s = val.toString().strip();
        Matcher m = NUMBER_EXTRACT_PATTERN.matcher(s);
        if (!m.find()) {
            return null;
        }
        String numStr = m.group(0);

        if (EU_NUMBER_PATTERN_1.matcher(numStr).matches() || EU_NUMBER_PATTERN_2.matcher(numStr).matches()) {
            numStr = numStr.replace(".", "").replace(",", ".");
        } else if (US_NUMBER_PATTERN.matcher(numStr).matches()) {
            numStr = numStr.replace(",", "");
        } else if (THOUSANDS_ONLY_PATTERN.matcher(numStr).matches()) {
            numStr = numStr.replace(".", "");
        }

        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String normalizePhone(Object val) {
        if (isNullValue(val)) {
            return null;
        }
        String s = val.toString().strip();
        String digitsOnly = s.replaceAll("[^\\d+]", "");

        if (digitsOnly.startsWith("0039")) {
            digitsOnly = "+" + digitsOnly.substring(2);
        }

        String national;
        if (digitsOnly.startsWith("+39")) {
            national = digitsOnly.substring(3);
        } else {
            national = digitsOnly.replaceFirst("^\\+", "");
        }
        return national;
    }

    public static String normalizeGender(Object val) {
        if (isNullValue(val)) {
            return null;
        }
        String s = val.toString().strip().toUpperCase(Locale.ROOT);
        if (s.equals("M") || s.equals("MASCHILE") || s.equals("MALE")) {
            return "M";
        }
        if (s.equals("F") || s.equals("FEMMINILE") || s.equals("FEMALE")) {
            return "F";
        }
        return s;
    }

    public static FieldComparisonResult compareField(String fieldPath, Object extVal, Object gtVal, boolean keyPresent) {
        boolean match;
        Object normExt;
        Object normGt;

        // 1. Caso null atteso in ground truth
        if (isNullValue(gtVal)) {
            boolean extIsNull = isNullValue(extVal);
            match = extIsNull;
            normExt = extIsNull ? null : extVal;
            normGt = null;
        } else if (isNullValue(extVal)) {
            // Valore atteso non-null, ma estratto null
            match = false;
            normExt = null;
            normGt = gtVal;
        } else {
            String rule = FIELD_ROUTING_TABLE.getOrDefault(fieldPath, "text");
            switch (rule) {
                case "gender" -> {
                    String ne = normalizeGender(extVal);
                    String ng = normalizeGender(gtVal);
                    match = ne != null && ne.equals(ng);
                    normExt = ne;
                    normGt = ng;
                }
                case "date" -> {
                    String ne = normalizeDate(extVal);
                    String ng = normalizeDate(gtVal);
                    match = ne != null && ne.equals(ng);
                    normExt = ne;
                    normGt = ng;
                }
                case "number" -> {
                    Double ne = normalizeNumber(extVal);
                    Double ng = normalizeNumber(gtVal);
                    if (ne != null && ng != null) {
                        match = Math.abs(ne - ng) < 1e-4;
                    } else {
                        match = false;
                    }
                    normExt = ne;
                    normGt = ng;
                }
                case "phone" -> {
                    String ne = normalizePhone(extVal);
                    String ng = normalizePhone(gtVal);
                    match = ne != null && ne.equals(ng);
                    normExt = ne;
                    normGt = ng;
                }
                case "honorifics" -> {
                    String ne = cleanHonorifics(extVal);
                    String ng = cleanHonorifics(gtVal);
                    if (ne != null && ng != null) {
                        match = ne.strip().equalsIgnoreCase(ng.strip());
                    } else {
                        match = (ne == null && ng == null);
                    }
                    normExt = ne;
                    normGt = ng;
                }
                default -> {
                    // Testo generico normalizzato case-insensitive
                    String se = extVal.toString().strip().toLowerCase(Locale.ROOT);
                    String sg = gtVal.toString().strip().toLowerCase(Locale.ROOT);
                    match = se.equals(sg);
                    normExt = extVal.toString().strip();
                    normGt = gtVal.toString().strip();
                }
            }
        }

        FieldClass fClass = classifyField(match, extVal, gtVal, keyPresent);

        return new FieldComparisonResult(
                fieldPath,
                match,
                keyPresent,
                extVal,
                gtVal,
                normExt,
                normGt,
                fClass
        );
    }

    public static FieldClass classifyField(boolean match, Object extVal, Object gtVal, boolean keyPresent) {
        if (match) {
            return FieldClass.CORRETTO;
        }
        if (extVal != null && extVal.toString().isEmpty()) {
            return FieldClass.STRINGA_VUOTA;
        }
        if ((extVal == null || !keyPresent || isNullValue(extVal)) && gtVal != null && !isNullValue(gtVal)) {
            return FieldClass.NULL_SU_VALORE_PRESENTE;
        }
        if ((gtVal == null || isNullValue(gtVal)) && extVal != null && !isNullValue(extVal) && !extVal.toString().isEmpty()) {
            return FieldClass.VALORE_SU_NULL_ATTESO;
        }
        return FieldClass.VALORE_ERRATO;
    }

    public static DocumentClassification classifyDocument(
            String status,
            Map<String, FieldComparisonResult> fieldMatches,
            int totalFields,
            int correctFields
    ) {
        boolean allMatched = (correctFields == totalFields);

        boolean hasValOnNull = false;
        boolean hasDiffVal = false;

        for (FieldComparisonResult f : fieldMatches.values()) {
            if (!f.match()) {
                if (!f.keyPresent()) {
                    // Omissione
                } else if (isNullValue(f.groundTruth()) && !isNullValue(f.extracted())) {
                    hasValOnNull = true;
                } else {
                    hasDiffVal = true;
                }
            }
        }

        String category;
        String c2Subcode = null;
        String catDesc;

        if ("OK".equalsIgnoreCase(status)) {
            if (allMatched) {
                category = "C1";
                catDesc = "OK con tutti i campi corretti rispetto alla ground truth";
            } else {
                category = "C2";
                if (hasValOnNull) {
                    c2Subcode = "C2c";
                    catDesc = "OK con almeno un valore allucinato su campo atteso null (C2c)";
                } else if (hasDiffVal) {
                    c2Subcode = "C2b";
                    catDesc = "OK con almeno un valore diverso su campo non-null (C2b)";
                } else {
                    c2Subcode = "C2a";
                    catDesc = "OK con solo chiavi assenti/omissioni (C2a)";
                }
            }
        } else if ("INDETERMINATE".equalsIgnoreCase(status)) {
            if (allMatched) {
                category = "C3";
                catDesc = "INDETERMINATE con estrazione uguale alla ground truth su tutti i campi";
            } else {
                category = "C4";
                catDesc = "INDETERMINATE con almeno un campo diverso dalla ground truth";
            }
        } else {
            category = "C5";
            catDesc = "Status inatteso: " + status;
        }

        return new DocumentClassification(
                category,
                c2Subcode,
                catDesc,
                allMatched,
                totalFields,
                correctFields,
                fieldMatches
        );
    }

    public static Map<String, Object> flattenJson(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        flattenJsonInternal(node, "", map);
        return map;
    }

    private static void flattenJsonInternal(JsonNode node, String prefix, Map<String, Object> map) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
                flattenJsonInternal(field.getValue(), key, map);
            }
        } else if (node.isNull()) {
            map.put(prefix, null);
        } else if (node.isTextual()) {
            map.put(prefix, node.asText());
        } else if (node.isNumber()) {
            map.put(prefix, node.numberValue());
        } else if (node.isBoolean()) {
            map.put(prefix, node.asBoolean());
        } else {
            map.put(prefix, node.asText());
        }
    }
}
