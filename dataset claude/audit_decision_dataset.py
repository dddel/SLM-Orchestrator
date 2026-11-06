#!/usr/bin/env python3
"""Audit indipendente: non importa il generatore (tranne per la rigenerazione, via subprocess)."""
import json, re, sys, hashlib, subprocess, tempfile, os, difflib, collections
D = sys.argv[1]; SEED = sys.argv[2]; NR = sys.argv[3]; NE = sys.argv[4]; GEN = sys.argv[5]
rows = [json.loads(l) for l in open(os.path.join(D, "dataset_all.jsonl"), encoding="utf-8")]
errs = []
def E(m): errs.append(m)

# --- checksum (implementazione separata) ---
T_ODD = {c: v for c, v in zip("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ", [1,0,5,7,9,13,15,17,19,21]+[1,0,5,7,9,13,15,17,19,21,2,4,18,20,11,3,6,8,12,14,16,10,22,25,24,23])}
def cf_ok(c):
    s = sum(T_ODD[ch] if i % 2 == 0 else (int(ch) if ch.isdigit() else ord(ch)-65) for i, ch in enumerate(c[:15])); return chr(65+s%26) == c[15]
def piva_ok(p):
    s = 0
    for i, ch in enumerate(p):
        n = int(ch)
        if i % 2 == 1: n = n*2 - (9 if n*2 > 9 else 0)
        s += n
    return s % 10 == 0
def iban_ok(b):
    if int("".join(str(int(c, 36)) for c in b[4:]+b[:4])) % 97 != 1: return False
    s = sum(T_ODD[ch] if i % 2 == 0 else (int(ch) if ch.isdigit() else ord(ch)-65) for i, ch in enumerate(b[5:])); return chr(65+s%26) == b[4]
ntok = collections.Counter()
for r in rows:
    for t in (r["text"], r.get("clean_text")):
        if not t: continue
        for m in re.findall(r"\b[A-Z]{6}\d{2}[A-Z]\d{2}[A-Z]\d{3}[A-Z]\b", t): ntok["CF"] += 1; (cf_ok(m) or E(f"{r['id']}: CF non valido {m}"))
        for m in re.findall(r"\bIT\d{2}[A-Z]\d{22}\b", t): ntok["IBAN"] += 1; (iban_ok(m) or E(f"{r['id']}: IBAN non valido {m}"))
        for m in re.findall(r"(?:P\.IVA:?|Partita IVA|VAT ID IT)\s?(\d{11})", t): ntok["PIVA"] += 1; (piva_ok(m) or E(f"{r['id']}: P.IVA non valida {m}"))

# i token con cifre (CF, IBAN, P.IVA) non devono essere alterati dal rumore OCR
for r in rows:
    if r.get("clean_text"):
        for m in re.findall(r"\b[A-Z]{6}\d{2}[A-Z]\d{2}[A-Z]\d{3}[A-Z]\b|\bIT\d{2}[A-Z]\d{22}\b|\b\d{11}\b", r["clean_text"]):
            if m not in r["text"]: E(f"{r['id']}: token {m} alterato dal rumore")
# --- regole di etichettatura sul testo pulito ---
HDR = re.compile(r"^\s*(Da|From):.*\n\s*(A|To):", re.I)
def first(t): return next((l for l in t.splitlines() if l.strip()), "")
FATT_TITLE = re.compile(r"^(fattura|invoice|ft\.|fatt\.)", re.I)
def is_fattura(t):
    return bool(FATT_TITLE.match(first(t))) and bool(re.search(r"\b(IVA|VAT)\b", t)) and bool(re.search(r"imponibile|imp\.|taxable", t, re.I))
def is_email(t): return bool(HDR.match(t))
def is_scheda(t): return bool(re.search(r"(scheda anagrafica|anagrafica|dati ente|contatto:|codice fiscale|C\.F\.|CF:|nato a)", first(t) + "\n" + t[:300], re.I)) and bool(re.search(r"\b[A-Z]{6}\d{2}[A-Z]\d{2}[A-Z]\d{3}[A-Z]\b|\bP\.?\s?IVA\b|Partita IVA", t))
ASKS = None
for r in rows:
    t = r["clean_text"] or r["text"]
    if r["task"] == "router":
        f, e = is_fattura(t), is_email(t)
        if r["label"] == "FATTURA" and not (f and not e): E(f"{r['id']}: etichetta FATTURA senza marcatori di fattura")
        if r["label"] == "EMAIL" and not e: E(f"{r['id']}: etichetta EMAIL senza intestazioni Da/A")
        if r["label"] == "ANAGRAFICA" and (e or f or not is_scheda(t)): E(f"{r['id']}: ANAGRAFICA incoerente")
        if r["label"] == "ALTRO" and (e or f): E(f"{r['id']}: ALTRO soddisfa marcatori di EMAIL/FATTURA")
        if r["clean_text"]:
            ratio = difflib.SequenceMatcher(None, r["clean_text"], r["text"]).ratio()
            if ratio < 0.88: E(f"{r['id']}: rumore OCR eccessivo (similarità {ratio:.2f})")
    if r["task"] == "email_role":
        # una sola famiglia di richieste nelle righe NON citate, e coincide con l'etichetta
        body = "\n".join(l for l in t.splitlines() if not l.lstrip().startswith(">"))
        if r["ask"].lower() not in body.lower() and r["strata"].split("+")[0] != "lungo_distrattori": E(f"{r['id']}: richiesta assente")
        if r["ask"].lower() not in body.lower(): E(f"{r['id']}: richiesta assente (lungo)")
        # richieste degli altri ruoli (ricostruite dai dati stessi) non devono comparire nella parte non citata
        allasks = collections.defaultdict(set)
        for q in rows:
            if q["task"] == "email_role": allasks[q["label"]].add(q["ask"].lower())
        for role, s in allasks.items():
            if role == r["label"]: continue
            for a_ in s:
                if a_ in body.lower(): E(f"{r['id']}: contiene richiesta di {role}: '{a_}'")
        if r["label"] in r["ctx_roles"] and r["strata"].startswith("conflitto"): E(f"{r['id']}: contesto contiene il ruolo corretto in strato conflitto")

# --- split disgiunti ---
for task, key in (("email_role", "ask"), ("router", "template")):
    s = {sp: {r[key] for r in rows if r["task"] == task and r["split"] == sp} for sp in ("dev", "test")}
    if key == "ask":
        pass
    inter = s["dev"] & s["test"]
    if inter: E(f"{task}: {key} condivisi tra dev e test: {sorted(inter)[:3]}")
ids = [r["id"] for r in rows]; (len(ids) == len(set(ids)) or E("ID duplicati"))
tx = collections.Counter(r["text"] for r in rows); dup = sum(v-1 for v in tx.values() if v > 1)
dups_by_task = collections.Counter(r["task"] for r in rows if tx[r["text"]] > 1)

# --- rigenerazione ---
with tempfile.TemporaryDirectory() as td:
    subprocess.run([sys.executable, GEN, "--seed", SEED, "--n-route", NR, "--n-email", NE, "--out", td], check=True, capture_output=True)
    same = hashlib.sha256(open(os.path.join(td, "dataset_all.jsonl"), "rb").read()).hexdigest() == hashlib.sha256(open(os.path.join(D, "dataset_all.jsonl"), "rb").read()).hexdigest()
    same or E("la rigenerazione con lo stesso seed NON è identica")

# --- riepilogo ---
print("campioni:", len(rows), dict(collections.Counter(r["task"] for r in rows)))
print("token verificati:", dict(ntok))
for task in ("router", "email_role"):
    for sp in ("dev", "test"):
        c = collections.Counter(r["label"] for r in rows if r["task"] == task and r["split"] == sp); print(f"{task}/{sp} classi:", dict(c))
    c = collections.Counter(r["strata"].split("+")[0] for r in rows if r["task"] == task); print(f"{task} strati:", dict(c))
    print(f"{task} con rumore OCR:", sum(1 for r in rows if r["task"] == task and r["clean_text"]))
print("testi duplicati (eccedenza):", dup, dict(dups_by_task))
print("rigenerazione identica:", same)
print("ANOMALIE:", len(errs)); [print(" -", e) for e in errs[:25]]
