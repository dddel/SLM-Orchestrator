import json
import math
from pathlib import Path

base = Path("risultati/test2/batteria1_3b_guardrail_ablation")
candidates = [
    "3B_CON_GUARDRAIL",
    "3B_ABLAZIONE_GUARDRAIL",
    "3B_MONOLITICO_SENZA_GUARDRAIL",
    "3B_MONOLITICO_POSTHOC_GUARDRAIL"
]
cells = [c for c in candidates if (base / c).is_dir()]
splits = ["heldout", "heldout_adversarial", "dev", "dev_adversarial"]

def calc_lat_stats(lats):
    if not lats:
        return {"median": 0.0, "mean": 0.0, "p95": 0}
    s = sorted(lats)
    n = len(s)
    med = (s[n // 2] if n % 2 == 1 else (s[n // 2 - 1] + s[n // 2]) / 2.0)
    mean = round(sum(s) / n, 1)
    p95_idx = max(0, min(n - 1, math.ceil(0.95 * n) - 1))
    return {"median": float(med), "mean": mean, "p95": int(s[p95_idx])}

report = {}
for s in splits:
    report[s] = {}
    for c in cells:
        sum_file = base / c / s / "summary.json"
        if not sum_file.exists():
            continue
        data = json.loads(sum_file.read_text())
        docs = list(data["documents"].values())

        val_on_null = 0
        all_lats = []
        by_type = {}
        for d in docs:
            dtype = d["type"]
            by_type.setdefault(dtype, {"docs": 0, "c1": 0, "c2": 0, "c3": 0, "c4": 0, "cf": 0, "tf": 0, "lats": []})
            by_type[dtype]["docs"] += 1
            cat = d["category"]
            if cat in ("C1", "C2", "C3", "C4"):
                by_type[dtype][cat.lower()] += 1
            by_type[dtype]["cf"] += d["correct_fields"]
            by_type[dtype]["tf"] += d["total_fields"]
            lat = d.get("client_latency_ms")
            if lat is None and isinstance(d.get("raw_response"), dict):
                lat = d["raw_response"].get("guardrailsLatencyMs")
                orig = d["raw_response"].get("originalRawResponse")
                if isinstance(orig, dict) and isinstance(orig.get("metrics"), dict):
                    lat = orig["metrics"].get("clientLatencyMs", lat)
            if lat is not None:
                all_lats.append(lat)
                by_type[dtype]["lats"].append(lat)
            for fm in d["field_matches"].values():
                if fm.get("field_class") == "VALORE_SU_NULL_ATTESO":
                    val_on_null += 1

        by_type_clean = {}
        for dtype, info in by_type.items():
            lstats = calc_lat_stats(info["lats"])
            by_type_clean[dtype] = {
                "docs": info["docs"],
                "c1": info["c1"],
                "c2": info["c2"],
                "c3": info["c3"],
                "c4": info["c4"],
                "cf": info["cf"],
                "tf": info["tf"],
                "median_ms": int(lstats["median"]) if lstats["median"].is_integer() else lstats["median"],
                "mean_ms": lstats["mean"]
            }

        report[s][c] = {
            "metadata": data["metadata"],
            "classification_counts": data["classification_counts"],
            "c2_subcode_counts": data.get("c2_subcode_counts", {}),
            "classification_wilson": data.get("classification_wilson", {}),
            "fields_summary": data["fields_summary"],
            "valore_su_null_atteso": val_on_null,
            "latency_ms": calc_lat_stats(all_lats),
            "by_type": by_type_clean
        }

(base / "riepilogo_batteria1.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))

lines = []
lines.append("# Batteria 1: Qwen 2.5 3B Con Guardrail vs Ablazione Guardrail vs Monolitico Senza Guardrail vs Monolitico Post-Hoc Guardrail")
lines.append("")
git_commit = report.get("heldout", {}).get("3B_CON_GUARDRAIL", {}).get("metadata", {}).get("git_commit", "freeze-1")
lines.append(f"- **Commit Git (`freeze-1`)**: `{git_commit}`")
lines.append("- **Modello**: `qwen2.5:3b-instruct-q8_0` (`temperature=0.0`, `seed=42`, `num_ctx=2048`, `num_predict=400`, `repeat_penalty=1.0`)")
lines.append("- **Runner e Valutatore**: Classi Java congelate (`ComparisonRunner`, `GroundTruthComparator`, `PostHocGuardrails`, `WilsonScore`, `OllamaMonolithicClient`) con pipeline YAML originali (`src/main/resources/pipelines/*.yaml`).")
lines.append("- **Configurazioni**:")
lines.append("  - `3B_CON_GUARDRAIL`: `orchestrator.guards.enabled=true`, `orchestrator.coherence.enabled=true`")
lines.append("  - `3B_ABLAZIONE_GUARDRAIL`: `orchestrator.guards.enabled=false`, `orchestrator.coherence.enabled=false`")
lines.append("  - `3B_MONOLITICO_SENZA_GUARDRAIL`: chiamata singola `OllamaMonolithicClient` con `prompt-monolitico.txt` e `schema-busta.json`")
lines.append("  - `3B_MONOLITICO_POSTHOC_GUARDRAIL`: derivazione deterministica tramite `PostHocGuardrails` applicata agli output grezzi di `3B_MONOLITICO_SENZA_GUARDRAIL`")
lines.append("")

def add_split_table(title, split_key, is_adv=False):
    lines.append(f"## {title}")
    lines.append("")
    active_cells = [c for c in cells if c in report.get(split_key, {})]
    if not is_adv:
        lines.append("| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            cc = d["classification_counts"]
            c2s = d.get("c2_subcode_counts", {})
            c2_str = f"{cc.get('C2', 0)} ({c2s.get('C2a', 0)}/{c2s.get('C2b', 0)}/{c2s.get('C2c', 0)})"
            lat = d["latency_ms"]
            lines.append(f"| `{cfg}` | {d['fields_summary'].get('accuracy_wilson', 'N/D')} | {d.get('classification_wilson', {}).get('C1', 'N/D')} | {c2_str} | {cc.get('C3', 0)} | {cc.get('C4', 0)} | {cc.get('C5', 0)} | {d['valore_su_null_atteso']} | {lat['median']} | {lat['mean']} | {lat['p95']} |")
        lines.append("")
        lines.append("### Dettaglio per Tipologia Documento")
        lines.append("")
        lines.append("| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot | Latenza Mediana (ms) | Latenza Media (ms) |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            bt = report[split_key][cfg]["by_type"]
            for dtype in ["FATTURA", "ANAGRAFICA", "EMAIL"]:
                if dtype in bt:
                    t = bt[dtype]
                    pct = round(t['cf'] * 100.0 / t['tf'], 1) if t['tf'] > 0 else 0.0
                    lines.append(f"| `{cfg}` | `{dtype}` | {t['c1']}/{t['docs']} | {t['c2']} | {t['c3']} | {t['c4']} | {t['cf']}/{t['tf']} ({pct}%) | {t['median_ms']} | {t['mean_ms']} |")
        lines.append("")
    else:
        lines.append("| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            cc = d["classification_counts"]
            tot_docs = sum(cc.values())
            ok_docs = cc.get("C1", 0) + cc.get("C2", 0)
            ind_docs = cc.get("C3", 0) + cc.get("C4", 0)
            ok_pct = round(ok_docs * 100.0 / tot_docs, 1) if tot_docs > 0 else 0.0
            ind_pct = round(ind_docs * 100.0 / tot_docs, 1) if tot_docs > 0 else 0.0
            lat = d["latency_ms"]
            lines.append(f"| `{cfg}` | {ok_docs}/{tot_docs} ({ok_pct}%) | {ind_docs}/{tot_docs} ({ind_pct}%) | {cc.get('C1', 0)} | {cc.get('C2', 0)} | {cc.get('C3', 0)} | {cc.get('C4', 0)} | {d['fields_summary'].get('accuracy_wilson', 'N/D')} | {lat['median']} | {lat['mean']} | {lat['p95']} |")
        lines.append("")

add_split_table("1. Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)", "heldout", False)
add_split_table("2. Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi)", "heldout_adversarial", True)
add_split_table("3. Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)", "dev", False)
add_split_table("4. Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida)", "dev_adversarial", True)

(base / "TABELLA_BATTERIA1.md").write_text("\n".join(lines) + "\n")
print("Aggiornata TABELLA_BATTERIA1.md con", len(cells), "configurazioni.")
