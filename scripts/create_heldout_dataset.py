#!/usr/bin/env python3
"""
Script di generazione deterministica del dataset held-out (Passo 3).
Produce:
  - data/heldout_dataset_v1.json (42 documenti principali nei 3 domini strutturati: FAT, ANA, EML)
  - data/heldout_adversarial_v1.json (6 documenti avversariali con violazione isolata e radici disgiunte)
  - data/heldout_router_altro_v1.json (8 documenti di tipo ALTRO per test di routing)

Isolamento informativo: generato unicamente a partire dagli schemi JSON in
src/main/resources/schemas/ e dagli standard matematici italiani (CF, P.IVA, IBAN).
Nessun cue meta-testuale artificiale; formattazione valute italiana naturale;
coerenza anagrafica incrociata al 100%.
"""

import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = REPO_ROOT / "data"

# ==============================================================================
# Funzioni per il calcolo e validazione rigorosa dei Checksum
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

def generate_coherent_cf(cognome: str, nome: str, day: int, month: int, year: int, sesso: str, belfiore: str) -> str:
    cg = cf_encode_cognome(cognome)
    nm = cf_encode_nome(nome)
    yr = f'{year % 100:02d}'
    mo = CF_MONTH_MAP[month]
    d = day + (40 if sesso.upper() == 'F' else 0)
    dy = f'{d:02d}'
    base15 = f'{cg}{nm}{yr}{mo}{dy}{belfiore.upper()}'
    return base15 + compute_cf_check(base15)

def compute_piva_check(piva10: str) -> str:
    s = 0
    for i, c in enumerate(piva10):
        d = int(c)
        if i % 2 == 1:
            d2 = d * 2
            s += d2 - 9 if d2 > 9 else d2
        else:
            s += d
    return str((10 - (s % 10)) % 10)

def make_valid_piva(piva10: str) -> str:
    return piva10 + compute_piva_check(piva10)

def make_invalid_piva(piva10: str) -> str:
    correct = compute_piva_check(piva10)
    wrong = str((int(correct) + 5) % 10)
    return piva10 + wrong

def compute_iban_check(cin_abi_cab_cc: str) -> str:
    base = cin_abi_cab_cc.upper() + '182900'
    digits = ''.join(c if c.isdigit() else str(ord(c) - ord('A') + 10) for c in base)
    chk = 98 - (int(digits) % 97)
    return f'{chk:02d}'

def make_valid_iban(cin_abi_cab_cc: str) -> str:
    chk = compute_iban_check(cin_abi_cab_cc)
    return f'IT{chk}{cin_abi_cab_cc.upper()}'

def make_invalid_iban(cin_abi_cab_cc: str) -> str:
    correct = compute_iban_check(cin_abi_cab_cc)
    wrong = f'{(int(correct) + 25) % 97:02d}'
    if wrong == correct:
        wrong = '99'
    return f'IT{wrong}{cin_abi_cab_cc.upper()}'


# ==============================================================================
# Costruzione del Set Principale Held-Out (42 Documenti: 14 FAT, 14 ANA, 14 EML)
# ==============================================================================

def build_heldout_dataset():
    docs = []

    # --------------------------------------------------------------------------
    # 1. FATTURA (14 Documenti)
    # --------------------------------------------------------------------------

    # HO_FAT_01: Parcella ingegnere civile discorsiva (senza CF cliente)
    piva_01 = make_valid_piva("0412893048")
    iban_01 = make_valid_iban("S0306909606100000019482")
    docs.append({
        "id": "HO_FAT_01",
        "type": "FATTURA",
        "template": "FAT_PARCELLA_DISCORSIVA",
        "payload": (
            "STUDIO DI INGEGNERIA CIVILE ING. MATTEO VALENTINI\n"
            f"Via delle Terme 42, 50123 Firenze (FI) - P.IVA {piva_01}\n\n"
            "Fattura Professionale n. 2026/PAR-084 emessa in data 12/03/2026\n"
            "Spettabile Committente: Impresa Costruzioni Generali Edilizia Moderna S.p.A.\n"
            "Cantiere: Riqualificazione strutturale compendio immobiliare 'Le Terrazze'.\n\n"
            "Oggetto: Onorario per collaudo statico in corso d'opera delle strutture in c.a.\n"
            "Imponibile concordato: Euro 2.800,00\n"
            "Contributo previdenziale Cassa Ingegneri (4%): Euro 112,00\n"
            "Imponibile IVA: Euro 2.912,00\n"
            "IVA ordinaria 22%: Euro 640,64\n"
            "Totale dovuto da saldare: Euro 3.552,64\n\n"
            "Estremi di pagamento: Bonifico Bancario presso Banca Monte dei Paschi di Siena\n"
            f"IBAN: {iban_01}\n"
            "Condizioni: rimessa a 30 giorni data fattura."
        ),
        "ground_truth": {
            "numeroFattura": "2026/PAR-084",
            "dataFattura": "12/03/2026",
            "fornitore": {
                "denominazione": "STUDIO DI INGEGNERIA CIVILE ING. MATTEO VALENTINI",
                "partitaIva": piva_01
            },
            "cliente": {
                "denominazione": "Impresa Costruzioni Generali Edilizia Moderna S.p.A.",
                "codiceFiscale": None
            },
            "importoTotale": 3552.64,
            "iban": iban_01
        }
    })

    # HO_FAT_02: Fattura fornitura industriale con cliente estero (senza CF cliente)
    piva_02 = make_valid_piva("0184729024")
    iban_02 = make_valid_iban("A0503411701000000049182")
    docs.append({
        "id": "HO_FAT_02",
        "type": "FATTURA",
        "template": "FAT_TABELLARE_COMPATTA",
        "payload": (
            "MECCANICA DI PRECISIONE VENETA S.R.L.\n"
            "Via dell'Artigianato 18, 36015 Schio (VI)\n"
            f"Partita IVA: {piva_02}\n\n"
            "DOCUMENTO: FATTURA COMMERCIALE\n"
            "Numero documento: 2026-FATT-00412\n"
            "Data emissione: 18/02/2026\n\n"
            "DESTINATARIO: BAVARIA MOTORWERKE GMBH\n"
            "Industriestrasse 94, 80331 Monaco di Baviera (Germania)\n\n"
            "Dettaglio Fornitura:\n"
            "- N. 40 Cuscinetti speciali autolubrificanti cod. CS-88: Euro 840,00\n"
            "- N. 12 Alberi di trasmissione rettificati cod. AT-102: Euro 489,80\n"
            "Operazione intracomunitaria non imponibile art. 41 D.L. 331/93.\n"
            "Totale Documento: € 1.329,80\n\n"
            f"Pagamento mediante bonifico estero su IBAN: {iban_02}"
        ),
        "ground_truth": {
            "numeroFattura": "2026-FATT-00412",
            "dataFattura": "18/02/2026",
            "fornitore": {
                "denominazione": "MECCANICA DI PRECISIONE VENETA S.R.L.",
                "partitaIva": piva_02
            },
            "cliente": {
                "denominazione": "BAVARIA MOTORWERKE GMBH",
                "codiceFiscale": None
            },
            "importoTotale": 1329.80,
            "iban": iban_02
        }
    })

    # HO_FAT_03: Fattura consulenza direzionale B2B senza IBAN (Ri.Ba.) e senza CF cliente a 16 caratteri
    piva_03 = make_valid_piva("0923481015")
    docs.append({
        "id": "HO_FAT_03",
        "type": "FATTURA",
        "template": "FAT_B2B_CONSULENZA",
        "payload": (
            "STRATEGIA & SVILUPPO MANAGEMENT CONSULTING S.R.L.\n"
            f"Piazza Cavour 7, 20121 Milano (MI) - C.F. e P.IVA: {piva_03}\n\n"
            "Fattura Elettronica n. FT-2026-099 del 05/03/2026\n"
            "Cliente: OROLOGERIE MILANESI S.P.A.\n"
            "Sede: Corso Magenta 45, 20123 Milano (MI)\n\n"
            "Descrizione prestazioni:\n"
            "Attività di assessment organizzativo e ottimizzazione supply chain (Fase 1)\n"
            "Importo imponibile: Euro 4.000,00\n"
            "IVA 22%: Euro 880,00\n"
            "Importo Totale Fattura: Euro 4.880,00\n\n"
            "Modalità di regolamento: Ricevuta Bancaria (Ri.Ba.) a 60 giorni data fattura fine mese."
        ),
        "ground_truth": {
            "numeroFattura": "FT-2026-099",
            "dataFattura": "05/03/2026",
            "fornitore": {
                "denominazione": "STRATEGIA & SVILUPPO MANAGEMENT CONSULTING S.R.L.",
                "partitaIva": piva_03
            },
            "cliente": {
                "denominazione": "OROLOGERIE MILANESI S.P.A.",
                "codiceFiscale": None
            },
            "importoTotale": 4880.00,
            "iban": None
        }
    })

    # HO_FAT_04: Canone di locazione commerciale a persona fisica senza CF e senza IBAN (addebito SDD)
    piva_04 = make_valid_piva("0319482037")
    docs.append({
        "id": "HO_FAT_04",
        "type": "FATTURA",
        "template": "FAT_LOCAZIONE_IMMOBILE",
        "payload": (
            "IMMOBILIARE BOLOGNESE DI VALERIO SERENA & C. S.A.S.\n"
            "Via Santo Stefano 110, 40125 Bologna (BO)\n"
            f"P.IVA: {piva_04}\n\n"
            "FATTURA N. LOC-2026/03 Data: 01/03/2026\n"
            "Intestata a: Dott.ssa Elena Ferri, conduttrice locali commerciali Via Ugo Bassi 14\n\n"
            "Canone di locazione mese di Marzo 2026: Euro 2.000,00\n"
            "IVA applicata (22%): Euro 440,00\n"
            "Totale da corrispondere: Euro 2.440,00\n\n"
            "Modalità di pagamento: Addebito diretto SDD già concordato sul conto corrente del conduttore."
        ),
        "ground_truth": {
            "numeroFattura": "LOC-2026/03",
            "dataFattura": "01/03/2026",
            "fornitore": {
                "denominazione": "IMMOBILIARE BOLOGNESE DI VALERIO SERENA & C. S.A.S.",
                "partitaIva": piva_04
            },
            "cliente": {
                "denominazione": "Dott.ssa Elena Ferri",
                "codiceFiscale": None
            },
            "importoTotale": 2440.00,
            "iban": None
        }
    })

    # HO_FAT_05: Materiale edile con CF cliente persona fisica senza IBAN (POS al carico)
    piva_05 = make_valid_piva("0549281028")
    cf_cli_05 = generate_coherent_cf("Rinaldi", "Stefano", 14, 6, 1978, "M", "G224")
    docs.append({
        "id": "HO_FAT_05",
        "type": "FATTURA",
        "template": "FAT_MATERIALI_EDILI",
        "payload": (
            "EDILIZIA PADOVANA FORNITURE S.R.L.\n"
            "Corso Stati Uniti 33, 35127 Padova (PD)\n"
            f"Partita IVA Fornitore: {piva_05}\n\n"
            "Fattura di vendita n. ED-892/2026 emessa il 24/02/2026\n"
            "Spettabile Sig. Stefano Rinaldi\n"
            f"Codice Fiscale: {cf_cli_05}\n"
            "Via Venezia 82, 35129 Padova (PD)\n\n"
            "Materiale: Fornitura isolante termico lana di roccia spessore 12cm.\n"
            "Imponibile merci: Euro 5.500,00\n"
            "Aliquota IVA 22%: Euro 1.210,00\n"
            "Totale Documento Fiscale: Euro 6.710,00\n\n"
            "Modalità di pagamento: Transazione POS bancomat al momento del carico merci presso il deposito."
        ),
        "ground_truth": {
            "numeroFattura": "ED-892/2026",
            "dataFattura": "24/02/2026",
            "fornitore": {
                "denominazione": "EDILIZIA PADOVANA FORNITURE S.R.L.",
                "partitaIva": piva_05
            },
            "cliente": {
                "denominazione": "Stefano Rinaldi",
                "codiceFiscale": cf_cli_05
            },
            "importoTotale": 6710.00,
            "iban": None
        }
    })

    # HO_FAT_06: Servizi Cloud con pagamento su carta di credito (senza IBAN)
    piva_06 = make_valid_piva("0782394096")
    docs.append({
        "id": "HO_FAT_06",
        "type": "FATTURA",
        "template": "FAT_SAAS_CLOUD",
        "payload": (
            "NUVOLA DIGITALE SOLUTIONS S.R.L.\n"
            "Viale Certosa 130, 20156 Milano (MI)\n"
            f"P.IVA: {piva_06}\n\n"
            "RICEVUTA FATTURA N. SAAS-2026-1104 del 15/01/2026\n"
            "Acquirente: STUDIO ARCHITETTURA ASSOCIATI BIANCHI E NERI\n"
            "Canone abbonamento Cloud Storage & Compute dedicato 12 mesi.\n"
            "Prezzo imponibile: Euro 700,00\n"
            "IVA 22%: Euro 154,00\n"
            "Totale transazione: Euro 854,00\n\n"
            "Metodo di pagamento: Addebito automatico su Carta di Credito Corporate terminante con 4091.\n"
            "Transazione autorizzata con successo con codice AUTH-883921."
        ),
        "ground_truth": {
            "numeroFattura": "SAAS-2026-1104",
            "dataFattura": "15/01/2026",
            "fornitore": {
                "denominazione": "NUVOLA DIGITALE SOLUTIONS S.R.L.",
                "partitaIva": piva_06
            },
            "cliente": {
                "denominazione": "STUDIO ARCHITETTURA ASSOCIATI BIANCHI E NERI",
                "codiceFiscale": None
            },
            "importoTotale": 854.00,
            "iban": None
        }
    })

    # HO_FAT_07: Arredi ufficio B2B senza CF cliente
    piva_07 = make_valid_piva("0294817023")
    iban_07 = make_valid_iban("B0503411701000000091847")
    docs.append({
        "id": "HO_FAT_07",
        "type": "FATTURA",
        "template": "FAT_ARREDI_COMMERCIALI",
        "payload": (
            "DESIGN UFFICIO & CONTRACT VERONA S.R.L.\n"
            f"Viale del Lavoro 25, 37135 Verona (VR) - P.IVA {piva_07}\n\n"
            "Fattura di vendita N. 2026/AR-045 Data: 20/02/2026\n"
            "Cliente: LOGISTICA SCALIGERA S.R.L.\n"
            "Viale Europa 12, Verona\n"
            "Fornitura 10 scrivanie operative regolabili e 10 poltrone ergonomiche.\n"
            "Subtotale: Euro 4.200,00\n"
            "IVA al 22%: Euro 924,00\n"
            "Importo Totale da Saldare: Euro 5.124,00\n\n"
            "Bonifico a favore di Design Ufficio Verona:\n"
            f"IBAN: {iban_07}"
        ),
        "ground_truth": {
            "numeroFattura": "2026/AR-045",
            "dataFattura": "20/02/2026",
            "fornitore": {
                "denominazione": "DESIGN UFFICIO & CONTRACT VERONA S.R.L.",
                "partitaIva": piva_07
            },
            "cliente": {
                "denominazione": "LOGISTICA SCALIGERA S.R.L.",
                "codiceFiscale": None
            },
            "importoTotale": 5124.00,
            "iban": iban_07
        }
    })

    # HO_FAT_08: Manutenzione impianti senza CF cliente e senza IBAN (MAV)
    piva_08 = make_valid_piva("0481923016")
    docs.append({
        "id": "HO_FAT_08",
        "type": "FATTURA",
        "template": "FAT_MANUTENZIONE_FOTOVOLTAICO",
        "payload": (
            "ECO-ENERGY IMPIANTI BERGAMO S.R.L.\n"
            "Via Borgo Palazzo 88, 24125 Bergamo (BG)\n"
            f"Partita IVA: {piva_08}\n\n"
            "FATTURA N. 2026-F-189\n"
            "Data emissione: 02/03/2026\n"
            "Destinatario fattura: Condominio Parco dei Fiori\n"
            "Intervento: Manutenzione straordinaria inverter e lavaggio pannelli impianto fotovoltaico 50kWp.\n"
            "Corrispettivo pattuito: Euro 1.600,00\n"
            "IVA 22%: Euro 352,00\n"
            "Totale Documento: Euro 1.952,00\n\n"
            "Condizioni di pagamento: Emissione bollettino MAV bancario inviato separatamente a mezzo posta."
        ),
        "ground_truth": {
            "numeroFattura": "2026-F-189",
            "dataFattura": "02/03/2026",
            "fornitore": {
                "denominazione": "ECO-ENERGY IMPIANTI BERGAMO S.R.L.",
                "partitaIva": piva_08
            },
            "cliente": {
                "denominazione": "Condominio Parco dei Fiori",
                "codiceFiscale": None
            },
            "importoTotale": 1952.00,
            "iban": None
        }
    })

    # HO_FAT_09: Fornitura DPI (senza CF cliente, senza IBAN - pagamento contanti al ritiro)
    piva_09 = make_valid_piva("0619284004")
    docs.append({
        "id": "HO_FAT_09",
        "type": "FATTURA",
        "template": "FAT_DPI_CASSA",
        "payload": (
            "SICUREZZA & LAVORO PIEMONTE S.A.S.\n"
            "Via Cuneo 12, 12100 Cuneo (CN) - P.IVA: " + piva_09 + "\n\n"
            "Fattura di vendita immediata N. VEND-2026-081 del 10/02/2026\n"
            "Acquirente: ALPES CONSTRUCTIONS SARL\n"
            "Fornitura n. 30 elmetti protettivi e calzature di sicurezza.\n"
            "Totale imponibile: Euro 600,00\n"
            "IVA 22%: Euro 132,00\n"
            "Totale Complessivo: Euro 732,00\n\n"
            "Pagamento: Saldato per contanti alla consegna presso nostro magazzino con emissione di quietanza."
        ),
        "ground_truth": {
            "numeroFattura": "VEND-2026-081",
            "dataFattura": "10/02/2026",
            "fornitore": {
                "denominazione": "SICUREZZA & LAVORO PIEMONTE S.A.S.",
                "partitaIva": piva_09
            },
            "cliente": {
                "denominazione": "ALPES CONSTRUCTIONS SARL",
                "codiceFiscale": None
            },
            "importoTotale": 732.00,
            "iban": None
        }
    })

    # HO_FAT_10: Parcella legale con cliente persona fisica e CF
    piva_10 = make_valid_piva("0812947058")
    cf_cli_10 = generate_coherent_cf("Marchetti", "Giulia", 5, 8, 1982, "F", "H501")
    iban_10 = make_valid_iban("F0200805000000104829104")
    docs.append({
        "id": "HO_FAT_10",
        "type": "FATTURA",
        "template": "FAT_PARCELLA_LEGALE",
        "payload": (
            "STUDIO LEGALE AVV. GABRIELE MORETTI\n"
            f"Via Barberini 16, 00187 Roma (RM) - Partita IVA: {piva_10}\n\n"
            "Fattura n. LEG-2026/019 emessa il 16/03/2026\n"
            "Cliente assistito: Sig.ra Giulia Marchetti\n"
            f"Codice Fiscale: {cf_cli_10}\n"
            "Oggetto: Assistenza legale stragiudiziale per stipula contratto di locazione ad uso commerciale.\n"
            "Compensi professionali: Euro 3.200,00\n"
            "IVA 22%: Euro 704,00\n"
            "Importo Totale Dovuto: Euro 3.904,00\n\n"
            "Pagamento su conto corrente studio:\n"
            f"IBAN: {iban_10}"
        ),
        "ground_truth": {
            "numeroFattura": "LEG-2026/019",
            "dataFattura": "16/03/2026",
            "fornitore": {
                "denominazione": "STUDIO LEGALE AVV. GABRIELE MORETTI",
                "partitaIva": piva_10
            },
            "cliente": {
                "denominazione": "Giulia Marchetti",
                "codiceFiscale": cf_cli_10
            },
            "importoTotale": 3904.00,
            "iban": iban_10
        }
    })

    # HO_FAT_11: Trasporto merci a mezzo Ri.Ba. (senza IBAN, senza CF cliente)
    piva_11 = make_valid_piva("0192837036")
    docs.append({
        "id": "HO_FAT_11",
        "type": "FATTURA",
        "template": "FAT_TRASPORTI_RIBA",
        "payload": (
            "AUTOTRASPORTI EMILIANI DI FERRARI LUCA S.R.L.\n"
            "Via Emilia Ovest 210, 41123 Modena (MO)\n"
            f"Partita IVA: {piva_11}\n\n"
            "Fattura Trasporti n. TR-2026-033 del 28/02/2026\n"
            "Destinatario: CERAMICHE MODERNE DEL DISTRETTO S.P.A.\n"
            "Servizio: Trasporto con bilico autoarticolato Modena - Lione.\n"
            "Prezzo trasporto: Euro 1.800,00\n"
            "IVA 22%: Euro 396,00\n"
            "Totale Documento: Euro 2.196,00\n\n"
            "Modalità di pagamento: Ricevuta Bancaria (Ri.Ba.) a 60 giorni d.f. f.m. tramite appoggio bancario abituale."
        ),
        "ground_truth": {
            "numeroFattura": "TR-2026-033",
            "dataFattura": "28/02/2026",
            "fornitore": {
                "denominazione": "AUTOTRASPORTI EMILIANI DI FERRARI LUCA S.R.L.",
                "partitaIva": piva_11
            },
            "cliente": {
                "denominazione": "CERAMICHE MODERNE DEL DISTRETTO S.P.A.",
                "codiceFiscale": None
            },
            "importoTotale": 2196.00,
            "iban": None
        }
    })

    # HO_FAT_12: Grafica e stampa B2B senza CF cliente
    piva_12 = make_valid_piva("0384729019")
    iban_12 = make_valid_iban("G0503411701000000082736")
    docs.append({
        "id": "HO_FAT_12",
        "type": "FATTURA",
        "template": "FAT_GRAFICA_STAMPA",
        "payload": (
            "ARTI GRAFICHE E COMUNICAZIONE CREMONESE S.N.C.\n"
            "Via Mantova 45, 26100 Cremona (CR)\n"
            f"P.IVA: {piva_12}\n\n"
            "FATTURA N. 2026/077 Data: 11/03/2026\n"
            "Spettabile: CASEIFICIO TIPICO DEL PO S.R.L.\n"
            "Via Argine 8, Cremona\n"
            "Stampa cataloghi promozionali ed etichette autoadesive alimentari.\n"
            "Imponibile: Euro 1.300,00\n"
            "IVA 22%: Euro 286,00\n"
            "Totale a Vostro Carico: Euro 1.586,00\n\n"
            "Bonifico su conto Credito Padano:\n"
            f"IBAN: {iban_12}"
        ),
        "ground_truth": {
            "numeroFattura": "2026/077",
            "dataFattura": "11/03/2026",
            "fornitore": {
                "denominazione": "ARTI GRAFICHE E COMUNICAZIONE CREMONESE S.N.C.",
                "partitaIva": piva_12
            },
            "cliente": {
                "denominazione": "CASEIFICIO TIPICO DEL PO S.R.L.",
                "codiceFiscale": None
            },
            "importoTotale": 1586.00,
            "iban": iban_12
        }
    })

    # HO_FAT_13: Noleggio cantiere senza CF cliente
    piva_13 = make_valid_piva("0719284013")
    iban_13 = make_valid_iban("H0306909606100000062839")
    docs.append({
        "id": "HO_FAT_13",
        "type": "FATTURA",
        "template": "FAT_NOLEGGIO_MACCHINARI",
        "payload": (
            "LARIO NOLEGGI & MACCHINE MOVIMENTO TERRA S.R.L.\n"
            "Via Como 90, 22036 Erba (CO)\n"
            f"Partita IVA: {piva_13}\n\n"
            "Fattura n. NOL-2026/102 del 09/03/2026\n"
            "Cliente: CANTIERE NAVALE DEL LARIO S.R.L.\n"
            "Canone mensile noleggio escavatore cingolato 35 quintali.\n"
            "Imponibile: Euro 3.500,00\n"
            "IVA al 22%: Euro 770,00\n"
            "Importo Totale Fattura: Euro 4.270,00\n\n"
            "Bonifico bancario:\n"
            f"IBAN: {iban_13}"
        ),
        "ground_truth": {
            "numeroFattura": "NOL-2026/102",
            "dataFattura": "09/03/2026",
            "fornitore": {
                "denominazione": "LARIO NOLEGGI & MACCHINE MOVIMENTO TERRA S.R.L.",
                "partitaIva": piva_13
            },
            "cliente": {
                "denominazione": "CANTIERE NAVALE DEL LARIO S.R.L.",
                "codiceFiscale": None
            },
            "importoTotale": 4270.00,
            "iban": iban_13
        }
    })

    # HO_FAT_14: Intermediazione immobiliare senza CF cliente
    piva_14 = make_valid_piva("0829183010")
    iban_14 = make_valid_iban("E0200801400000105928371")
    docs.append({
        "id": "HO_FAT_14",
        "type": "FATTURA",
        "template": "FAT_PROVVIGIONE_AGENZIA",
        "payload": (
            "LIGURIA REAL ESTATE BROKERAGE S.R.L.\n"
            f"Via XX Settembre 30, 16121 Genova (GE) - P.IVA: {piva_14}\n\n"
            "FATTURA N. 2026/PROV-015 del 04/03/2026\n"
            "Mandante acquirente: INVESTIMENTI PORTUALI GENOVESI S.P.A.\n"
            "Via del Molo 18, Genova\n"
            "Provvigione per mediazione compravendita immobile commerciale sito in Genova Porto Antico.\n"
            "Corrispettivo: Euro 6.000,00\n"
            "IVA di legge 22%: Euro 1.320,00\n"
            "Saldo Complessivo: Euro 7.320,00\n\n"
            "Bonifico a favore di Liguria Real Estate Brokerage:\n"
            f"IBAN: {iban_14}"
        ),
        "ground_truth": {
            "numeroFattura": "2026/PROV-015",
            "dataFattura": "04/03/2026",
            "fornitore": {
                "denominazione": "LIGURIA REAL ESTATE BROKERAGE S.R.L.",
                "partitaIva": piva_14
            },
            "cliente": {
                "denominazione": "INVESTIMENTI PORTUALI GENOVESI S.P.A.",
                "codiceFiscale": None
            },
            "importoTotale": 7320.00,
            "iban": iban_14
        }
    })

    # --------------------------------------------------------------------------
    # 2. ANAGRAFICA (14 Documenti)
    # --------------------------------------------------------------------------

    # HO_ANA_01: Carta d'identità elettronica, maschio, Roma, senza sesso esplicito (sesso: null)
    cf_ana_01 = generate_coherent_cf("Galli", "Federico", 18, 4, 1983, "M", "H501")
    docs.append({
        "id": "HO_ANA_01",
        "type": "ANAGRAFICA",
        "template": "ANA_CIE_COMPLETA",
        "payload": (
            "REPUBBLICA ITALIANA - MINISTERO DELL'INTERNO\n"
            "CARTA DI IDENTITÀ ELETTRONICA N. CA72910AA\n\n"
            "Cognome: Galli\n"
            "Nome: Federico\n"
            "Luogo e data di nascita: Roma (RM), 18/04/1983\n"
            f"Codice Fiscale: {cf_ana_01}\n"
            "Rilasciata dal: Comune di Roma\n"
            "Data di emissione: 14/05/2022\n"
            "Data di scadenza: 18/04/2032"
        ),
        "ground_truth": {
            "tipoDocumento": "CARTA_IDENTITA",
            "nome": "Federico",
            "cognome": "Galli",
            "codiceFiscale": cf_ana_01,
            "dataNascita": "18/04/1983",
            "luogoNascita": "Roma (RM)",
            "sesso": None,
            "numeroDocumento": "CA72910AA",
            "dataRilascio": "14/05/2022",
            "dataScadenza": "18/04/2032",
            "enteRilascio": "Comune di Roma"
        }
    })

    # HO_ANA_02: Passaporto, femmina, Milano, completa
    cf_ana_02 = generate_coherent_cf("Pellegrini", "Chiara", 9, 10, 1990, "F", "F205")
    docs.append({
        "id": "HO_ANA_02",
        "type": "ANAGRAFICA",
        "template": "ANA_PASSAPORTO_COMPLETO",
        "payload": (
            "REPUBBLICA ITALIANA - PASSAPORTO ITALIANO N. YA8192031\n"
            "Cognome / Surname: Pellegrini\n"
            "Nome / Given names: Chiara\n"
            "Sesso / Sex: F\n"
            "Data di nascita / Date of birth: 09/10/1990\n"
            "Luogo di nascita / Place of birth: Milano (MI)\n"
            f"Codice Fiscale: {cf_ana_02}\n"
            "Autorità di rilascio / Authority: Questura di Milano\n"
            "Data di rilascio / Date of issue: 22/01/2021\n"
            "Data di scadenza / Date of expiry: 22/01/2031"
        ),
        "ground_truth": {
            "tipoDocumento": "PASSAPORTO",
            "nome": "Chiara",
            "cognome": "Pellegrini",
            "codiceFiscale": cf_ana_02,
            "dataNascita": "09/10/1990",
            "luogoNascita": "Milano (MI)",
            "sesso": "F",
            "numeroDocumento": "YA8192031",
            "dataRilascio": "22/01/2021",
            "dataScadenza": "22/01/2031",
            "enteRilascio": "Questura di Milano"
        }
    })

    # HO_ANA_03: Patente di guida card, maschio, Torino, senza sesso esplicito (sesso: null)
    cf_ana_03 = generate_coherent_cf("Lombardi", "Davide", 25, 7, 1976, "M", "L219")
    docs.append({
        "id": "HO_ANA_03",
        "type": "ANAGRAFICA",
        "template": "ANA_PATENTE_GUIDA",
        "payload": (
            "REPUBBLICA ITALIANA - PATENTE DI GUIDA\n"
            "1. Lombardi\n"
            "2. Davide\n"
            "3. 25/07/1976 Torino (TO)\n"
            "4a. 05/09/2019  4b. 25/07/2029  4c. MIT-UCO\n"
            "5. U1928401X\n"
            f"Codice Fiscale: {cf_ana_03}"
        ),
        "ground_truth": {
            "tipoDocumento": "PATENTE",
            "nome": "Davide",
            "cognome": "Lombardi",
            "codiceFiscale": cf_ana_03,
            "dataNascita": "25/07/1976",
            "luogoNascita": "Torino (TO)",
            "sesso": None,
            "numeroDocumento": "U1928401X",
            "dataRilascio": "05/09/2019",
            "dataScadenza": "25/07/2029",
            "enteRilascio": "MIT-UCO"
        }
    })

    # HO_ANA_04: Tessera Sanitaria (senza dataRilascio né enteRilascio)
    cf_ana_04 = generate_coherent_cf("De Luca", "Serena", 12, 1, 1994, "F", "F839")
    docs.append({
        "id": "HO_ANA_04",
        "type": "ANAGRAFICA",
        "template": "ANA_TESSERA_SANITARIA",
        "payload": (
            "MINISTERO DELL'ECONOMIA E DELLE FINANZE\n"
            "TESSERA SANITARIA - CARTA NAZIONALE DEI SERVIZI\n"
            "Cognome: De Luca\n"
            "Nome: Serena\n"
            f"Codice Fiscale: {cf_ana_04}\n"
            "Sesso: F\n"
            "Data di nascita: 12/01/1994\n"
            "Luogo di nascita: Napoli (NA)\n"
            "Numero di identificazione della tessera: 80380001200394829102\n"
            "Scadenza: 12/01/2028"
        ),
        "ground_truth": {
            "tipoDocumento": "TESSERA_SANITARIA",
            "nome": "Serena",
            "cognome": "De Luca",
            "codiceFiscale": cf_ana_04,
            "dataNascita": "12/01/1994",
            "luogoNascita": "Napoli (NA)",
            "sesso": "F",
            "numeroDocumento": "80380001200394829102",
            "dataRilascio": None,
            "dataScadenza": "12/01/2028",
            "enteRilascio": None
        }
    })

    # HO_ANA_05: Autocertificazione anagrafica (NON_SPECIFICATO, nessun estremo documento, senza sesso)
    cf_ana_05 = generate_coherent_cf("Caruso", "Vincenzo", 3, 3, 1968, "M", "G273")
    docs.append({
        "id": "HO_ANA_05",
        "type": "ANAGRAFICA",
        "template": "ANA_AUTOCERTIFICAZIONE_RESIDENZA",
        "payload": (
            "DICHIARAZIONE SOSTITUTIVA DI CERTIFICAZIONE ANAGRAFICA\n"
            "(Art. 46 D.P.R. 28 dicembre 2000, n. 445)\n\n"
            "Il sottoscritto Caruso Vincenzo, nato a Palermo (PA) il 03/03/1968,\n"
            f"Codice Fiscale {cf_ana_05}, residente in Via Roma 40, Palermo,\n"
            "dichiara sotto la propria personale responsabilità di essere residente al recapito sopra indicato.\n\n"
            "Palermo, 08/03/2026\n"
            "Firma del dichiarante: Vincenzo Caruso"
        ),
        "ground_truth": {
            "tipoDocumento": "NON_SPECIFICATO",
            "nome": "Vincenzo",
            "cognome": "Caruso",
            "codiceFiscale": cf_ana_05,
            "dataNascita": "03/03/1968",
            "luogoNascita": "Palermo (PA)",
            "sesso": None,
            "numeroDocumento": None,
            "dataRilascio": None,
            "dataScadenza": None,
            "enteRilascio": None
        }
    })

    # HO_ANA_06: Verbale identificazione teste (CARTA_IDENTITA, senza sesso)
    cf_ana_06 = generate_coherent_cf("Venturi", "Andrea", 30, 11, 1985, "M", "A944")
    docs.append({
        "id": "HO_ANA_06",
        "type": "ANAGRAFICA",
        "template": "ANA_VERBALE_TESTIMONE",
        "payload": (
            "COMANDO POLIZIA LOCALE - VERBALE DI SOMMARIE INFORMAZIONI\n"
            "Addì 14/02/2026, è presente il teste:\n"
            "Cognome: Venturi\n"
            "Nome: Andrea\n"
            "Nato a Bologna (BO) il 30/11/1985\n"
            f"Codice Fiscale: {cf_ana_06}\n"
            "Identificato a mezzo Carta d'Identità cartacea n. AT9182371 rilasciata dal Comune di Bologna\n"
            "in data 10/01/2018 con scadenza il 30/11/2028."
        ),
        "ground_truth": {
            "tipoDocumento": "CARTA_IDENTITA",
            "nome": "Andrea",
            "cognome": "Venturi",
            "codiceFiscale": cf_ana_06,
            "dataNascita": "30/11/1985",
            "luogoNascita": "Bologna (BO)",
            "sesso": None,
            "numeroDocumento": "AT9182371",
            "dataRilascio": "10/01/2018",
            "dataScadenza": "30/11/2028",
            "enteRilascio": "Comune di Bologna"
        }
    })

    # HO_ANA_07: Modulo fondo pensione (CARTA_IDENTITA, femmina, Firenze, senza sesso)
    cf_ana_07 = generate_coherent_cf("Bernardi", "Silvia", 17, 9, 1988, "F", "D612")
    docs.append({
        "id": "HO_ANA_07",
        "type": "ANAGRAFICA",
        "template": "ANA_FONDO_PENSIONE",
        "payload": (
            "MODULO DI ADESIONE FONDO PENSIONE REGIONALE\n"
            "DATI DELL'ADERENTE:\n"
            "Nome: Silvia\n"
            "Cognome: Bernardi\n"
            "Data di Nascita: 17/09/1988\n"
            "Luogo di Nascita: Firenze (FI)\n"
            f"Codice Fiscale: {cf_ana_07}\n"
            "Documento allegato: Carta d'Identità n. CD3829104 emessa dal Comune di Firenze\n"
            "Rilascio: 01/10/2020 - Scadenza: 17/09/2030"
        ),
        "ground_truth": {
            "tipoDocumento": "CARTA_IDENTITA",
            "nome": "Silvia",
            "cognome": "Bernardi",
            "codiceFiscale": cf_ana_07,
            "dataNascita": "17/09/1988",
            "luogoNascita": "Firenze (FI)",
            "sesso": None,
            "numeroDocumento": "CD3829104",
            "dataRilascio": "01/10/2020",
            "dataScadenza": "17/09/2030",
            "enteRilascio": "Comune di Firenze"
        }
    })

    # HO_ANA_08: Apertura conto titoli (PASSAPORTO, maschio, Bari, senza dataRilascio né sesso)
    cf_ana_08 = generate_coherent_cf("Santoro", "Michele", 21, 5, 1974, "M", "A662")
    docs.append({
        "id": "HO_ANA_08",
        "type": "ANAGRAFICA",
        "template": "ANA_CONTO_TITOLI",
        "payload": (
            "BANCA DI CREDITO PUGLIESE - PROFILO ANAGRAFICO CLIENTE\n"
            "Nominativo: Santoro Michele\n"
            "Data di nascita: 21/05/1974\n"
            "Comune di nascita: Bari (BA)\n"
            f"Codice Fiscale: {cf_ana_08}\n"
            "Documento esibito: Passaporto numero 14AB92810 rilasciato dalla Questura di Bari\n"
            "Valido fino al: 04/06/2028"
        ),
        "ground_truth": {
            "tipoDocumento": "PASSAPORTO",
            "nome": "Michele",
            "cognome": "Santoro",
            "codiceFiscale": cf_ana_08,
            "dataNascita": "21/05/1974",
            "luogoNascita": "Bari (BA)",
            "sesso": None,
            "numeroDocumento": "14AB92810",
            "dataRilascio": None,
            "dataScadenza": "04/06/2028",
            "enteRilascio": "Questura di Bari"
        }
    })

    # HO_ANA_09: Scheda neoassunto (PATENTE, femmina, Genova, senza sesso)
    cf_ana_09 = generate_coherent_cf("Parodi", "Francesca", 8, 2, 1993, "F", "D969")
    docs.append({
        "id": "HO_ANA_09",
        "type": "ANAGRAFICA",
        "template": "ANA_ASSUNZIONE_DIPENDENTE",
        "payload": (
            "RISORSE UMANE - REGISTRO ANAGRAFICA DIPENDENTI\n"
            "Dipendente: Parodi Francesca\n"
            "Nata a Genova (GE) in data 08/02/1993\n"
            f"Codice Fiscale: {cf_ana_09}\n"
            "Estremi patente di guida per guida autovettura aziendale:\n"
            "Patente n. U1829402T rilasciata da MIT-UCO il 15/03/2021, scadenza 08/02/2031."
        ),
        "ground_truth": {
            "tipoDocumento": "PATENTE",
            "nome": "Francesca",
            "cognome": "Parodi",
            "codiceFiscale": cf_ana_09,
            "dataNascita": "08/02/1993",
            "luogoNascita": "Genova (GE)",
            "sesso": None,
            "numeroDocumento": "U1829402T",
            "dataRilascio": "15/03/2021",
            "dataScadenza": "08/02/2031",
            "enteRilascio": "MIT-UCO"
        }
    })

    # HO_ANA_10: Dichiarazione notorietà (NON_SPECIFICATO, senza CF né documenti)
    docs.append({
        "id": "HO_ANA_10",
        "type": "ANAGRAFICA",
        "template": "ANA_DICHIARAZIONE_NOTORIETA",
        "payload": (
            "DICHIARAZIONE SOSTITUTIVA DELL'ATTO DI NOTORIETÀ\n"
            "Io sottoscritto Sartori Alberto, nato a Verona (VR) il 19/07/1971,\n"
            "sesso M, residente in Corso Cavour 22, Verona,\n"
            "dichiaro la conformità all'originale delle copie documentali prodotte.\n"
            "Verona, 26/02/2026."
        ),
        "ground_truth": {
            "tipoDocumento": "NON_SPECIFICATO",
            "nome": "Alberto",
            "cognome": "Sartori",
            "codiceFiscale": None,
            "dataNascita": "19/07/1971",
            "luogoNascita": "Verona (VR)",
            "sesso": "M",
            "numeroDocumento": None,
            "dataRilascio": None,
            "dataScadenza": None,
            "enteRilascio": None
        }
    })

    # HO_ANA_11: Consegna badge aziendale (TESSERA_SANITARIA, senza sesso né date rilascio/scadenza)
    cf_ana_11 = generate_coherent_cf("Trevisan", "Monica", 14, 12, 1986, "F", "L736")
    docs.append({
        "id": "HO_ANA_11",
        "type": "ANAGRAFICA",
        "template": "ANA_CONSEGNA_BADGE",
        "payload": (
            "MODULO ASSEGNAZIONE CHIAVI E TESSERA DI ACCESSO SEDE\n"
            "Si certifica l'identità del collaboratore:\n"
            "Cognome: Trevisan\n"
            "Nome: Monica\n"
            "Nata a Venezia (VE) il 14/12/1986\n"
            f"Codice Fiscale: {cf_ana_11}\n"
            "Identificata a vista mediante Tessera Sanitaria n. 80380001492817293841."
        ),
        "ground_truth": {
            "tipoDocumento": "TESSERA_SANITARIA",
            "nome": "Monica",
            "cognome": "Trevisan",
            "codiceFiscale": cf_ana_11,
            "dataNascita": "14/12/1986",
            "luogoNascita": "Venezia (VE)",
            "sesso": None,
            "numeroDocumento": "80380001492817293841",
            "dataRilascio": None,
            "dataScadenza": None,
            "enteRilascio": None
        }
    })

    # HO_ANA_12: Iscrizione albo (CARTA_IDENTITA, maschio, Perugia, senza sesso)
    cf_ana_12 = generate_coherent_cf("Rastelli", "Simone", 28, 8, 1980, "M", "G478")
    docs.append({
        "id": "HO_ANA_12",
        "type": "ANAGRAFICA",
        "template": "ANA_ISCRIZIONE_ALBO",
        "payload": (
            "COLLEGIO DEI GEOMETRI E DEI GEOMETRI LAUREATI\n"
            "DOMANDA DI ISCRIZIONE ALL'ALBO PROFESSIONALE\n"
            "Candidato: Rastelli Simone\n"
            "Data di nascita: 28/08/1980\n"
            "Luogo: Perugia (PG)\n"
            f"Codice Fiscale: {cf_ana_12}\n"
            "Documento di riconoscimento: Carta d'Identità n. AS8291042 rilasciata dal Comune di Perugia\n"
            "Data rilascio: 19/09/2021 - Data scadenza: 28/08/2031"
        ),
        "ground_truth": {
            "tipoDocumento": "CARTA_IDENTITA",
            "nome": "Simone",
            "cognome": "Rastelli",
            "codiceFiscale": cf_ana_12,
            "dataNascita": "28/08/1980",
            "luogoNascita": "Perugia (PG)",
            "sesso": None,
            "numeroDocumento": "AS8291042",
            "dataRilascio": "19/09/2021",
            "dataScadenza": "28/08/2031",
            "enteRilascio": "Comune di Perugia"
        }
    })

    # HO_ANA_13: Visita idoneità sportiva (TESSERA_SANITARIA, maschio, Catania, senza date doc)
    cf_ana_13 = generate_coherent_cf("Rapisarda", "Domenico", 11, 4, 1995, "M", "C351")
    docs.append({
        "id": "HO_ANA_13",
        "type": "ANAGRAFICA",
        "template": "ANA_VISITA_SPORTIVA",
        "payload": (
            "CENTRO DI MEDICINA DELLO SPORT ETNEO\n"
            "SCHEDA ATLETA VISITA DI IDONEITÀ AGONISTICA\n"
            "Cognome: Rapisarda\n"
            "Nome: Domenico\n"
            "Sesso: M\n"
            "Data di nascita: 11/04/1995\n"
            "Comune di nascita: Catania (CT)\n"
            f"C.F.: {cf_ana_13}\n"
            "Tessera Sanitaria esibita n. 80380001593820194821"
        ),
        "ground_truth": {
            "tipoDocumento": "TESSERA_SANITARIA",
            "nome": "Domenico",
            "cognome": "Rapisarda",
            "codiceFiscale": cf_ana_13,
            "dataNascita": "11/04/1995",
            "luogoNascita": "Catania (CT)",
            "sesso": "M",
            "numeroDocumento": "80380001593820194821",
            "dataRilascio": None,
            "dataScadenza": None,
            "enteRilascio": None
        }
    })

    # HO_ANA_14: Consenso informato dati sanitari (NON_SPECIFICATO, femmina, Trieste, senza estremi doc né sesso)
    cf_ana_14 = generate_coherent_cf("Kovacs", "Elena", 6, 6, 1987, "F", "L424")
    docs.append({
        "id": "HO_ANA_14",
        "type": "ANAGRAFICA",
        "template": "ANA_CONSENSO_PRIVACY",
        "payload": (
            "MODULO DI CONSENSO INFORMATO AL TRATTAMENTO DEI DATI PERSONALI\n"
            "Paziente: Kovacs Elena\n"
            "Nata a Trieste (TS) il 06/06/1987\n"
            f"Codice Fiscale: {cf_ana_14}\n"
            "La sottoscritta acconsente al trattamento dei dati per finalità diagnostiche.\n"
            "Trieste, 03/03/2026."
        ),
        "ground_truth": {
            "tipoDocumento": "NON_SPECIFICATO",
            "nome": "Elena",
            "cognome": "Kovacs",
            "codiceFiscale": cf_ana_14,
            "dataNascita": "06/06/1987",
            "luogoNascita": "Trieste (TS)",
            "sesso": None,
            "numeroDocumento": None,
            "dataRilascio": None,
            "dataScadenza": None,
            "enteRilascio": None
        }
    })

    # --------------------------------------------------------------------------
    # 3. EMAIL (14 Documenti)
    # --------------------------------------------------------------------------

    # HO_EML_01: Preventivo catering convention completa
    docs.append({
        "id": "HO_EML_01",
        "type": "EMAIL",
        "template": "EML_CATERING_CONVENTION",
        "payload": (
            "Da: Stefano Brambilla <s.brambilla@eventi-aziendali-roma.it>\n"
            "Data: 10/03/2026 09:15\n"
            "A: preventivi@banqueting-excellence.it\n"
            "Oggetto: Richiesta preventivo catering convention aziendale annuale\n\n"
            "Gentile Team Banqueting,\n"
            "per conto di Eventi Aziendali Roma S.r.l., desidero richiedere una proposta di catering "
            "per la convention annuale prevista a Roma il giorno 28/05/2026.\n"
            "La platea attesa è di 150 partecipanti e prevediamo un coffee break mattutino e un buffet lunch.\n"
            "Il nostro budget allocato per il servizio è di Euro 12.000,00.\n"
            "Resto a disposizione per concordare un sopralluogo.\n\n"
            "Cordiali saluti,\n"
            "Stefano Brambilla\n"
            "Eventi Aziendali Roma S.r.l.\n"
            "Telefono: +39 06 48920145"
        ),
        "ground_truth": {
            "mittente": "Stefano Brambilla",
            "email": "s.brambilla@eventi-aziendali-roma.it",
            "telefono": "+39 06 48920145",
            "azienda": "Eventi Aziendali Roma S.r.l.",
            "oggetto": "Richiesta preventivo catering convention aziendale annuale",
            "citta": "Roma",
            "numeroPartecipanti": 150,
            "budgetEuro": 12000.0,
            "dataEvento": "28/05/2026"
        }
    })

    # HO_EML_02: Supporto tecnico portale B2B (senza partecipanti, budget, dataEvento, citta)
    docs.append({
        "id": "HO_EML_02",
        "type": "EMAIL",
        "template": "EML_TICKET_SUPPORTO",
        "payload": (
            "Da: Ing. Marco Ferretti <m.ferretti@ferretti-logistica.it>\n"
            "Data: 04/03/2026 14:22\n"
            "A: helpdesk@cloud-servizi-b2b.com\n"
            "Oggetto: Ticket 8941 - Blocco accesso con credenziali SSO al portale fatturazione\n\n"
            "Buongiorno,\n"
            "segnalo che da questa mattina i nostri operatori di filiale non riescono ad accedere al portale.\n"
            "Il sistema restituisce errore di autenticazione 502 Bad Gateway.\n"
            "Vi chiediamo un intervento tempestivo data l'urgenza di spedizione dei lotti.\n\n"
            "Distinti saluti,\n"
            "Ing. Marco Ferretti\n"
            "Ferretti Logistica Integrata S.p.A.\n"
            "Tel. +39 051 6829104"
        ),
        "ground_truth": {
            "mittente": "Marco Ferretti",
            "email": "m.ferretti@ferretti-logistica.it",
            "telefono": "+39 051 6829104",
            "azienda": "Ferretti Logistica Integrata S.p.A.",
            "oggetto": "Ticket 8941 - Blocco accesso con credenziali SSO al portale fatturazione",
            "citta": None,
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    # HO_EML_03: Bando di gara vigilanza (senza partecipanti, senza budget, senza dataEvento)
    docs.append({
        "id": "HO_EML_03",
        "type": "EMAIL",
        "template": "EML_QUESITO_GARA",
        "payload": (
            "Da: Dott.ssa Cristina Valli <c.valli@vigilanza-lombarda.it>\n"
            "Data: 22/02/2026 11:40\n"
            "A: rsu.appalti@comune.milano.it\n"
            "Oggetto: Chiarimento gara europea vigilanza armata sedi municipali Milano\n\n"
            "Spettabile Stazione Appaltante del Comune di Milano,\n"
            "in riferimento alla procedura di gara per i servizi di vigilanza armata, chiediamo conferma se la fideiussione "
            "provvisoria possa essere emessa con firma digitale qualificata e autentica notarile telematica.\n\n"
            "In attesa di riscontro,\n"
            "Dott.ssa Cristina Valli\n"
            "Vigilanza Lombarda Sicurezza S.r.l. - Milano\n"
            "Recapito: +39 02 87192044"
        ),
        "ground_truth": {
            "mittente": "Cristina Valli",
            "email": "c.valli@vigilanza-lombarda.it",
            "telefono": "+39 02 87192044",
            "azienda": "Vigilanza Lombarda Sicurezza S.r.l.",
            "oggetto": "Chiarimento gara europea vigilanza armata sedi municipali Milano",
            "citta": "Milano",
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    # HO_EML_04: Conferma sala meeting e proiettore completa
    docs.append({
        "id": "HO_EML_04",
        "type": "EMAIL",
        "template": "EML_CONFERMA_SALA",
        "payload": (
            "Da: Dott. Alberto Sala <alberto.sala@centro-congressi-to.it>\n"
            "Data: 15/02/2026 16:30\n"
            "A: amministrazione@finanza-piemonte.org\n"
            "Oggetto: Conferma prenotazione Sala Cavour per seminario finanza etica\n\n"
            "Gentile Dottore,\n"
            "confermiamo la prenotazione della Sala Cavour presso la nostra sede di Torino per il giorno 18/04/2026.\n"
            "L'allestimento a platea sarà predisposto per 45 partecipanti con impianto audio e videoproiettore 4K.\n"
            "Il corrispettivo pattuito per l'intera giornata ammonta a Euro 800,00.\n\n"
            "A presto,\n"
            "Dott. Alberto Sala\n"
            "Centro Congressi Metropolitano Torino S.r.l.\n"
            "Tel: +39 011 5928104"
        ),
        "ground_truth": {
            "mittente": "Alberto Sala",
            "email": "alberto.sala@centro-congressi-to.it",
            "telefono": "+39 011 5928104",
            "azienda": "Centro Congressi Metropolitano Torino S.r.l.",
            "oggetto": "Conferma prenotazione Sala Cavour per seminario finanza etica",
            "citta": "Torino",
            "numeroPartecipanti": 45,
            "budgetEuro": 800.0,
            "dataEvento": "18/04/2026"
        }
    })

    # HO_EML_05: Disservizio connettività (senza azienda, senza telefono, senza budget, senza dataEvento)
    docs.append({
        "id": "HO_EML_05",
        "type": "EMAIL",
        "template": "EML_SEGNALAZIONE_GUASTO",
        "payload": (
            "Da: Roberto Maran <roberto.maran@telecom-fibra-net.org>\n"
            "Data: 02/03/2026 08:50\n"
            "A: noc-operazioni@retelombardia.it\n"
            "Oggetto: Segnalazione trancio cavo ottico dorsale Brescia - Bergamo\n\n"
            "Gentili colleghi del NOC,\n"
            "vi informo che una scavatrice di terzi ha tranciato il fascio principale in ingresso a Brescia.\n"
            "Squadre di giuntisti già inviate sul posto per ripristino entro le ore 13:00.\n\n"
            "Saluti,\n"
            "Roberto Maran"
        ),
        "ground_truth": {
            "mittente": "Roberto Maran",
            "email": "roberto.maran@telecom-fibra-net.org",
            "telefono": None,
            "azienda": None,
            "oggetto": "Segnalazione trancio cavo ottico dorsale Brescia - Bergamo",
            "citta": "Brescia",
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    # HO_EML_06: Corso formazione sicurezza sul lavoro completa
    docs.append({
        "id": "HO_EML_06",
        "type": "EMAIL",
        "template": "EML_CORSO_FORMAZIONE",
        "payload": (
            "Da: Dott.ssa Giorgia Bellini <formazione@sicur-academy-bo.it>\n"
            "Data: 19/02/2026 10:05\n"
            "A: hr-gestione@officine-meccaniche-re.it\n"
            "Oggetto: Iscrizione corso antincendio e primo soccorso D.Lgs 81/08\n\n"
            "Gentile Responsabile Risorse Umane,\n"
            "confermiamo l'avvio della sessione formativa a Bologna per il 06/05/2026.\n"
            "Abbiamo riservato i posti per 8 partecipanti della Vostra azienda.\n"
            "La quota complessiva di iscrizione è pari a Euro 1.600,00 comprensiva di attestati e manualistica.\n\n"
            "Un cordiale saluto,\n"
            "Dott.ssa Giorgia Bellini\n"
            "Sicur Academy Bologna S.r.l.\n"
            "Telefono diretto: +39 051 4482910"
        ),
        "ground_truth": {
            "mittente": "Giorgia Bellini",
            "email": "formazione@sicur-academy-bo.it",
            "telefono": "+39 051 4482910",
            "azienda": "Sicur Academy Bologna S.r.l.",
            "oggetto": "Iscrizione corso antincendio e primo soccorso D.Lgs 81/08",
            "citta": "Bologna",
            "numeroPartecipanti": 8,
            "budgetEuro": 1600.0,
            "dataEvento": "06/05/2026"
        }
    })

    # HO_EML_07: Proposta workshop innovazione (senza telefono, senza budget, senza partecipanti)
    docs.append({
        "id": "HO_EML_07",
        "type": "EMAIL",
        "template": "EML_PROPOSTA_WORKSHOP",
        "payload": (
            "Da: Dott. Paolo Vianello <p.vianello@digital-hub-firenze.it>\n"
            "Data: 12/03/2026 15:45\n"
            "A: relazioni.esterne@fondazione-ricerca-toscana.org\n"
            "Oggetto: Proposta collaborazione per workshop su Intelligenza Artificiale Generativa\n\n"
            "Gentile Direttore,\n"
            "vi proponiamo di co-organizzare una giornata di studio a Firenze in data 24/09/2026.\n"
            "L'aula magna del polo tecnologico è già stata opzionata per la conferenza.\n"
            "Saremmo felici di valutare insieme le modalità di patrocinio.\n\n"
            "Cordiali saluti,\n"
            "Dott. Paolo Vianello\n"
            "Digital Hub Firenze Consorzio"
        ),
        "ground_truth": {
            "mittente": "Paolo Vianello",
            "email": "p.vianello@digital-hub-firenze.it",
            "telefono": None,
            "azienda": "Digital Hub Firenze Consorzio",
            "oggetto": "Proposta collaborazione per workshop su Intelligenza Artificiale Generativa",
            "citta": "Firenze",
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": "24/09/2026"
        }
    })

    # HO_EML_08: Richiesta nota di credito commerciale (senza budget, senza partecipanti, senza dataEvento, senza citta)
    docs.append({
        "id": "HO_EML_08",
        "type": "EMAIL",
        "template": "EML_RECLAMO_COMMERCIALE",
        "payload": (
            "Da: Federica Neri <acquisti@distribuzione-alimentare-nord.it>\n"
            "Data: 27/02/2026 12:10\n"
            "A: contabilita@pastificio-emiliano-dop.it\n"
            "Oggetto: Richiesta emissione nota di credito per bancale danneggiato rif DDT 240/26\n\n"
            "Buongiorno,\n"
            "alla ricezione del carico odierno abbiamo riscontrato lo schiacciamento di un pallet di farina tipo 00.\n"
            "Come anticipato al trasportatore, respingiamo 20 colli e vi chiediamo di emettere relativa nota di accredito.\n\n"
            "Grazie e saluti,\n"
            "Federica Neri\n"
            "Distribuzione Alimentare Nord S.r.l.\n"
            "Tel. +39 045 8920148"
        ),
        "ground_truth": {
            "mittente": "Federica Neri",
            "email": "acquisti@distribuzione-alimentare-nord.it",
            "telefono": "+39 045 8920148",
            "azienda": "Distribuzione Alimentare Nord S.r.l.",
            "oggetto": "Richiesta emissione nota di credito per bancale danneggiato rif DDT 240/26",
            "citta": None,
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    # HO_EML_09: Sollecito fornitura (senza azienda, senza telefono, senza budget, senza partecipanti, senza dataEvento)
    docs.append({
        "id": "HO_EML_09",
        "type": "EMAIL",
        "template": "EML_SOLLECITO_CONSEGNA",
        "payload": (
            "Da: Gianluca Riva <gianluca.riva@studio-associato-riva.it>\n"
            "Data: 06/03/2026 17:00\n"
            "A: ordini@forniture-ufficio-online.com\n"
            "Oggetto: Sollecito consegna ordine cancelleria ORD-2026-9921\n\n"
            "Gentile Assistenza Clienti,\n"
            "abbiamo inoltrato l'ordine 10 giorni fa con consegna garantita in 48 ore a Padova.\n"
            "Il materiale cartaceo e i toner non sono ancora stati recapitati.\n"
            "Vi chiediamo aggiornamento immediato sul tracking del corriere.\n\n"
            "Gianluca Riva"
        ),
        "ground_truth": {
            "mittente": "Gianluca Riva",
            "email": "gianluca.riva@studio-associato-riva.it",
            "telefono": None,
            "azienda": None,
            "oggetto": "Sollecito consegna ordine cancelleria ORD-2026-9921",
            "citta": "Padova",
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    # HO_EML_10: Tavola rotonda transizione ecologica (senza budget)
    docs.append({
        "id": "HO_EML_10",
        "type": "EMAIL",
        "template": "EML_INVITO_CONVEGNO",
        "payload": (
            "Da: Dott.ssa Beatrice Pozzi <segreteria@confindustria-liguria.it>\n"
            "Data: 01/03/2026 11:20\n"
            "A: associati.territorio@imprese-liguri.it\n"
            "Oggetto: Invito alla tavola rotonda su decarbonizzazione dei trasporti e PNRR\n\n"
            "Gentili Imprenditori Associati,\n"
            "siamo lieti di invitarVi al dibattito che si terrà a Genova il prossimo 11/06/2026.\n"
            "La sala convegni del Porto Antico accoglierà fino a 70 partecipanti.\n"
            "La partecipazione è gratuita previa iscrizione.\n\n"
            "Cordiali saluti,\n"
            "Dott.ssa Beatrice Pozzi\n"
            "Confindustria Liguria Servizi S.c.a.r.l.\n"
            "Telefono: +39 010 2498105"
        ),
        "ground_truth": {
            "mittente": "Beatrice Pozzi",
            "email": "segreteria@confindustria-liguria.it",
            "telefono": "+39 010 2498105",
            "azienda": "Confindustria Liguria Servizi S.c.a.r.l.",
            "oggetto": "Invito alla tavola rotonda su decarbonizzazione dei trasporti e PNRR",
            "citta": "Genova",
            "numeroPartecipanti": 70,
            "budgetEuro": None,
            "dataEvento": "11/06/2026"
        }
    })

    # HO_EML_11: Noleggio auto aziendali (senza dataEvento, senza partecipanti, con budget)
    docs.append({
        "id": "HO_EML_11",
        "type": "EMAIL",
        "template": "EML_PREVENTIVO_FLOTTA",
        "payload": (
            "Da: Dott. Fabio Manzoni <f.manzoni@manzoni-commerciale.it>\n"
            "Data: 17/02/2026 15:10\n"
            "A: corporate-sales@lease-plan-italia.it\n"
            "Oggetto: Richiesta quotazione noleggio a lungo termine flotta 5 vetture full-electric\n\n"
            "Spettabile Lease Plan,\n"
            "per la nostra sede di Milano valutiamo la sostituzione delle auto dei nostri commerciali.\n"
            "Abbiamo stimato un budget annuo di Euro 30.000,00 comprensivo di kasko e manutenzione ordinaria.\n"
            "Attendiamo un Vostro prospetto comparativo.\n\n"
            "Cordiali saluti,\n"
            "Dott. Fabio Manzoni\n"
            "Manzoni Soluzioni Commerciali S.p.A.\n"
            "Tel. +39 02 67194012"
        ),
        "ground_truth": {
            "mittente": "Fabio Manzoni",
            "email": "f.manzoni@manzoni-commerciale.it",
            "telefono": "+39 02 67194012",
            "azienda": "Manzoni Soluzioni Commerciali S.p.A.",
            "oggetto": "Richiesta quotazione noleggio a lungo termine flotta 5 vetture full-electric",
            "citta": "Milano",
            "numeroPartecipanti": None,
            "budgetEuro": 30000.0,
            "dataEvento": None
        }
    })

    # HO_EML_12: Variazione orari estivi (senza budget, partecipanti, dataEvento, telefono)
    docs.append({
        "id": "HO_EML_12",
        "type": "EMAIL",
        "template": "EML_CIRCOLARE_ORARI",
        "payload": (
            "Da: Dott.ssa Simona Ruggiero <amministrazione@studio-ruggiero-commercialisti.it>\n"
            "Data: 11/03/2026 18:05\n"
            "A: clienti-tutti@studio-ruggiero-commercialisti.it\n"
            "Oggetto: Comunicazione orari estivi di apertura al pubblico sede di Bari\n\n"
            "Gentili Clienti,\n"
            "vi informiamo che a partire dal mese prossimo la sede di Bari effettuerà orario continuato 08:30 - 16:30.\n"
            "I consulenti fiscali riceveranno solo su appuntamento telematico.\n\n"
            "Studio Ruggiero Dottori Commercialisti Associati\n"
            "Dott.ssa Simona Ruggiero"
        ),
        "ground_truth": {
            "mittente": "Simona Ruggiero",
            "email": "amministrazione@studio-ruggiero-commercialisti.it",
            "telefono": None,
            "azienda": "Studio Ruggiero Dottori Commercialisti Associati",
            "oggetto": "Comunicazione orari estivi di apertura al pubblico sede di Bari",
            "citta": "Bari",
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    # HO_EML_13: Audit sicurezza informatica (senza partecipanti, completa di budget e data)
    docs.append({
        "id": "HO_EML_13",
        "type": "EMAIL",
        "template": "EML_CYBERSECURITY_AUDIT",
        "payload": (
            "Da: Dott. Lorenzo Gatti <l.gatti@finanza-veneta-holding.it>\n"
            "Data: 09/03/2026 10:40\n"
            "A: security-assessment@cyber-defense-italy.com\n"
            "Oggetto: Incarico per vulnerability assessment e penetration test infrastruttura cloud\n\n"
            "Gentile Security Team,\n"
            "confermiamo l'affidamento delle verifiche tecniche sui nostri endpoint di Verona a partire dal 15/05/2026.\n"
            "Il corrispettivo concordato per le attività di ethical hacking è fissato in Euro 6.500,00.\n"
            "Restiamo in attesa dell'accordo di non divulgazione (NDA) controfirmato.\n\n"
            "Lorenzo Gatti\n"
            "Finanza Veneta Holding S.p.A.\n"
            "Telefono: +39 045 4920194"
        ),
        "ground_truth": {
            "mittente": "Lorenzo Gatti",
            "email": "l.gatti@finanza-veneta-holding.it",
            "telefono": "+39 045 4920194",
            "azienda": "Finanza Veneta Holding S.p.A.",
            "oggetto": "Incarico per vulnerability assessment e penetration test infrastruttura cloud",
            "citta": "Verona",
            "numeroPartecipanti": None,
            "budgetEuro": 6500.0,
            "dataEvento": "15/05/2026"
        }
    })

    # HO_EML_14: Candidatura spontanea ingegnere (senza azienda, budget, partecipanti, dataEvento)
    docs.append({
        "id": "HO_EML_14",
        "type": "EMAIL",
        "template": "EML_CANDIDATURA_LAVORO",
        "payload": (
            "Da: Ing. Matteo Salvi <matteo.salvi.dev@libero-mail.it>\n"
            "Data: 07/03/2026 12:55\n"
            "A: recruiting-tech@innovazione-software-torino.it\n"
            "Oggetto: Candidatura per posizione Senior Data Platform Engineer - Sede Torino\n\n"
            "Spettabile Team Risorse Umane,\n"
            "con la presente desidero sottoporre il mio profilo per le selezioni attive nel Vostro polo tecnologico di Torino.\n"
            "Ho maturato 7 anni di esperienza su architetture a microservizi e pipeline analitiche.\n"
            "Allego curriculum vitae aggiornato con dettaglio delle certificazioni conseguite.\n\n"
            "Cordiali saluti,\n"
            "Ing. Matteo Salvi\n"
            "Cellulare: +39 347 8920145"
        ),
        "ground_truth": {
            "mittente": "Matteo Salvi",
            "email": "matteo.salvi.dev@libero-mail.it",
            "telefono": "+39 347 8920145",
            "azienda": None,
            "oggetto": "Candidatura per posizione Senior Data Platform Engineer - Sede Torino",
            "citta": "Torino",
            "numeroPartecipanti": None,
            "budgetEuro": None,
            "dataEvento": None
        }
    })

    return docs


# ==============================================================================
# Costruzione del Set Avversariale Held-Out (6 Documenti: Violazioni Isolate e Radici Disgiunte)
# ==============================================================================

def build_heldout_adversarial():
    docs = []

    # HO_ADV_01: FATTURA - P.IVA Fornitore con cifra di controllo Luhn errata
    # Radice 10 cifre inedita: 0731945028. Cifra corretta Luhn: 6. Corrotta a 1.
    piva_adv_01 = make_invalid_piva("0731945028")
    iban_adv_01 = make_valid_iban("K0503411701000000038472")
    docs.append({
        "id": "HO_ADV_01",
        "type": "FATTURA",
        "adversarial_feature": "INVALID_SUPPLIER_PIVA_LUHN",
        "adversarial_field": "fornitore.partitaIva",
        "expected_valid": False,
        "payload": (
            "TERMOIDRAULICA F.LLI SARTORI S.N.C.\n"
            "Via Brennero 40, 38122 Trento (TN)\n"
            f"Partita IVA: {piva_adv_01}\n\n"
            "Fattura n. FAT-2026-049 del 02/03/2026\n"
            "Spettabile: HOTEL DOLOMITI RESORT S.R.L.\n"
            "Intervento: Sostituzione valvola miscelatrice impianto termico centrale.\n"
            "Imponibile: Euro 1.100,00\n"
            "IVA 22%: Euro 242,00\n"
            "Totale Documento: Euro 1.342,00\n\n"
            "Accredito bonifico:\n"
            f"IBAN: {iban_adv_01}"
        ),
        "ground_truth": {
            "numeroFattura": "FAT-2026-049",
            "dataFattura": "02/03/2026",
            "fornitore": {
                "denominazione": "TERMOIDRAULICA F.LLI SARTORI S.N.C.",
                "partitaIva": piva_adv_01
            },
            "cliente": {
                "denominazione": "HOTEL DOLOMITI RESORT S.R.L.",
                "codiceFiscale": None
            },
            "importoTotale": 1342.00,
            "iban": iban_adv_01
        }
    })

    # HO_ADV_02: FATTURA - P.IVA Fornitore con cifra di controllo Luhn errata
    # Radice 10 cifre inedita: 0852417039. Cifra corretta Luhn: 4. Corrotta a 9.
    piva_adv_02 = make_invalid_piva("0852417039")
    iban_adv_02 = make_valid_iban("Y0306909606100000047281")
    docs.append({
        "id": "HO_ADV_02",
        "type": "FATTURA",
        "adversarial_feature": "INVALID_SUPPLIER_PIVA_LUHN",
        "adversarial_field": "fornitore.partitaIva",
        "expected_valid": False,
        "payload": (
            "GRAFICHE MECCANICHE PADOVANE S.R.L.\n"
            "Corso Milano 120, 35138 Padova (PD)\n"
            f"Partita IVA Fornitore: {piva_adv_02}\n\n"
            "Fattura n. GM-2026/088 emessa il 25/02/2026\n"
            "Cliente: VETRERIA ARTISTICA VENETA S.P.A.\n"
            "Lavorazione: Incisione laser e packaging personalizzato.\n"
            "Subtotale: Euro 2.300,00\n"
            "IVA 22%: Euro 506,00\n"
            "Totale Fattura: Euro 2.806,00\n\n"
            "Pagamento mediante bonifico bancario:\n"
            f"IBAN: {iban_adv_02}"
        ),
        "ground_truth": {
            "numeroFattura": "GM-2026/088",
            "dataFattura": "25/02/2026",
            "fornitore": {
                "denominazione": "GRAFICHE MECCANICHE PADOVANE S.R.L.",
                "partitaIva": piva_adv_02
            },
            "cliente": {
                "denominazione": "VETRERIA ARTISTICA VENETA S.P.A.",
                "codiceFiscale": None
            },
            "importoTotale": 2806.00,
            "iban": iban_adv_02
        }
    })

    # HO_ADV_03: ANAGRAFICA - Codice Fiscale con carattere di controllo errato
    # Lorenzo Gentile, M, 15/04/1984 a Verona (L781). Base 15: GNTLNZ84D15L781. Check corretto: E. Corrotto a X.
    cf_adv_03 = "GNTLNZ84D15L781X"
    docs.append({
        "id": "HO_ADV_03",
        "type": "ANAGRAFICA",
        "adversarial_feature": "INVALID_CF_CONTROL_CHAR",
        "adversarial_field": "codiceFiscale",
        "expected_valid": False,
        "payload": (
            "REPUBBLICA ITALIANA - COMUNE DI VERONA\n"
            "ESTREMI CARTA DI IDENTITÀ N. AX8492019\n"
            "Cognome: Gentile\n"
            "Nome: Lorenzo\n"
            "Sesso: M\n"
            "Nato a Verona (VR) il 15/04/1984\n"
            f"Codice Fiscale: {cf_adv_03}\n"
            "Rilasciata il: 10/05/2020  Scadenza: 15/04/2030\n"
            "Ente: Comune di Verona"
        ),
        "ground_truth": {
            "tipoDocumento": "CARTA_IDENTITA",
            "nome": "Lorenzo",
            "cognome": "Gentile",
            "codiceFiscale": cf_adv_03,
            "dataNascita": "15/04/1984",
            "luogoNascita": "Verona (VR)",
            "sesso": "M",
            "numeroDocumento": "AX8492019",
            "dataRilascio": "10/05/2020",
            "dataScadenza": "15/04/2030",
            "enteRilascio": "Comune di Verona"
        }
    })

    # HO_ADV_04: ANAGRAFICA - Codice Fiscale con carattere di controllo errato
    # Martina Verdi, F, 12/09/1991 a Firenze (D612). Base 15: VRDMTN91P52D612. Check corretto: S. Corrotto a K.
    cf_adv_04 = "VRDMTN91P52D612K"
    docs.append({
        "id": "HO_ADV_04",
        "type": "ANAGRAFICA",
        "adversarial_feature": "INVALID_CF_CONTROL_CHAR",
        "adversarial_field": "codiceFiscale",
        "expected_valid": False,
        "payload": (
            "QUESTURA DI FIRENZE - UFFICIO PASSAPORTI\n"
            "DOCUMENTO DI VIAGGIO N. 08BB49281\n"
            "Cognome: Verdi\n"
            "Nome: Martina\n"
            "Sesso: F\n"
            "Data di nascita: 12/09/1991\n"
            "Luogo di nascita: Firenze (FI)\n"
            f"Codice Fiscale: {cf_adv_04}\n"
            "Data emissione: 18/06/2019  Data scadenza: 18/06/2029\n"
            "Autorità: Questura di Firenze"
        ),
        "ground_truth": {
            "tipoDocumento": "PASSAPORTO",
            "nome": "Martina",
            "cognome": "Verdi",
            "codiceFiscale": cf_adv_04,
            "dataNascita": "12/09/1991",
            "luogoNascita": "Firenze (FI)",
            "sesso": "F",
            "numeroDocumento": "08BB49281",
            "dataRilascio": "18/06/2019",
            "dataScadenza": "18/06/2029",
            "enteRilascio": "Questura di Firenze"
        }
    })

    # HO_ADV_05: FATTURA - IBAN con cifra di controllo MOD 97 errata
    # P.IVA fornitore valida (06283940167). IBAN corrotto: IT91B0306909606100000029584 (corretto sarebbe IT66)
    piva_adv_05 = make_valid_piva("0628394016")
    iban_adv_05 = make_invalid_iban("B0306909606100000029584")
    docs.append({
        "id": "HO_ADV_05",
        "type": "FATTURA",
        "adversarial_feature": "INVALID_IBAN_MOD97",
        "adversarial_field": "iban",
        "expected_valid": False,
        "payload": (
            "CONSORZIO AGRICOLO COLLINE SENESI SOC. COOP.\n"
            "Strada dei Fiori 14, 53100 Siena (SI)\n"
            f"P.IVA: {piva_adv_05}\n\n"
            "Fattura fornitura n. AGR-2026-074 del 16/02/2026\n"
            "Destinatario: CANTINE DEL TUSCAN SUN S.R.L.\n"
            "Fornitura concimi organici certificati bio.\n"
            "Imponibile: Euro 1.850,00\n"
            "IVA 10%: Euro 185,00\n"
            "Totale Documento: Euro 2.035,00\n\n"
            "Regolamento mediante bonifico ordinario:\n"
            f"IBAN: {iban_adv_05}"
        ),
        "ground_truth": {
            "numeroFattura": "AGR-2026-074",
            "dataFattura": "16/02/2026",
            "fornitore": {
                "denominazione": "CONSORZIO AGRICOLO COLLINE SENESI SOC. COOP.",
                "partitaIva": piva_adv_05
            },
            "cliente": {
                "denominazione": "CANTINE DEL TUSCAN SUN S.R.L.",
                "codiceFiscale": None
            },
            "importoTotale": 2035.00,
            "iban": iban_adv_05
        }
    })

    # HO_ADV_06: FATTURA - IBAN con cifra di controllo MOD 97 errata
    # P.IVA fornitore valida (09172830541). IBAN corrotto: IT14V0503411701000000073921 (corretto sarebbe IT89)
    piva_adv_06 = make_valid_piva("0917283054")
    iban_adv_06 = make_invalid_iban("V0503411701000000073921")
    docs.append({
        "id": "HO_ADV_06",
        "type": "FATTURA",
        "adversarial_feature": "INVALID_IBAN_MOD97",
        "adversarial_field": "iban",
        "expected_valid": False,
        "payload": (
            "ELETTROIMPIANTI RIVIERA S.R.L.\n"
            "Via Aurelia Ponente 45, 17047 Vado Ligure (SV)\n"
            f"P.IVA: {piva_adv_06}\n\n"
            "Fattura n. ELE-2026/031 Data: 05/03/2026\n"
            "Spettabile: MARINA TURISTICA DEL PONENTE S.P.A.\n"
            "Intervento: Adeguamento colonnine di ricarica banchina nord.\n"
            "Importo imponibile: Euro 3.100,00\n"
            "IVA 22%: Euro 682,00\n"
            "Totale Dovuto: Euro 3.782,00\n\n"
            "Coordinate bancarie per il bonifico:\n"
            f"IBAN: {iban_adv_06}"
        ),
        "ground_truth": {
            "numeroFattura": "ELE-2026/031",
            "dataFattura": "05/03/2026",
            "fornitore": {
                "denominazione": "ELETTROIMPIANTI RIVIERA S.R.L.",
                "partitaIva": piva_adv_06
            },
            "cliente": {
                "denominazione": "MARINA TURISTICA DEL PONENTE S.P.A.",
                "codiceFiscale": None
            },
            "importoTotale": 3782.00,
            "iban": iban_adv_06
        }
    })

    return docs


# ==============================================================================
# Costruzione del Set Router ALTRO (8 Documenti per test del classificatore/router)
# ==============================================================================

def build_heldout_router_altro():
    docs = [
        {
            "id": "HO_ALT_01",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Comunicato stampa istituzionale su sostenibilità urbana",
            "payload": (
                "COMUNE DI BOLOGNA - UFFICIO STAMPA E COMUNICAZIONE\n"
                "COMUNICATO STAMPA N. 142/2026\n"
                "Bologna, 24 Febbraio 2026\n\n"
                "PRESENTATO IL PIANO DI TRANSIZIONE ENERGETICA E FORESTAZIONE URBANA 2026-2030\n\n"
                "Questa mattina, presso la Sala Rossa di Palazzo d'Accursio, l'Assessore all'Ambiente e Urbanistica "
                "ha illustrato le linee guida del nuovo piano straordinario di mitigazione climatica. Il progetto prevede "
                "la piantumazione di 15.000 nuovi alberi autoctoni lungo le direttrici periferiche e la progressiva "
                "pedonalizzazione di quattro piazze storiche entro il prossimo triennio. L'intervento mira a ridurre "
                "le isole di calore estive e promuovere la mobilità attiva ciclabile. Gli incontri di partecipazione civica "
                "con i comitati di quartiere avranno inizio a partire dal mese di aprile presso le Case di Quartiere."
            )
        },
        {
            "id": "HO_ALT_02",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Verbale assembleare di condominio residenziale",
            "payload": (
                "CONDOMINIO RESIDENZA DEI PINI - VIA DEI COLLI 15, VERONA\n"
                "VERBALE DI ASSEMBLEA ORDINARIA IN SECONDA CONVOCAZIONE\n\n"
                "L'anno 2026, il giorno 10 del mese di marzo alle ore 20:30, si sono riuniti i condòmini per discutere "
                "e deliberare sul seguente Ordine del Giorno:\n"
                "1. Esame e approvazione del rendiconto consuntivo gestione ordinaria esercizio 2025.\n"
                "2. Nomina amministratore e definizione del compenso annuo.\n"
                "3. Interventi di manutenzione della copertura dell'edificio a seguito delle infiltrazioni.\n\n"
                "Assume la presidenza il Sig. Alberto Rinaldi, il quale nomina segretario il Sig. Marco Boni.\n"
                "Verificata la validità della costituzione (intervenuti n. 14 condòmini rappresentanti 625 millesimi), "
                "l'assemblea approva all'unanimità il rendiconto consuntivo con saldo attivo di Euro 420,15. "
                "Sul terzo punto, l'assemblea delega l'amministratore a richiedere tre preventivi tecnici per il rifacimento "
                "della guaina impermeabilizzante. La seduta è tolta alle ore 22:15."
            )
        },
        {
            "id": "HO_ALT_03",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Ricetta culinaria tradizionale lombarda",
            "payload": (
                "RICETTA TRADIZIONALE: RISOTTO ALLO ZAFFERANO CON FUNGHI PORCINI TRIFOLATI\n"
                "Difficoltà: Media | Tempo di preparazione: 20 min | Tempo di cottura: 18 min | Dosi: 4 persone\n\n"
                "INGREDIENTI:\n"
                "- 320g di riso Carnaroli autentico\n"
                "- 300g di funghi porcini freschi (o 30g secchi rinvenuti in acqua tiepida)\n"
                "- 1 bustina di zafferano purissimo in pistilli\n"
                "- 1 scalogno dorato tritato finemente\n"
                "- 1 bicchiere di vino bianco secco fermo\n"
                "- 1 litro di brodo vegetale leggero filtrato\n"
                "- 60g di burro di malga fresco\n"
                "- 50g di Parmigiano Reggiano DOP stagionato 24 mesi grattugiato\n"
                "- 1 spicchio d'aglio e prezzemolo fresco tritato\n\n"
                "PREPARAZIONE:\n"
                "Pulire accuratamente i porcini eliminando la terra con un panno umido, quindi tagliarli a fettine regolari. "
                "In una padella capiente far dorare lo spicchio d'aglio con un filo d'olio, unire i funghi e trifolarli a fiamma vivace per 5 minuti; "
                "spegnere e cospargere con il prezzemolo. In una casseruola a fondo spesso far appassire lo scalogno con metà del burro, "
                "tostare il riso finché i chicchi risultano traslucidi e sfumare con il vino bianco. Aggiungere gradualmente il brodo bollente. "
                "A cinque minuti dal termine della cottura incorporare lo zafferano disciolto in poco brodo caldo e metà dei funghi trifolati. "
                "Spegnere il fuoco e mantecare vigorosamente con il burro freddo rimasto e il Parmigiano. "
                "Coprire e lasciar riposare per 2 minuti prima di servire all'onda nei piatti piani, decorando con i restanti porcini trifolati."
            )
        },
        {
            "id": "HO_ALT_04",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Log di errore applicativo e stacktrace server",
            "payload": (
                "2026-03-08T03:14:22.842Z ERROR [http-nio-8080-exec-19] com.zaxxer.hikari.pool.HikariPool:\n"
                "HikariPool-1 - Connection is not available, request timed out after 30005ms.\n"
                "\tat com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:696)\n"
                "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:197)\n"
                "\tat com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:128)\n"
                "\tat org.springframework.jdbc.datasource.DataSourceUtils.fetchConnection(DataSourceUtils.java:160)\n"
                "\tat org.springframework.jdbc.datasource.DataSourceUtils.doGetConnection(DataSourceUtils.java:116)\n"
                "\tat org.springframework.jdbc.datasource.DataSourceTransactionManager.doBegin(DataSourceTransactionManager.java:305)\n"
                "Caused by: org.postgresql.util.PSQLException: The connection attempt failed due to socket timeout.\n"
                "\tat org.postgresql.core.v3.ConnectionFactoryImpl.openConnectionImpl(ConnectionFactoryImpl.java:344)\n"
                "\tat org.postgresql.core.ConnectionFactory.openConnection(ConnectionFactoryImpl.java:54)\n"
                "Connection pool size: total=50, active=50, idle=0, waiting=12."
            )
        },
        {
            "id": "HO_ALT_05",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Estratto termini licenza d'uso software open-source",
            "payload": (
                "CONDIZIONI GENERALI DI LICENZA SOFTWARE OPEN SOURCE\n"
                "Versione 2.1 - Marzo 2026\n\n"
                "Con la presente si concede a chiunque ottenga una copia di questo software e della documentazione associata "
                "il permesso gratuito, senza alcuna limitazione, di utilizzare, copiare, modificare, unire, pubblicare, distribuire, "
                "sublicenziare e/o vendere copie del Software, a condizione che l'avviso di copyright sopra indicato e la presente "
                "dichiarazione di autorizzazione siano inclusi in tutte le copie sostanziali dell'opera.\n\n"
                "IL SOFTWARE VIENE FORNITO 'COSÌ COM'È', SENZA GARANZIA DI ALCUN TIPO, ESPLICITA O IMPLICITA, COMPRESE, A TITOLO ESEMPLIFICATIVO, "
                "LE GARANZIE DI COMMERCIABILITÀ O IDONEITÀ PER UNO SCOPO PARTICOLARE. IN NESSUN CASO GLI AUTORI O I TITOLARI DEL COPYRIGHT "
                "SARANNO RESPONSABILI PER QUALSIASI RECLAMO, DANNO O ALTRA RESPONSABILITÀ DERIVANTE DALL'UTILIZZO DEL PRESENTE CODICE."
            )
        },
        {
            "id": "HO_ALT_06",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Bollettino di allertamento meteo e protezione civile",
            "payload": (
                "DIPARTIMENTO DELLA PROTEZIONE CIVILE - CENTRO FUNZIONALE REGIONALE\n"
                "AVVISO DI CRITICITÀ IDROGEOLOGICA ED IDRAULICA N. 078/2026\n"
                "Emissione: Lunedì 09 Marzo 2026 ore 13:00\n\n"
                "Si comunica per le successive 36 ore l'attivazione del livello di ALLERTA ARANCIONE per vento forte, mareggiate "
                "lungo l'arco costiero tirrenico e precipitazioni a carattere di rovescio temporalesco sui settori montani occidentali. "
                "I valori di raffica potranno raggiungere localmente gli 85 km/h con moto ondoso in rapido aumento fino a molto mosso. "
                "Si raccomanda agli enti locali di attivare i presidi territoriali, verificare il corretto deflusso dei canali di scolo "
                "e limitare l'accesso ai moli e ai sentieri escursionistici di crinale."
            )
        },
        {
            "id": "HO_ALT_07",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Referto clinico ed esami ematochimici di laboratorio",
            "payload": (
                "LABORATORIO ANALISI CLINICHE SAN MARCO\n"
                "REFERTO ESAMI EMATOCHIMICI E PROFILO LIPIDICO\n"
                "Data prelievo: 05/03/2026 ore 07:45 - Data validazione: 05/03/2026 ore 16:30\n\n"
                "Parametro                   Valore      U.M.        Intervallo di riferimento\n"
                "-----------------------------------------------------------------------------\n"
                "Glucosio basale             92          mg/dL       70 - 100\n"
                "Colesterolo Totale          184         mg/dL       < 200 desiderabile\n"
                "Colesterolo HDL             58          mg/dL       > 50 protettivo\n"
                "Colesterolo LDL calcolato   108         mg/dL       < 115 ottimale\n"
                "Trigliceridi                89          mg/dL       50 - 150\n"
                "Creatinina sierica          0.91        mg/dL       0.70 - 1.20\n"
                "e-GFR (CKD-EPI)             94          mL/min      > 90 conservata funzione\n"
                "Transaminasi ALT (GPT)      22          U/L         10 - 45\n"
                "Transaminasi AST (GOT)      19          U/L         10 - 40\n"
                "TSH riflesso                1.85        microUI/mL  0.40 - 4.00\n\n"
                "Osservazioni: Il quadro biochimico rientra nei limiti fisiologici di normalità. Campione non emolizzato."
            )
        },
        {
            "id": "HO_ALT_08",
            "type": "ALTRO",
            "expected_classification": "ALTRO",
            "description": "Lettera di motivazione per candidatura accademica",
            "payload": (
                "Alla cortese attenzione della Commissione Giudicatrice\n"
                "Dottorato di Ricerca in Scienze della Terra e Cambiamento Globale\n"
                "Università degli Studi di Padova\n\n"
                "LETTERA DI MOTIVAZIONE PER L'AMMISSIONE AL CICLO XLII\n\n"
                "Scrivo per esprimere con profondo entusiasmo il mio interesse a concorrere per la borsa di dottorato "
                "focalizzata sullo studio dei modelli idrodinamici delle falde acquifere nell'arco alpino orientale. "
                "Durante il mio percorso di laurea magistrale in Scienze Geologiche ho sviluppato una solida preparazione "
                "nella modellazione numerica tridimensionale e nell'analisi geostatistica di dati piezometrici storici. "
                "La reputazione scientifica del Vostro dipartimento e le infrastrutture analitiche disponibili rappresentano "
                "l'ambiente ideale per approfondire la ricerca sull'impatto dei periodi di siccità prolungata sui sistemi idrici montani.\n\n"
                "Ringraziando per l'attenzione riservata alla presente candidatura, rimango a completa disposizione per il colloquio orale."
            )
        }
    ]
    return docs


# ==============================================================================
# Salvataggio su Disco con Formattazione Ordinata e Deterministica
# ==============================================================================

def main():
    print("Avvio generazione deterministica del dataset held-out (Passo 3)...")

    # 1. Dataset Principale (42 documenti: 14 FAT, 14 ANA, 14 EML)
    heldout_main = build_heldout_dataset()
    main_path = DATA_DIR / "heldout_dataset_v1.json"
    with open(main_path, "w", encoding="utf-8") as f:
        json.dump(heldout_main, f, indent=2, ensure_ascii=False)
    print(f"Salvati {len(heldout_main)} documenti principali in: {main_path}")

    # 2. Dataset Avversariale (6 documenti)
    heldout_adv = build_heldout_adversarial()
    adv_path = DATA_DIR / "heldout_adversarial_v1.json"
    with open(adv_path, "w", encoding="utf-8") as f:
        json.dump(heldout_adv, f, indent=2, ensure_ascii=False)
    print(f"Salvati {len(heldout_adv)} documenti avversariali in: {adv_path}")

    # 3. Dataset Router ALTRO (8 documenti)
    heldout_router = build_heldout_router_altro()
    router_path = DATA_DIR / "heldout_router_altro_v1.json"
    with open(router_path, "w", encoding="utf-8") as f:
        json.dump(heldout_router, f, indent=2, ensure_ascii=False)
    print(f"Salvati {len(heldout_router)} documenti router ALTRO in: {router_path}")

    print("\nGenerazione completata con successo.")

if __name__ == "__main__":
    main()
