#!/usr/bin/env python3
"""Baseline lessicali (NON sono il router del progetto): misurano quanto il dataset si risolve con sole parole-spia."""
import json, re, sys, collections, math, random
D = sys.argv[1]
rows = [json.loads(l) for l in open(f"{D}/dataset_test.jsonl", encoding="utf-8")]
def wilson(k, n, z=1.96):
    if n == 0: return (0, 0)
    p = k/n; d = 1+z*z/n; c = p+z*z/(2*n); h = z*math.sqrt(p*(1-p)/n+z*z/(4*n*n)); return ((c-h)/d, (c+h)/d)
# baseline routing: 4 regex semplici (come quelle già usate nel progetto sul dataset precedente)
def route(t):
    tl = t.lower()
    if re.search(r"^\s*(da|from):", tl, re.M) and re.search(r"^\s*(a|to):", tl, re.M): return "EMAIL"
    if re.search(r"fattura|invoice|imponibile|\biva\b|\bvat\b", tl): return "FATTURA"
    if re.search(r"codice fiscale|\bcf\b|c\.f\.|nato a|anagrafica|p\.iva|partita iva", tl): return "ANAGRAFICA"
    return "ALTRO"
def route_kw(t):  # puramente lessicale, senza regola sulle intestazioni
    tl = t.lower(); sc = {"FATTURA": len(re.findall(r"fattura|invoice|imponibile|\biva\b|\bvat\b|totale", tl)), "EMAIL": len(re.findall(r"cordiali saluti|buongiorno|gentile|ciao|grazie|inoltr", tl)),
        "ANAGRAFICA": len(re.findall(r"codice fiscale|\bcf\b|nato a|anagrafica|p\.iva|iban|residenza", tl)), "ALTRO": 0}
    b = max(sc, key=sc.get); return b if sc[b] > 0 else "ALTRO"
def role(t):
    tl = t.lower(); sc = {k: sum(tl.count(w) for w in ws) for k, ws in KW.items()}; m = max(sc.values())
    return random.Random(t).choice([k for k, v in sc.items() if v == m])
sys.path.insert(0, ".")
import importlib.util
spec = importlib.util.spec_from_file_location("g", "generate_decision_dataset.py"); g = importlib.util.module_from_spec(spec); spec.loader.exec_module(g); KW = g.KW
def report(name, task, fn):
    R = [r for r in rows if r["task"] == task]; by = collections.defaultdict(list)
    for r in R: by[r["strata"].split("+")[0]].append(fn(r["text"]) == r["label"])
    allk = sum(sum(v) for v in by.values()); n = len(R); lo, hi = wilson(allk, n)
    print(f"\n{name}: {allk}/{n} = {allk/n:.1%}  (Wilson sulle prove {lo:.1%}-{hi:.1%}; le prove NON sono tutte indipendenti: template ripetuti)")
    for k, v in sorted(by.items()): print(f"   {k:22s} {sum(v):4d}/{len(v):4d} = {sum(v)/len(v):.1%}")
report("ROUTER baseline regex (4 regole con regola sulle intestazioni)", "router", route)
report("ROUTER baseline solo lessicale (senza regola sulle intestazioni)", "router", route_kw)
report("EMAIL baseline parole-spia (conta tutte le parole del testo)", "email_role", role)
print("\nattesa per scelta casuale: router 25%, email 20%")
