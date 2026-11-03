import json
from pathlib import Path

base = Path("risultati/test2/batteria2_3b_vs_7b")
candidates = [
    "3B_CON_GUARDRAIL",
    "7B_MONOLITICO_SENZA_GUARDRAIL",
    "7B_MONOLITICO_POSTHOC_GUARDRAIL",
    "7B_ABLAZIONE_GUARDRAIL",
    "7B_CON_GUARDRAIL"
]
cells = [c for c in candidates if (base / c).is_dir()]
splits = ["heldout", "heldout_adversarial", "dev", "dev_adversarial"]

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
        by_type = {}
        for d in docs:
            dtype = d["type"]
            by_type.setdefault(dtype, {"docs": 0, "c1": 0, "c2": 0, "c3": 0, "c4": 0, "cf": 0, "tf": 0})
            by_type[dtype]["docs"] += 1
            cat = d["category"]
            if cat in ("C1", "C2", "C3", "C4"):
                by_type[dtype][cat.lower()] += 1
            by_type[dtype]["cf"] += d["correct_fields"]
            by_type[dtype]["tf"] += d["total_fields"]
            for fm in d["field_matches"].values():
                if fm.get("field_class") == "VALORE_SU_NULL_ATTESO":
                    val_on_null += 1

        report[s][c] = {
            "metadata": data["metadata"],
            "classification_counts": data["classification_counts"],
            "c2_subcode_counts": data.get("c2_subcode_counts", {}),
            "classification_wilson": data.get("classification_wilson", {}),
            "fields_summary": data["fields_summary"],
            "valore_su_null_atteso": val_on_null,
            "by_type": by_type
        }

(base / "riepilogo_batteria2.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))

lines = []
lines.append("# Batteria 2: Qwen 2.5 3B Con Guardrail (Cella A) vs Qwen 2.5 7B Senza Guardrail (Cella B) e 7B Post-Hoc Guardrail (Cella C) e 7B Orchestratore")
lines.append("")
git_commit = report.get("heldout", {}).get("3B_CON_GUARDRAIL", {}).get("metadata", {}).get("git_commit", "freeze-1")
lines.append(f"- **Commit Git (`freeze-1`)**: `{git_commit}`")
lines.append("- **Modelli confrontati**: `qwen2.5:3b-instruct-q8_0` (Cella A) vs `qwen2.5:7b-instruct-q8_0` (Celle B, C, 7B_ABLAZIONE, 7B_CON_GUARDRAIL)")
lines.append("- **Parametri di campionamento**: `temperature=0.0`, `seed=42`, `num_ctx=2048`, `num_predict=400`, `repeat_penalty=1.0`")
lines.append("- **Nota Hardware**: Come da protocollo, non viene effettuato alcun confronto di latenza tra 3B e 7B poiché `qwen2.5:7b-instruct-q8_0` viene eseguito in split GPU/CPU sulla macchina di test.")
lines.append("- **Runner e Valutatore**: Classi Java congelate (`ComparisonRunner`, `GroundTruthComparator`, `PostHocGuardrails`, `WilsonScore`, `OllamaMonolithicClient`).")
lines.append("")

def add_split_table(title, split_key, is_adv=False):
    lines.append(f"## {title}")
    lines.append("")
    active_cells = [c for c in cells if c in report.get(split_key, {})]
    if not is_adv:
        lines.append("| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            cc = d["classification_counts"]
            c2s = d.get("c2_subcode_counts", {})
            c2_str = f"{cc.get('C2', 0)} ({c2s.get('C2a', 0)}/{c2s.get('C2b', 0)}/{c2s.get('C2c', 0)})"
            lines.append(f"| `{cfg}` | {d['fields_summary'].get('accuracy_wilson', 'N/D')} | {d.get('classification_wilson', {}).get('C1', 'N/D')} | {c2_str} | {cc.get('C3', 0)} | {cc.get('C4', 0)} | {cc.get('C5', 0)} | {d['valore_su_null_atteso']} |")
        lines.append("")
        lines.append("### Dettaglio per Tipologia Documento")
        lines.append("")
        lines.append("| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            bt = report[split_key][cfg]["by_type"]
            for dtype in ["FATTURA", "ANAGRAFICA", "EMAIL"]:
                if dtype in bt:
                    t = bt[dtype]
                    pct = round(t['cf'] * 100.0 / t['tf'], 1) if t['tf'] > 0 else 0.0
                    lines.append(f"| `{cfg}` | `{dtype}` | {t['c1']}/{t['docs']} | {t['c2']} | {t['c3']} | {t['c4']} | {t['cf']}/{t['tf']} ({pct}%) |")
        lines.append("")
    else:
        lines.append("| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            cc = d["classification_counts"]
            tot_docs = sum(cc.values())
            ok_docs = cc.get("C1", 0) + cc.get("C2", 0)
            ind_docs = cc.get("C3", 0) + cc.get("C4", 0)
            ok_pct = round(ok_docs * 100.0 / tot_docs, 1) if tot_docs > 0 else 0.0
            ind_pct = round(ind_docs * 100.0 / tot_docs, 1) if tot_docs > 0 else 0.0
            lines.append(f"| `{cfg}` | {ok_docs}/{tot_docs} ({ok_pct}%) | {ind_docs}/{tot_docs} ({ind_pct}%) | {cc.get('C1', 0)} | {cc.get('C2', 0)} | {cc.get('C3', 0)} | {cc.get('C4', 0)} | {d['fields_summary'].get('accuracy_wilson', 'N/D')} |")
        lines.append("")

add_split_table("1. Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)", "heldout", False)
add_split_table("2. Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi)", "heldout_adversarial", True)
add_split_table("3. Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)", "dev", False)
add_split_table("4. Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida)", "dev_adversarial", True)

(base / "TABELLA_BATTERIA2.md").write_text("\n".join(lines) + "\n")
print(f"Aggiornata TABELLA_BATTERIA2.md con {len(cells)} configurazioni.")
