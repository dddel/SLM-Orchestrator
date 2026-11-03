#!/usr/bin/env python3
"""
sanitize_and_freeze_phase_b_dataset.py

Applica la correzione algoritmica di validità a tutti i Codici Fiscali, Partite IVA e IBAN
presenti nel dataset della Fase B (secondo la condizione B0 del disciplinare), aggiunge i
campioni a contenuto neutro (B2), verifica matematicamente ogni entità e salva il dataset congelato
con impronta crittografica SHA-256.
"""

import json
import re
import hashlib
import os

ODD_VALUES = {
    "0": 1, "1": 0, "2": 5, "3": 7, "4": 9, "5": 13, "6": 15, "7": 17, "8": 19, "9": 21,
    "A": 1, "B": 0, "C": 5, "D": 7, "E": 9, "F": 13, "G": 15, "H": 17, "I": 19, "J": 21,
    "K": 2, "L": 4, "M": 18, "N": 20, "O": 11, "P": 3, "Q": 6, "R": 8, "S": 12, "T": 14,
    "U": 16, "V": 10, "W": 22, "X": 25, "Y": 24, "Z": 23
}
EVEN_VALUES = {
    "0": 0, "1": 1, "2": 2, "3": 3, "4": 4, "5": 5, "6": 6, "7": 7, "8": 8, "9": 9,
    "A": 0, "B": 1, "C": 2, "D": 3, "E": 4, "F": 5, "G": 6, "H": 7, "I": 8, "J": 9,
    "K": 10, "L": 11, "M": 12, "N": 13, "O": 14, "P": 15, "Q": 16, "R": 17, "S": 18, "T": 19,
    "U": 20, "V": 21, "W": 22, "X": 23, "Y": 24, "Z": 25
}

def make_valid_cf(prefix15):
    p = prefix15.upper()[:15]
    tot = sum(ODD_VALUES[c] if i % 2 == 0 else EVEN_VALUES[c] for i, c in enumerate(p))
    check = chr(ord("A") + (tot % 26))
    return p + check

def is_valid_cf(cf):
    cf = cf.upper()
    if not re.match(r"^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPR-T][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$", cf):
        return False
    omocodia = {"L": "0", "M": "1", "N": "2", "P": "3", "Q": "4", "R": "5", "S": "6", "T": "7", "U": "8", "V": "9"}
    chars = list(cf)
    for i in [6, 7, 9, 10, 12, 13, 14]:
        if chars[i] in omocodia:
            chars[i] = omocodia[chars[i]]
    tot = sum(ODD_VALUES[c] if i % 2 == 0 else EVEN_VALUES[c] for i, c in enumerate(chars[:15]))
    return cf[15] == chr(ord("A") + (tot % 26))

def make_valid_piva(prefix10):
    p = prefix10[:10]
    s_odd = sum(int(p[i]) for i in range(0, 10, 2))
    s_even = sum(int(p[i])*2 if int(p[i])*2 < 10 else int(p[i])*2 - 9 for i in range(1, 10, 2))
    check = (10 - ((s_odd + s_even) % 10)) % 10
    return p + str(check)

def is_valid_piva(piva):
    if not re.match(r"^[0-9]{11}$", piva):
        return False
    s_odd = sum(int(piva[i]) for i in range(0, 10, 2))
    s_even = sum(int(piva[i])*2 if int(piva[i])*2 < 10 else int(piva[i])*2 - 9 for i in range(1, 10, 2))
    return int(piva[10]) == (10 - ((s_odd + s_even) % 10)) % 10

def make_valid_iban(abi="03069", cab="09606", account="100000012345"):
    bban_tail = f"{abi}{cab}{account}"
    tot = sum(ODD_VALUES[c] if i % 2 == 0 else EVEN_VALUES[c] for i, c in enumerate(bban_tail))
    cin = chr(ord("A") + (tot % 26))
    bban = f"{cin}{abi}{cab}{account}"
    rearranged = bban + "IT00"
    num = "".join(str(ord(c) - ord("A") + 10) if c.isalpha() else c for c in rearranged)
    mod = int(num) % 97
    kk = f"{98 - mod:02d}"
    return f"IT{kk}{bban}"

def is_valid_iban(iban):
    iban = iban.replace(" ", "").upper()
    if len(iban) != 27 or not iban.startswith("IT"):
        return False
    rearranged = iban[4:] + iban[:4]
    numeric = "".join(str(ord(c) - ord("A") + 10) if c.isalpha() else c for c in rearranged)
    return int(numeric) % 97 == 1

# Tabella di sostituzione deterministica per CF, P.IVA e IBAN non validi
CF_REPLACEMENTS = {
    "FRRGLI92M64D612T": make_valid_cf("FRRGLI92M64D612"),
    "SPZSVN78S17F839Z": make_valid_cf("SPZSVN78S17F839"),
    "RSSMRA85T10H501K": make_valid_cf("RSSMRA85T10H501"),
    "BNCRRT75H14A944U": make_valid_cf("BNCRRT75H14A944"),
    "MRTLNE78P69F257D": make_valid_cf("MRTLNE78P69F257"),
    "BNCLNZ08A05A944K": make_valid_cf("BNCLNZ08A05A944"),
    "MCNRRT64S27E388A": make_valid_cf("MCNRRT64S27E388"),
    "GLLLRA91D55L219Z": make_valid_cf("GLLLRA91D55L219"),
    "VLNLCU82C10F205P": make_valid_cf("VLNLCU82C10F205"),
    "RSSNTN80E03D643W": make_valid_cf("RSSNTN80E03D643"),
    "MNDSRA90H50L736P": make_valid_cf("MNDSRA90H50L736"),
    "BNCMR88A12H501Y": make_valid_cf("BNCMRC88A12H501"),
}

PIVA_REPLACEMENTS = {
    "02938470981": make_valid_piva("0293847098"),
    "09876543211": make_valid_piva("0987654321"),
    "01122334455": make_valid_piva("0112233445"),
    "02391020192": make_valid_piva("0239102019"),
}

IBAN_REPLACEMENTS = {
    "IT44X0200801102000000456789": make_valid_iban(abi="02008", cab="01102", account="000000456789"),
    "IT02L0100503382000000112233": make_valid_iban(abi="01005", cab="03382", account="000000112233"),
    "IT99A0306912345100000098765": make_valid_iban(abi="03069", cab="12345", account="100000098765"),
    "IT60X0542811101000000123456": make_valid_iban(abi="05428", cab="11101", account="000000984512"),
}

NEUTRAL_SAMPLES = [
    {
        "id": "N_01",
        "category": "neutral",
        "text": "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Testo latino fittizio privo di qualsiasi semantica aziendale o fattuale",
        "review": False
    },
    {
        "id": "N_02",
        "category": "neutral",
        "text": "La fotosintesi clorofilliana è un processo biochimico attraverso il quale gli organismi autotrofi convertono energia luminosa in energia chimica, producendo glucosio e ossigeno a partire da anidride carbonica e acqua.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Trattazione scientifica e biologica astratta",
        "review": False
    },
    {
        "id": "N_03",
        "category": "neutral",
        "text": "Sequenza numerica rilevata: 4, 8, 15, 16, 23, 42, 108, 256, 512, 1024. Parametri fisici costanti registrati a intervalli regolari di sessanta secondi.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Sequenza di numeri e parametri priva di intestazioni e ruoli",
        "review": False
    },
    {
        "id": "N_04",
        "category": "neutral",
        "text": "Ricetta per torta di mele soffice: unire 300 grammi di farina, 150 grammi di zucchero semolato, due uova intere e 80 grammi di burro fuso. Aggiungere le mele tagliate a fettine e infornare a 180 gradi per 40 minuti.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Istruzioni culinarie prive di coordinate contabili o formali",
        "review": False
    },
    {
        "id": "N_05",
        "category": "neutral",
        "text": "Il cielo sopra il porto aveva il colore di uno schermo televisivo sintonizzato su un canale morto. Il vento freddo sollevava mulinelli di polvere lungo il molo deserto nelle prime ore del mattino.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Brano letterario descrittivo cyberpunk/narrativo",
        "review": False
    },
    {
        "id": "N_06",
        "category": "neutral",
        "text": "Bollettino meteorologico regionale: condizioni di cielo sereno o poco nuvoloso al mattino con locali addensamenti cumuliformi pomeridiani sui rilievi appenninici. Venti deboli variabili, mari calmi.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Notiziario meteo standard senza riferimenti documentali",
        "review": False
    },
    {
        "id": "N_07",
        "category": "neutral",
        "text": "In informatica teorica, una macchina di Turing è un dispositivo matematico ideale che manipola simboli su un nastro infinito secondo un insieme finito di istruzioni ben definite.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Definizione formale di logica matematica e informatica",
        "review": False
    },
    {
        "id": "N_08",
        "category": "neutral",
        "text": "Le colline toscane si estendono a perdita d'occhio con filari di ulivi secolari e cipressi che costeggiano le antiche strade poderali in terra battuta.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Descrizione paesaggistica naturalistica",
        "review": False
    },
    {
        "id": "N_09",
        "category": "neutral",
        "text": "Studio di composizione armonica: progressione dal primo grado in Do maggiore verso il sesto grado in La minore settima, con modulazione alla dominante in Sol maggiore.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Teoria musicale astratta",
        "review": False
    },
    {
        "id": "N_10",
        "category": "neutral",
        "text": "Scheda mineralogica: cristallo di pirite con abito cubico regolare e marcata lucentezza metallica dorata, accresciuto su una matrice di quarzo ialino microcristallino.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Descrizione geologica di campioni minerali",
        "review": False
    },
    {
        "id": "N_11",
        "category": "neutral",
        "text": "Regolamento del gioco degli scacchi: ciascun giocatore dispone di sedici pezzi. L'obiettivo primario consiste nel porre il re avversario sotto scacco irreversibile denominato scacco matto.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Regole ludiche scacchistiche",
        "review": False
    },
    {
        "id": "N_12",
        "category": "neutral",
        "text": "Esperimento di ottica geometrica: il passaggio di un fascio di luce bianca collimata attraverso un prisma di vetro flint provoca la scomposizione cromatica dello spettro visibile.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Principio fisico di rifrazione della luce",
        "review": False
    },
    {
        "id": "N_13",
        "category": "neutral",
        "text": "Consigli di botanica domestica: durante il riposo vegetativo invernale le annaffiature delle piante succulente devono essere drasticamente ridotte per prevenire il marciume radicale.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Note di coltivazione botanica",
        "review": False
    },
    {
        "id": "N_14",
        "category": "neutral",
        "text": "Appunti della giornata: ore 07:30 risveglio e colazione; ore 08:30 camminata nel parco comunale; ore 10:00 consultazione volume di storia rinascimentale in biblioteca.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Diario personale quotidiano privo di mittenti o ruoli aziendali",
        "review": False
    },
    {
        "id": "N_15",
        "category": "neutral",
        "text": "Teorema fondamentale dell'aritmetica: ogni numero intero strettamente maggiore di uno può essere espresso in modo unico come prodotto di fattori primi, a meno dell'ordine dei fattori.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Enunciato di teoria dei numeri",
        "review": False
    },
    {
        "id": "N_16",
        "category": "neutral",
        "text": "Idrostatica classica: ogni corpo immerso parzialmente o totalmente in un fluido a riposo sperimenta una forza verticale diretta verso l'alto pari al peso del volume di fluido spostato.",
        "primary": "ALTRO",
        "admissible": ["ALTRO", "FATTURA", "EMAIL", "ANAGRAFICA"],
        "rationale": "Principio fisico di Archimede",
        "review": False
    }
]

def sanitize_text(text):
    for old_cf, new_cf in CF_REPLACEMENTS.items():
        text = text.replace(old_cf, new_cf)
    for old_piva, new_piva in PIVA_REPLACEMENTS.items():
        text = text.replace(old_piva, new_piva)
    for old_iban, new_iban in IBAN_REPLACEMENTS.items():
        text = text.replace(old_iban, new_iban)
    return text

def main():
    src_path = "scripts/data/phase_b_dataset.json"
    with open(src_path) as f:
        data = json.load(f)

    # Sanitize router samples
    for s in data["router_samples"]:
        s["text"] = sanitize_text(s["text"])

    # Sanitize email role samples
    for s in data["email_role_samples"]:
        s["text"] = sanitize_text(s["text"])

    # Add neutral samples
    data["neutral_samples"] = NEUTRAL_SAMPLES

    # Update metadata
    data["metadata"]["neutral_samples_count"] = len(NEUTRAL_SAMPLES)
    data["metadata"]["checksum_validation"] = "100% compliant with Agenzia delle Entrate CF, Luhn P.IVA, ISO 7064 mod 97 IBAN"

    # Verify all entities
    all_samples = data["router_samples"] + data["email_role_samples"] + data["neutral_samples"]
    cfs_tested = 0
    pivas_tested = 0
    ibans_tested = 0

    for s in all_samples:
        txt = s["text"]
        for m in re.finditer(r"\b[A-Z]{6}[0-9]{2}[A-EHLMPR-T][0-9]{2}[A-Z][0-9]{3}[A-Z]\b", txt):
            cf = m.group(0)
            assert is_valid_cf(cf), f"CF NON VALIDO in {s['id']}: {cf}"
            cfs_tested += 1

        for m in re.finditer(r"(?:P\.?\s*IVA|Partita\s+IVA)\s*:?\s*([0-9]{11})\b", txt, re.IGNORECASE):
            piva = m.group(1)
            assert is_valid_piva(piva), f"PIVA NON VALIDA in {s['id']}: {piva}"
            pivas_tested += 1

        for m in re.finditer(r"\b(IT[0-9]{2}[A-Z][0-9]{10}[0-9A-Z]{12})\b", txt):
            iban = m.group(1)
            assert is_valid_iban(iban), f"IBAN NON VALIDO in {s['id']}: {iban}"
            ibans_tested += 1

    # Save finalized dataset
    out_json = json.dumps(data, indent=2, ensure_ascii=False)
    with open(src_path, "w", encoding="utf-8") as f:
        f.write(out_json)

    sha256 = hashlib.sha256(out_json.encode("utf-8")).hexdigest()
    print("================================================================================")
    print("DATASET FASE B CONGELATO CON SUCCESSO")
    print(f"File: {src_path}")
    print(f"SHA-256: {sha256}")
    print(f"Campioni Router: {len(data['router_samples'])}")
    print(f"Campioni Email Ruoli: {len(data['email_role_samples'])}")
    print(f"Campioni Neutri: {len(data['neutral_samples'])}")
    print(f"CF verificati al 100%: {cfs_tested}")
    print(f"P.IVA verificate al 100%: {pivas_tested}")
    print(f"IBAN verificati al 100%: {ibans_tested}")
    print("================================================================================")

if __name__ == "__main__":
    main()
