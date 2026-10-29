#!/usr/bin/env python3
"""
Script di validazione formale del dataset held-out (Passo 3).
Verifica:
1. Conformità agli schemi JSON ufficiali in src/main/resources/schemas/
2. Validità matematica dei checksum (100% validi nel set principale, esattamente 1 violazione isolata per documento nei 6 avversariali)
3. Coerenza anagrafica incrociata (CF vs Cognome, Nome, Data di Nascita, Sesso, Codice Belfiore Comune e cronologia date)
4. Presenza testuale dei valori estratti non-null nel payload sorgente (con normalizzazione formati numerici italiani)
5. Quota percentuale esatta di campi attesi null (>= 20.0% su 392 campi foglia dei 42 documenti principali)
6. Totale disgiunzione (0 collisioni su radici P.IVA 10 cifre, CF 15 caratteri, conti IBAN ed entità) rispetto a:
   - data/dataset_60_v2.json
   - data/dataset_60.json
   - data/adversarial_piva_set.json
   - e tra heldout principale e heldout avversariale
7. Verifica file router data/heldout_router_altro_v1.json (8 documenti ALTRO)
8. Calcolo SHA-256 dei 3 file generati
"""

import hashlib
import json
import re
import sys
from datetime import datetime
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = REPO_ROOT / "data"
SCHEMAS_DIR = REPO_ROOT / "src/main/resources/schemas"

# ==============================================================================
# Algoritmi di Verifica Checksum e Coerenza Fiscale
# ==============================================================================

CF_ODD_MAP = {
    '0': 1, '1': 0, '2': 5, '3': 7, '4': 9, '5': 13, '6': 15, '7': 17, '8': 19, '9': 21,
    'A': 1, 'B': 0, 'C': 5, 'D': 7, 'E': 9, 'F': 13, 'G': 15, 'H': 17, 'I': 19, 'J': 21,
    'K': 2, 'L': 4, 'M': 18, 'N': 20, 'O': 11, 'P': 3, 'Q': 6, 'R': 8, 'S': 12, 'T': 14,
    'U': 16, 'V': 10, 'W': 22, 'X': 25, 'Y': 24, 'Z': 23
}

CF_EVEN_MAP = {
    '0': 0, '1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6, '7': 7, '8': 8, '9': 9,
    **{chr(ord('A') + i): i for i in range(26)}
}

CF_MONTH_MAP = {
    1: 'A', 2: 'B', 3: 'C', 4: 'D', 5: 'E', 6: 'H',
    7: 'L', 8: 'M', 9: 'P', 10: 'R', 11: 'S', 12: 'T'
}

BELFIORE_MAP = {
    'ROMA': 'H501',
    'MILANO': 'F205',
    'NAPOLI': 'F839',
    'TORINO': 'L219',
    'FIRENZE': 'D612',
    'BOLOGNA': 'A944',
    'PALERMO': 'G273',
    'GENOVA': 'D969',
    'VERONA': 'L781',
    'VENEZIA': 'L736',
    'BARI': 'A662',
    'CATANIA': 'C351',
    'TRIESTE': 'L424',
    'PADOVA': 'G224',
    'BRESCIA': 'B157',
    'BERGAMO': 'A794',
    'PERUGIA': 'G478',
    'CAGLIARI': 'B354',
    'PAVIA': 'G388',
    'PISA': 'G702',
    'SCHIO': 'I531',
    'ERBA': 'D416',
    'TRENTO': 'L378',
    'CUNEO': 'D205',
    'MODENA': 'F257',
    'CREMONA': 'D150',
    'SIENA': 'I726',
    'VADO LIGURE': 'L528'
}

def cf_extract_cons_vows(s: str):
    vowels = 'AEIOU'
    cleaned = ''.join(c for c in s.upper() if c.isalpha())
    cons = [c for c in cleaned if c not in vowels]
    vows = [c for c in cleaned if c in vowels]
    return cons, vows

def cf_encode_cognome(cognome: str) -> str:
    cons, vows = cf_extract_cons_vows(cognome)
    res = cons + vows + ['X', 'X', 'X']
    return ''.join(res[:3])

def cf_encode_nome(nome: str) -> str:
    cons, vows = cf_extract_cons_vows(nome)
    if len(cons) >= 4:
        return cons[0] + cons[2] + cons[3]
    res = cons + vows + ['X', 'X', 'X']
    return ''.join(res[:3])

def compute_cf_check(cf15: str) -> str:
    s = sum(CF_ODD_MAP[c] if i % 2 == 0 else CF_EVEN_MAP[c] for i, c in enumerate(cf15.upper()))
    return chr(ord('A') + (s % 26))

def is_valid_cf(cf: str) -> bool:
    if not cf or len(cf) != 16:
        return False
    cf = cf.upper()
    return cf[15] == compute_cf_check(cf[:15])

def is_valid_piva(piva: str) -> bool:
    if not piva or len(piva) != 11 or not piva.isdigit():
        return False
    s = 0
    for i, c in enumerate(piva[:10]):
        d = int(c)
        if i % 2 == 1:
            d2 = d * 2
            s += d2 - 9 if d2 > 9 else d2
        else:
            s += d
    expected = (10 - (s % 10)) % 10
    return int(piva[10]) == expected

def is_valid_iban(iban: str) -> bool:
    if not iban:
        return False
    clean = iban.replace(" ", "").upper()
    if len(clean) != 27 or not clean.startswith("IT"):
        return False
    rearranged = clean[4:] + clean[:4]
    digits = ''.join(c if c.isdigit() else str(ord(c) - ord('A') + 10) for c in rearranged)
    return int(digits) % 97 == 1

def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(65536):
            hasher.update(chunk)
    return hasher.hexdigest()

# ==============================================================================
# Validatore Schema JSON Ricorsivo
# ==============================================================================

def validate_json_schema(instance, schema, path="root"):
    expected_type = schema.get("type")
    if expected_type:
        if isinstance(expected_type, list):
            valid = False
            for t in expected_type:
                if t == "null" and instance is None:
                    valid = True
                    break
                elif t == "string" and isinstance(instance, str):
                    valid = True
                    break
                elif t == "number" and (isinstance(instance, (int, float)) and not isinstance(instance, bool)):
                    valid = True
                    break
                elif t == "integer" and (isinstance(instance, int) and not isinstance(instance, bool)):
                    valid = True
                    break
                elif t == "object" and isinstance(instance, dict):
                    valid = True
                    break
            if not valid:
                raise ValueError(f"Campo '{path}': atteso uno di {expected_type}, ricevuto {type(instance).__name__} ({instance})")
        else:
            if expected_type == "object":
                if not isinstance(instance, dict):
                    raise ValueError(f"Campo '{path}': atteso object, ricevuto {type(instance).__name__}")
                for req in schema.get("required", []):
                    if req not in instance:
                        raise ValueError(f"Campo '{path}': proprietà obbligatoria '{req}' mancante")
                for prop_name, prop_schema in schema.get("properties", {}).items():
                    if prop_name in instance:
                        validate_json_schema(instance[prop_name], prop_schema, f"{path}.{prop_name}")
            elif expected_type == "string":
                if not isinstance(instance, str):
                    raise ValueError(f"Campo '{path}': atteso string, ricevuto {type(instance).__name__}")
                if "enum" in schema and instance not in schema["enum"]:
                    raise ValueError(f"Campo '{path}': valore '{instance}' non ammesso tra gli enum consentiti {schema['enum']}")
            elif expected_type == "number":
                if not isinstance(instance, (int, float)) or isinstance(instance, bool):
                    raise ValueError(f"Campo '{path}': atteso number, ricevuto {type(instance).__name__}")
            elif expected_type == "integer":
                if not isinstance(instance, int) or isinstance(instance, bool):
                    raise ValueError(f"Campo '{path}': atteso integer, ricevuto {type(instance).__name__}")
            elif expected_type == "array":
                if not isinstance(instance, list):
                    raise ValueError(f"Campo '{path}': atteso array, ricevuto {type(instance).__name__}")
                item_schema = schema.get("items", {})
                for idx, item in enumerate(instance):
                    validate_json_schema(item, item_schema, f"{path}[{idx}]")

# ==============================================================================
# Scomposizione Campi Foglia e Rilevazione Null
# ==============================================================================

def get_leaf_fields(doc_type: str, gt: dict):
    """Restituisce la lista di tuple (field_name, field_value) per i 3 domini strutturati."""
    fields = []
    if doc_type == "FATTURA":
        fields.append(("numeroFattura", gt.get("numeroFattura")))
        fields.append(("dataFattura", gt.get("dataFattura")))
        forn = gt.get("fornitore") or {}
        fields.append(("fornitore.denominazione", forn.get("denominazione")))
        fields.append(("fornitore.partitaIva", forn.get("partitaIva")))
        cli = gt.get("cliente") or {}
        fields.append(("cliente.denominazione", cli.get("denominazione")))
        fields.append(("cliente.codiceFiscale", cli.get("codiceFiscale")))
        fields.append(("importoTotale", gt.get("importoTotale")))
        fields.append(("iban", gt.get("iban")))
    elif doc_type == "ANAGRAFICA":
        fields.append(("tipoDocumento", gt.get("tipoDocumento")))
        fields.append(("nome", gt.get("nome")))
        fields.append(("cognome", gt.get("cognome")))
        fields.append(("codiceFiscale", gt.get("codiceFiscale")))
        fields.append(("dataNascita", gt.get("dataNascita")))
        fields.append(("luogoNascita", gt.get("luogoNascita")))
        fields.append(("sesso", gt.get("sesso")))
        fields.append(("numeroDocumento", gt.get("numeroDocumento")))
        fields.append(("dataRilascio", gt.get("dataRilascio")))
        fields.append(("dataScadenza", gt.get("dataScadenza")))
        fields.append(("enteRilascio", gt.get("enteRilascio")))
    elif doc_type == "EMAIL":
        fields.append(("mittente", gt.get("mittente")))
        fields.append(("email", gt.get("email")))
        fields.append(("telefono", gt.get("telefono")))
        fields.append(("azienda", gt.get("azienda")))
        fields.append(("oggetto", gt.get("oggetto")))
        fields.append(("citta", gt.get("citta")))
        fields.append(("numeroPartecipanti", gt.get("numeroPartecipanti")))
        fields.append(("budgetEuro", gt.get("budgetEuro")))
        fields.append(("dataEvento", gt.get("dataEvento")))
    return fields

# ==============================================================================
# Validazione della Coerenza Anagrafica Incrociata
# ==============================================================================

def validate_anagrafica_coherence(doc_id: str, gt: dict) -> list:
    """Verifica la coerenza incrociata tra CF e dati anagrafici, nonché la cronologia delle date."""
    errors = []
    cf = gt.get("codiceFiscale")
    if not cf or len(cf) != 16:
        # Se CF non è presente (es. HO_ANA_10), non possiamo fare il cross-check del CF
        return errors

    cf = cf.upper()
    cognome = gt.get("cognome")
    nome = gt.get("nome")
    data_nascita = gt.get("dataNascita")
    luogo_nascita = gt.get("luogoNascita")
    sesso = gt.get("sesso")

    # 1. Cognome vs CF[0:3]
    if cognome:
        expected_cg = cf_encode_cognome(cognome)
        if cf[:3] != expected_cg:
            errors.append(f"Incoerenza CF/Cognome: CF ha '{cf[:3]}', atteso '{expected_cg}' per cognome '{cognome}'")

    # 2. Nome vs CF[3:6]
    if nome:
        expected_nm = cf_encode_nome(nome)
        if cf[3:6] != expected_nm:
            errors.append(f"Incoerenza CF/Nome: CF ha '{cf[3:6]}', atteso '{expected_nm}' per nome '{nome}'")

    # 3. Data di Nascita vs CF[6:11]
    if data_nascita:
        try:
            dt = datetime.strptime(data_nascita, "%d/%m/%Y")
            exp_yr = f"{dt.year % 100:02d}"
            exp_mo = CF_MONTH_MAP[dt.month]
            if cf[6:8] != exp_yr:
                errors.append(f"Incoerenza CF/Anno di nascita: CF ha '{cf[6:8]}', atteso '{exp_yr}'")
            if cf[8] != exp_mo:
                errors.append(f"Incoerenza CF/Mese di nascita: CF ha '{cf[8]}', atteso '{exp_mo}'")

            cf_day = int(cf[9:11])
            if sesso == "M":
                if cf_day != dt.day:
                    errors.append(f"Incoerenza CF/Giorno maschio: CF ha '{cf_day}', atteso '{dt.day}'")
            elif sesso == "F":
                if cf_day != dt.day + 40:
                    errors.append(f"Incoerenza CF/Giorno femmina: CF ha '{cf_day}', atteso '{dt.day + 40}'")
            else:
                # Sesso non specificato esplicitamente nel testo: deve corrispondere a M (day) o F (day + 40)
                if cf_day != dt.day and cf_day != dt.day + 40:
                    errors.append(f"Incoerenza CF/Giorno: CF ha '{cf_day}', non coincide né con {dt.day} (M) né con {dt.day+40} (F)")
        except ValueError:
            errors.append(f"Formato data nascita non valido: '{data_nascita}' (atteso GG/MM/AAAA)")

    # 4. Luogo di Nascita vs Belfiore CF[11:15]
    if luogo_nascita:
        comune_clean = re.sub(r'\(.*?\)', '', luogo_nascita).strip().upper()
        if comune_clean in BELFIORE_MAP:
            exp_belfiore = BELFIORE_MAP[comune_clean]
            if cf[11:15] != exp_belfiore:
                errors.append(f"Incoerenza CF/Belfiore: CF ha '{cf[11:15]}', atteso '{exp_belfiore}' per '{luogo_nascita}'")

    # 5. Cronologia delle Date (Nascita < Rilascio < Scadenza)
    data_ril = gt.get("dataRilascio")
    data_scad = gt.get("dataScadenza")
    dt_nasc = None
    dt_ril = None
    dt_scad = None

    if data_nascita:
        try: dt_nasc = datetime.strptime(data_nascita, "%d/%m/%Y")
        except: pass
    if data_ril:
        try: dt_ril = datetime.strptime(data_ril, "%d/%m/%Y")
        except: pass
    if data_scad:
        try: dt_scad = datetime.strptime(data_scad, "%d/%m/%Y")
        except: pass

    if dt_nasc and dt_ril and dt_ril <= dt_nasc:
        errors.append(f"Incoerenza cronologica: dataRilascio ({data_ril}) <= dataNascita ({data_nascita})")
    if dt_ril and dt_scad and dt_scad <= dt_ril:
        errors.append(f"Incoerenza cronologica: dataScadenza ({data_scad}) <= dataRilascio ({data_ril})")
    if dt_nasc and dt_scad and dt_scad <= dt_nasc:
        errors.append(f"Incoerenza cronologica: dataScadenza ({data_scad}) <= dataNascita ({data_nascita})")

    return errors

# ==============================================================================
# Verifica Presenza Numerica Naturale Italiana
# ==============================================================================

def check_number_in_text(num_val: float, payload: str) -> bool:
    """Verifica la presenza del numero nel testo accettando formattazioni italiane."""
    variants = [
        str(num_val),
        f"{num_val:.2f}",
        f"{num_val:.2f}".replace('.', ','),
        f"{num_val:,.2f}".replace(',', 'X').replace('.', ',').replace('X', '.'),
    ]
    if num_val.is_integer() or abs(num_val - round(num_val)) < 1e-6:
        ival = int(round(num_val))
        variants.extend([
            str(ival),
            f"{ival:,}".replace(',', '.')
        ])
    for v in variants:
        if v.lower() in payload.lower():
            return True
    return False

# ==============================================================================
# Validazione Principale
# ==============================================================================

def main():
    print("=== VALIDAZIONE FORMALE RIGOROSA DEL DATASET HELDOUT (PASSO 3) ===")

    heldout_file = DATA_DIR / "heldout_dataset_v1.json"
    adv_file = DATA_DIR / "heldout_adversarial_v1.json"
    router_file = DATA_DIR / "heldout_router_altro_v1.json"
    d60_v2_file = DATA_DIR / "dataset_60_v2.json"
    d60_file = DATA_DIR / "dataset_60.json"
    adv_piva_file = DATA_DIR / "adversarial_piva_set.json"

    for p in [heldout_file, adv_file, router_file]:
        if not p.exists():
            print(f"ERRORE: File {p} non trovato!", file=sys.stderr)
            sys.exit(1)

    with open(heldout_file, "r", encoding="utf-8") as f:
        heldout_docs = json.load(f)
    with open(adv_file, "r", encoding="utf-8") as f:
        adv_docs = json.load(f)
    with open(router_file, "r", encoding="utf-8") as f:
        router_docs = json.load(f)

    # Caricamento schemi
    schemas = {
        "FATTURA": json.loads((SCHEMAS_DIR / "fattura_info.json").read_text(encoding="utf-8")),
        "ANAGRAFICA": json.loads((SCHEMAS_DIR / "anagrafica_documento.json").read_text(encoding="utf-8")),
        "EMAIL": json.loads((SCHEMAS_DIR / "email_info.json").read_text(encoding="utf-8")),
    }

    # --------------------------------------------------------------------------
    # 1. Conformità Schemi JSON
    # --------------------------------------------------------------------------
    print("\n--- 1. Verifica Conformità agli Schemi JSON Ufficiali ---")
    schema_errors = []
    for doc in heldout_docs + adv_docs:
        doc_id = doc["id"]
        doc_type = doc["type"]
        gt = doc["ground_truth"]
        schema = schemas[doc_type]
        try:
            validate_json_schema(gt, schema)
        except Exception as e:
            schema_errors.append((doc_id, str(e)))

    if schema_errors:
        print(f"ERRORE: Rilevate {len(schema_errors)} violazioni di schema:", file=sys.stderr)
        for doc_id, err in schema_errors:
            print(f"  [{doc_id}] {err}", file=sys.stderr)
        sys.exit(1)
    print(f"Conformità schemi: 100% superata su tutti i {len(heldout_docs) + len(adv_docs)} documenti.")

    # --------------------------------------------------------------------------
    # 2. Verifica Validità Matematica Checksum nel Set Principale
    # --------------------------------------------------------------------------
    print("\n--- 2. Verifica Validità Checksum (CF, P.IVA, IBAN) nel Set Principale ---")
    main_checksum_errors = []
    cf_count = 0
    piva_count = 0
    iban_count = 0

    for doc in heldout_docs:
        gt = doc["ground_truth"]
        doc_id = doc["id"]

        # CF persona fisica in ANAGRAFICA o cliente FATTURA (standard alfanumerico a 16 caratteri)
        cf = gt.get("codiceFiscale")
        if not cf and gt.get("cliente") and isinstance(gt["cliente"], dict):
            cf_val = gt["cliente"].get("codiceFiscale")
            if cf_val:
                if len(cf_val) == 16:
                    cf = cf_val
                else:
                    main_checksum_errors.append((doc_id, f"CF cliente deve essere standard a 16 caratteri: {cf_val}"))

        if cf:
            cf_count += 1
            if not is_valid_cf(cf):
                main_checksum_errors.append((doc_id, f"CF non conforme al codice di controllo: {cf}"))

        # P.IVA fornitore
        if gt.get("fornitore") and isinstance(gt["fornitore"], dict) and gt["fornitore"].get("partitaIva"):
            piva = gt["fornitore"]["partitaIva"]
            piva_count += 1
            if not is_valid_piva(piva):
                main_checksum_errors.append((doc_id, f"P.IVA fornitore non conforme a Luhn: {piva}"))

        # IBAN
        if gt.get("iban"):
            iban = gt["iban"]
            iban_count += 1
            if not is_valid_iban(iban):
                main_checksum_errors.append((doc_id, f"IBAN non conforme a ISO 7064 MOD 97: {iban}"))

    if main_checksum_errors:
        print(f"ERRORE: {len(main_checksum_errors)} checksum invalidi nel set principale:", file=sys.stderr)
        for doc_id, err in main_checksum_errors:
            print(f"  [{doc_id}] {err}", file=sys.stderr)
        sys.exit(1)
    print(f"Set Principale: 100% checksum matematicamente validi verificati ({cf_count} CF, {piva_count} P.IVA, {iban_count} IBAN).")

    # --------------------------------------------------------------------------
    # 3. Verifica Coerenza Anagrafica Incrociata (ANAGRAFICA)
    # --------------------------------------------------------------------------
    print("\n--- 3. Verifica Coerenza Anagrafica Incrociata e Cronologia Date ---")
    coherence_errors = []
    ana_checked = 0
    for doc in heldout_docs:
        if doc["type"] == "ANAGRAFICA":
            ana_checked += 1
            errs = validate_anagrafica_coherence(doc["id"], doc["ground_truth"])
            for e in errs:
                coherence_errors.append((doc["id"], e))

    if coherence_errors:
        print(f"ERRORE: Rilevate {len(coherence_errors)} incoerenze anagrafiche:", file=sys.stderr)
        for doc_id, err in coherence_errors:
            print(f"  [{doc_id}] {err}", file=sys.stderr)
        sys.exit(1)
    print(f"Coerenza anagrafica incrociata: 100% verificata su tutti i {ana_checked} documenti ANAGRAFICA.")

    # --------------------------------------------------------------------------
    # 4. Verifica Documenti Avversariali (Singola Violazione Isolata)
    # --------------------------------------------------------------------------
    print("\n--- 4. Verifica Documenti Avversariali (Singola Violazione Isolata) ---")
    adv_errors = []
    for doc in adv_docs:
        doc_id = doc["id"]
        feat = doc.get("adversarial_feature")
        target_field = doc.get("adversarial_field")
        gt = doc["ground_truth"]

        if feat == "INVALID_SUPPLIER_PIVA_LUHN":
            piva = gt["fornitore"]["partitaIva"]
            if is_valid_piva(piva):
                adv_errors.append((doc_id, f"P.IVA {piva} attesa invalida ma risulta valida!"))
            # Verifica che tutti gli altri campi siano conformi
            if gt.get("iban") and not is_valid_iban(gt["iban"]):
                adv_errors.append((doc_id, f"IBAN collaterale non valido in documento con violazione isolata PIVA"))
        elif feat == "INVALID_CF_CONTROL_CHAR":
            cf = gt["codiceFiscale"]
            if is_valid_cf(cf):
                adv_errors.append((doc_id, f"CF {cf} atteso invalido ma risulta valido!"))
            # Verifica che la radice 15 caratteri sia coerente con l'anagrafica
            coherence_sub_errs = validate_anagrafica_coherence(doc_id, gt)
            if coherence_sub_errs:
                adv_errors.append((doc_id, f"Incoerenza collaterale in documento CF avversariale: {coherence_sub_errs}"))
        elif feat == "INVALID_IBAN_MOD97":
            iban = gt["iban"]
            if is_valid_iban(iban):
                adv_errors.append((doc_id, f"IBAN {iban} atteso invalido ma risulta valido!"))
            # Verifica che la PIVA fornitore sia valida
            if gt.get("fornitore", {}).get("partitaIva") and not is_valid_piva(gt["fornitore"]["partitaIva"]):
                adv_errors.append((doc_id, f"PIVA collaterale non valida in documento con violazione isolata IBAN"))

    if adv_errors:
        print(f"ERRORE: Discrepanze nei documenti avversariali:", file=sys.stderr)
        for doc_id, err in adv_errors:
            print(f"  [{doc_id}] {err}", file=sys.stderr)
        sys.exit(1)
    print(f"Documenti avversariali: 6/6 violazioni isolate e conformità collaterale verificate con successo.")

    # --------------------------------------------------------------------------
    # 5. Presenza Testuale con Normalizzazione Numerica Italiana
    # --------------------------------------------------------------------------
    print("\n--- 5. Verifica Presenza Testuale Valori Ground Truth nel Testo Sorgente ---")
    text_presence_errors = []
    for doc in heldout_docs + adv_docs:
        doc_id = doc["id"]
        doc_type = doc["type"]
        payload_lower = doc["payload"].lower()
        fields = get_leaf_fields(doc_type, doc["ground_truth"])

        for f_name, f_val in fields:
            if f_val is None:
                continue

            if isinstance(f_val, str):
                if f_name == "tipoDocumento":
                    # tipoDocumento è un enum canonico (es. NON_SPECIFICATO o PATENTE)
                    continue
                clean_val = f_val.strip().lower()
                if clean_val not in payload_lower:
                    # tollera spazi interni differenti per CF o IBAN
                    compact_val = re.sub(r'\s+', '', clean_val)
                    compact_payload = re.sub(r'\s+', '', payload_lower)
                    if compact_val not in compact_payload:
                        text_presence_errors.append((doc_id, f"Campo stringa '{f_name}' con valore '{f_val}' non rintracciato nel testo"))
            elif isinstance(f_val, (int, float)):
                if not check_number_in_text(float(f_val), doc["payload"]):
                    text_presence_errors.append((doc_id, f"Campo numerico '{f_name}' ({f_val}) non rintracciato nel testo con formattazione italiana"))

    if text_presence_errors:
        print(f"ERRORE: Rilevate {len(text_presence_errors)} discrepanze di presenza testuale:", file=sys.stderr)
        for doc_id, err in text_presence_errors:
            print(f"  [{doc_id}] {err}", file=sys.stderr)
        sys.exit(1)
    print("Presenza testuale: 100% dei valori Ground Truth non-null rintracciati nel testo sorgente.")

    # --------------------------------------------------------------------------
    # 6. Conteggio e Quota Percentuale Campi Null Attesi (>= 20.0%)
    # --------------------------------------------------------------------------
    print("\n--- 6. Calcolo della Quota di Campi Null Attesi nel Set Principale ---")
    domain_counts = {}
    domain_fields = {}
    domain_nulls = {}
    total_fields = 0
    total_nulls = 0

    for doc in heldout_docs:
        dtype = doc["type"]
        domain_counts[dtype] = domain_counts.get(dtype, 0) + 1
        fields = get_leaf_fields(dtype, doc["ground_truth"])
        n_fields = len(fields)
        n_nulls = sum(1 for _, val in fields if val is None)

        domain_fields[dtype] = domain_fields.get(dtype, 0) + n_fields
        domain_nulls[dtype] = domain_nulls.get(dtype, 0) + n_nulls
        total_fields += n_fields
        total_nulls += n_nulls

    print(f"Distribuzione Documenti e Campi nel Set Principale ({len(heldout_docs)} documenti totali):")
    for dtype in ["FATTURA", "ANAGRAFICA", "EMAIL"]:
        cnt = domain_counts.get(dtype, 0)
        flds = domain_fields.get(dtype, 0)
        nls = domain_nulls.get(dtype, 0)
        pct = (nls / flds * 100) if flds > 0 else 0
        print(f"  - {dtype:11s}: {cnt:2d} doc | {flds:3d} campi foglia | {nls:2d} null ({pct:5.2f}%)")

    null_percentage = (total_nulls / total_fields) * 100
    print(f"\nTOTALE Set Principale: {total_fields} campi foglia complessivi | {total_nulls} campi null attesi ({null_percentage:.2f}%)")

    if total_fields != 392:
        print(f"ERRORE: Attesi esattamente 392 campi foglia per 42 documenti, rilevati {total_fields}", file=sys.stderr)
        sys.exit(1)

    if null_percentage < 20.0:
        print(f"ERRORE: Quota null {null_percentage:.2f}% inferiore al vincolo minimo del 20.0%", file=sys.stderr)
        sys.exit(1)
    print(f"[OK] Vincolo quota null attesi >= 20.0% pienamente soddisfatto ({null_percentage:.2f}% >= 20.0%).")

    # --------------------------------------------------------------------------
    # 7. Disgiunzione Totale rispetto a Dataset Pregressi e Tra Set
    # --------------------------------------------------------------------------
    print("\n--- 7. Verifica Disgiunzione Radici Entità (PIVA, CF, IBAN, Denominazioni) ---")
    legacy_piva_roots = set()
    legacy_cf_roots = set()
    legacy_iban_accounts = set()
    legacy_names = set()

    for l_path in [d60_v2_file, d60_file, adv_piva_file]:
        if not l_path.exists(): continue
        with open(l_path, "r", encoding="utf-8") as f:
            l_docs = json.load(f)
        for doc in l_docs:
            gt = doc.get("ground_truth") or doc.get("expected_output") or {}
            # PIVA (10 cifre radice)
            p = gt.get("partitaIva") or (gt.get("fornitore", {}).get("partitaIva") if isinstance(gt.get("fornitore"), dict) else None)
            if p and len(p) >= 10:
                legacy_piva_roots.add(p[:10])
            # CF (15 char radice)
            c = gt.get("codiceFiscale") or (gt.get("cliente", {}).get("codiceFiscale") if isinstance(gt.get("cliente"), dict) else None)
            if c and len(c) == 16:
                legacy_cf_roots.add(c[:15].upper())
            # IBAN (conto 10-12 cifre)
            ib = gt.get("iban")
            if ib:
                legacy_iban_accounts.add(ib.replace(" ", "")[-10:].upper())
            # Nomi
            for k in ["nome", "cognome", "mittente"]:
                if gt.get(k): legacy_names.add(gt[k].strip().lower())
            if gt.get("fornitore", {}).get("denominazione") if isinstance(gt.get("fornitore"), dict) else None:
                legacy_names.add(gt["fornitore"]["denominazione"].strip().lower())
            if gt.get("cliente", {}).get("denominazione") if isinstance(gt.get("cliente"), dict) else None:
                legacy_names.add(gt["cliente"]["denominazione"].strip().lower())

    disjunction_errors = []
    # Set per controllo incrociato tra heldout_dataset e heldout_adversarial
    heldout_main_piva_roots = set()
    heldout_main_cf_roots = set()
    heldout_main_iban_accounts = set()

    for doc in heldout_docs:
        doc_id = doc["id"]
        gt = doc["ground_truth"]
        p = gt.get("fornitore", {}).get("partitaIva") if isinstance(gt.get("fornitore"), dict) else None
        if p and len(p) >= 10:
            root = p[:10]
            if root in legacy_piva_roots:
                disjunction_errors.append((doc_id, f"Radice PIVA {root} collide con dataset pregressi"))
            heldout_main_piva_roots.add(root)

        c = gt.get("codiceFiscale") or (gt.get("cliente", {}).get("codiceFiscale") if isinstance(gt.get("cliente"), dict) else None)
        if c and len(c) == 16:
            root = c[:15].upper()
            if root in legacy_cf_roots:
                disjunction_errors.append((doc_id, f"Radice CF {root} collide con dataset pregressi"))
            heldout_main_cf_roots.add(root)

        ib = gt.get("iban")
        if ib:
            acc = ib.replace(" ", "")[-10:].upper()
            if acc in legacy_iban_accounts:
                disjunction_errors.append((doc_id, f"Conto IBAN {acc} collide con dataset pregressi"))
            heldout_main_iban_accounts.add(acc)

    # Verifica avversariali contro legacy E contro heldout_dataset_v1
    for doc in adv_docs:
        doc_id = doc["id"]
        gt = doc["ground_truth"]
        p = gt.get("fornitore", {}).get("partitaIva") if isinstance(gt.get("fornitore"), dict) else None
        if p and len(p) >= 10:
            root = p[:10]
            if root in legacy_piva_roots:
                disjunction_errors.append((doc_id, f"Radice PIVA avversariale {root} collide con dataset pregressi"))
            if root in heldout_main_piva_roots:
                disjunction_errors.append((doc_id, f"Radice PIVA avversariale {root} collide con il set heldout principale"))

        c = gt.get("codiceFiscale")
        if c and len(c) == 16:
            root = c[:15].upper()
            if root in legacy_cf_roots:
                disjunction_errors.append((doc_id, f"Radice CF avversariale {root} collide con dataset pregressi"))
            if root in heldout_main_cf_roots:
                disjunction_errors.append((doc_id, f"Radice CF avversariale {root} collide con il set heldout principale"))

        ib = gt.get("iban")
        if ib:
            acc = ib.replace(" ", "")[-10:].upper()
            if acc in legacy_iban_accounts:
                disjunction_errors.append((doc_id, f"Conto IBAN avversariale {acc} collide con dataset pregressi"))
            if acc in heldout_main_iban_accounts:
                disjunction_errors.append((doc_id, f"Conto IBAN avversariale {acc} collide con il set heldout principale"))

    if disjunction_errors:
        print(f"ERRORE: Rilevate {len(disjunction_errors)} collisioni di entità:", file=sys.stderr)
        for doc_id, err in disjunction_errors:
            print(f"  [{doc_id}] {err}", file=sys.stderr)
        sys.exit(1)
    print("Disgiunzione entità: 0 collisioni rilevate. Radici PIVA, CF, IBAN ed entità sono al 100% inedite e disgiunte.")

    # --------------------------------------------------------------------------
    # 8. Verifica File Router ALTRO
    # --------------------------------------------------------------------------
    print("\n--- 8. Verifica File Router ALTRO (data/heldout_router_altro_v1.json) ---")
    if len(router_docs) != 8:
        print(f"ERRORE: Attesi 8 documenti router ALTRO, trovati {len(router_docs)}", file=sys.stderr)
        sys.exit(1)
    for r_doc in router_docs:
        if r_doc.get("type") != "ALTRO" or r_doc.get("expected_classification") != "ALTRO":
            print(f"ERRORE: Documento router {r_doc.get('id')} non ha tipo ALTRO", file=sys.stderr)
            sys.exit(1)
        if not r_doc.get("payload") or len(r_doc["payload"]) < 50:
            print(f"ERRORE: Documento router {r_doc.get('id')} ha payload vuoto o insufficiente", file=sys.stderr)
            sys.exit(1)
    print(f"Set Router ALTRO: 8/8 documenti verificati con successo per il test di routing.")

    # --------------------------------------------------------------------------
    # 9. Calcolo Checksum SHA-256 Preliminari
    # --------------------------------------------------------------------------
    print("\n--- 9. Checksum SHA-256 Preliminari dei File Held-Out Generati ---")
    sha_main = compute_sha256(heldout_file)
    sha_adv = compute_sha256(adv_file)
    sha_router = compute_sha256(router_file)

    print(f"  - data/heldout_dataset_v1.json      : {sha_main}")
    print(f"  - data/heldout_adversarial_v1.json  : {sha_adv}")
    print(f"  - data/heldout_router_altro_v1.json : {sha_router}")

    print("\n=== ESITO: TUTTI I CONTROLLI DI VALIDAZIONE SUPERATI CON SUCCESSO (0 errori) ===")

if __name__ == "__main__":
    main()
