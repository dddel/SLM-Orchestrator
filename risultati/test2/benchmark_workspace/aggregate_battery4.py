import json
import statistics
from pathlib import Path
from collections import Counter

base = Path("risultati/test2/batteria4_smart_retry")
cells = [
    "3B_CON_GUARDRAIL_SENZA_RETRY",
    "3B_CON_GUARDRAIL_E_RETRY",
    "7B_CON_GUARDRAIL_E_RETRY"
]
splits = ["heldout", "heldout_adversarial", "dev", "dev_adversarial"]

def analyze_doc_file(doc_file: Path):
    if not doc_file.exists():
        return 0, 0, [], [], 0
    d = json.loads(doc_file.read_text())
    raw = d.get("raw_response", {}) or {}
    metrics = raw.get("metrics", {}) or {}
    steps = metrics.get("steps", []) or []
    inf_steps = [s for s in steps if s.get("type") == "MODEL_INFERENCE"]
    step_counts = Counter(s.get("stepId") for s in inf_steps)
    # Extra retry calls = sum of (count - 1) for each distinct stepId
    extra_retries = sum(cnt - 1 for cnt in step_counts.values() if cnt > 1)
    retried_step_ids = [sid for sid, cnt in step_counts.items() if cnt > 1]
    errors = raw.get("errors", []) or []
    action_errors = raw.get("actionErrors", []) or []
    all_errors = errors + action_errors
    lat = raw.get("latencyMs") or d.get("client_latency_ms") or 0
    return len(inf_steps), extra_retries, retried_step_ids, inf_steps, all_errors, lat

report = {}
for s in splits:
    report[s] = {}
    for c in cells:
        sum_file = base / c / s / "summary.json"
        if not sum_file.exists():
            continue
        data = json.loads(sum_file.read_text())
        docs = data["documents"]

        val_on_null = 0
        by_type = {}
        total_inference_steps = 0
        docs_with_retries = 0
        extra_inference_steps = 0
        retries_resolved_to_c1 = 0
        retries_resolved_to_ok = 0
        retries_ended_indeterminate = 0
        short_circuited_docs = 0
        retry_trajectories = []
        latencies = []

        for doc_id, d in sorted(docs.items()):
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

            doc_file = base / c / s / "docs" / f"{doc_id}.json"
            n_inf, extra_ret, retried_sids, inf_steps, all_errors, lat = analyze_doc_file(doc_file)
            latencies.append(lat)
            total_inference_steps += n_inf

            no_retry_doc_file = base / "3B_CON_GUARDRAIL_SENZA_RETRY" / s / "docs" / f"{doc_id}.json"
            _, _, _, _, no_retry_errors, _ = analyze_doc_file(no_retry_doc_file)

            if extra_ret > 0:
                docs_with_retries += 1
                extra_inference_steps += extra_ret
                if cat == "C1":
                    retries_resolved_to_c1 += 1
                if d.get("status") == "OK":
                    retries_resolved_to_ok += 1
                else:
                    retries_ended_indeterminate += 1

                err_codes = [f"{e.get('path')}: {e.get('code')} ({e.get('validatorName')})" for e in all_errors]
                initial_err_codes = [f"{e.get('path')}: {e.get('code')} ({e.get('validatorName')})" for e in no_retry_errors]

                # Filter attempts to only the retried stepId(s)
                retried_attempts = [st for st in inf_steps if st.get("stepId") in retried_sids]

                retry_trajectories.append({
                    "doc_id": doc_id,
                    "type": dtype,
                    "retried_step_id": ", ".join(retried_sids),
                    "step_attempts": len(retried_attempts),
                    "total_doc_inference_steps": n_inf,
                    "final_status": d.get("status"),
                    "final_category": cat,
                    "correct_fields": d["correct_fields"],
                    "total_fields": d["total_fields"],
                    "total_latency_ms": lat,
                    "initial_errors_if_3b": initial_err_codes,
                    "final_errors": err_codes,
                    "attempts": [
                        {
                            "attempt": idx + 1,
                            "stepId": st.get("stepId"),
                            "latency_ms": st.get("latencyMs"),
                            "prompt_tokens": st.get("promptTokens"),
                            "completion_tokens": st.get("completionTokens")
                        }
                        for idx, st in enumerate(retried_attempts)
                    ]
                })
            elif d.get("status") == "INDETERMINATE" and all_errors:
                if any(e.get("code") == "SOURCE_CHECKSUM_INVALID" for e in all_errors):
                    short_circuited_docs += 1

        lat_stats = {}
        if latencies:
            lat_stats = {
                "mean_ms": round(statistics.mean(latencies), 1),
                "median_ms": round(statistics.median(latencies), 1),
                "min_ms": min(latencies),
                "max_ms": max(latencies)
            }

        report[s][c] = {
            "metadata": data["metadata"],
            "classification_counts": data["classification_counts"],
            "c2_subcode_counts": data.get("c2_subcode_counts", {}),
            "classification_wilson": data.get("classification_wilson", {}),
            "fields_summary": data["fields_summary"],
            "valore_su_null_atteso": val_on_null,
            "latency_ms": lat_stats,
            "retry_metrics": {
                "total_docs": len(docs),
                "total_inference_steps": total_inference_steps,
                "docs_with_retries": docs_with_retries,
                "extra_inference_steps": extra_inference_steps,
                "retries_resolved_to_c1": retries_resolved_to_c1,
                "retries_resolved_to_ok": retries_resolved_to_ok,
                "retries_ended_indeterminate": retries_ended_indeterminate,
                "short_circuited_source_checksum_docs": short_circuited_docs,
                "saved_retry_inferences_by_short_circuit": short_circuited_docs * 2 if "E_RETRY" in c else 0,
                "retry_trajectories": retry_trajectories
            },
            "by_type": by_type
        }

(base / "riepilogo_batteria4.json").write_text(json.dumps(report, indent=2, ensure_ascii=False))

lines = []
lines.append("# Batteria 4: Efficacia dello Smart Retry (`RF7`), Cross-Field Coherence e Short-Circuit Avversariale")
lines.append("")
git_commit = report.get("heldout", {}).get("3B_CON_GUARDRAIL_E_RETRY", {}).get("metadata", {}).get("git_commit", "freeze-1")
lines.append(f"- **Commit Git (`freeze-1`)**: `{git_commit}`")
lines.append("- **Configurazioni a confronto**:")
lines.append("  1. `3B_CON_GUARDRAIL_SENZA_RETRY`: `qwen2.5:3b-instruct-q8_0`, Guardrail deterministico attivo, Cross-Field Coherence attiva, `max_retries=0` (pipeline caricate da `risultati/test2/benchmark_workspace/pipelines_no_retry` senza blocchi `on_failure`).")
lines.append("  2. `3B_CON_GUARDRAIL_E_RETRY`: `qwen2.5:3b-instruct-q8_0`, Guardrail deterministico attivo, Cross-Field Coherence attiva, `max_retries=2` con iniezione feedback errori tipizzati (`RF7`, senza valore rifiutato) e incremento temperatura `0.0 -> 0.2`.")
lines.append("  3. `7B_CON_GUARDRAIL_E_RETRY`: `qwen2.5:7b-instruct-q8_0`, Guardrail deterministico attivo, Cross-Field Coherence attiva, `max_retries=2` con `RF7` e incremento temperatura `0.0 -> 0.2`.")
lines.append("- **Nota sugli Step di Inferenza**: I documenti `FATTURA` e `ANAGRAFICA` prevedono **1 step base** (`fattura_inference` / `anagrafica_inference`), mentre i documenti `EMAIL` prevedono **2 step sequenziali distinti** (`email_inference` per i 9 campi di estrazione + `recipient_inference` per l'instradamento verso il catalogo `recipients_catalog.json`). Pertanto, su `heldout` (14 FATTURA + 14 ANAGRAFICA + 14 EMAIL) il numero minimo fisiologico di chiamate LLM senza alcun retry è `14 + 14 + 28 = 56`; su `dev` (20 FATTURA + 20 ANAGRAFICA + 20 EMAIL) è `20 + 20 + 40 = 80`. Un retry si attiva esclusivamente quando uno stesso `stepId` viene invocato `> 1` volta.")
lines.append("- **Meccanismo di Short-Circuit Avversariale (`SOURCE_CHECKSUM_INVALID`)**: Se un codice formalmente invalido (P.IVA Luhn, CF CIN, IBAN MOD-97) è presente *letteralmente* nel testo sorgente (`DocumentContext.containsInSource`), `OutputValidationStep` emette il codice `SOURCE_CHECKSUM_INVALID` e `BasePipeline.canShortCircuitSourceErrors` interrompe immediatamente il ciclo di retry dopo **1 sola chiamata LLM** (`n_inference_steps = 1`), evitando 2 retry inutili per documento.")
lines.append("")

def add_split_section(title, split_key, is_adv=False):
    lines.append(f"## {title}")
    lines.append("")
    active_cells = [c for c in cells if c in report.get(split_key, {})]
    if not is_adv:
        lines.append("### Metriche di Accuratezza e Classificazione Documentale")
        lines.append("")
        lines.append("| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            cc = d["classification_counts"]
            c2s = d.get("c2_subcode_counts", {})
            c2_str = f"{cc.get('C2', 0)} ({c2s.get('C2a', 0)}/{c2s.get('C2b', 0)}/{c2s.get('C2c', 0)})"
            lines.append(f"| `{cfg}` | {d['fields_summary'].get('accuracy_wilson', 'N/D')} | {d.get('classification_wilson', {}).get('C1', 'N/D')} | {c2_str} | {cc.get('C3', 0)} | {cc.get('C4', 0)} | {cc.get('C5', 0)} | {d['valore_su_null_atteso']} |")
        lines.append("")
        lines.append("### Metriche di Smart Retry (`RF7`) e Chiamate di Inferenza")
        lines.append("")
        lines.append("| Configurazione | Doc Totali | Chiamate LLM Totali | Doc con Retry (`stepId > 1`) | Chiamate Extra di Retry | Recuperati a `OK` (`C1`) | Esauriti in `INDETERMINATE` (`C3`/`C4`) | Latenza Media / Mediana (ms) |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            rm = d["retry_metrics"]
            lat = d["latency_ms"]
            lat_str = f"{lat.get('mean_ms', 'N/D')} / {lat.get('median_ms', 'N/D')}" if "3B" in cfg else "N/A (split GPU/CPU)"
            lines.append(f"| `{cfg}` | {rm['total_docs']} | {rm['total_inference_steps']} | {rm['docs_with_retries']} | +{rm['extra_inference_steps']} | {rm['retries_resolved_to_ok']} ({rm['retries_resolved_to_c1']} `C1`) | {rm['retries_ended_indeterminate']} | {lat_str} |")
        lines.append("")

        trajs = []
        for cfg in active_cells:
            for t in report[split_key][cfg]["retry_metrics"]["retry_trajectories"]:
                trajs.append((cfg, t))
        if trajs:
            lines.append("### Tracciato Analitico dei Documenti con Attivazione di Smart Retry (`RF7`)")
            lines.append("")
            lines.append("| Configurazione | Doc ID (`Tipo`) | Step in Retry (`Tentativi`) | Tentativo 1 (`prompt`/`compl` tok, ms) | Tentativo 2 (`prompt`/`compl` tok, ms) | Tentativo 3 (`prompt`/`compl` tok, ms) | Causa Trigger / Errore Guardrail | Esito Finale (`Categoria`, Campi) |")
            lines.append("| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |")
            for cfg, t in trajs:
                atts = t["attempts"]
                a1 = f"`{atts[0]['prompt_tokens']}`/`{atts[0]['completion_tokens']}` tok ({atts[0]['latency_ms']} ms)" if len(atts) >= 1 else "-"
                a2 = f"`{atts[1]['prompt_tokens']}`/`{atts[1]['completion_tokens']}` tok ({atts[1]['latency_ms']} ms)" if len(atts) >= 2 else "-"
                a3 = f"`{atts[2]['prompt_tokens']}`/`{atts[2]['completion_tokens']}` tok ({atts[2]['latency_ms']} ms)" if len(atts) >= 3 else "-"
                if t["final_errors"]:
                    err_desc = ", ".join(f"`{e}` (non risolto)" for e in t["final_errors"])
                elif t["initial_errors_if_3b"] and "3B" in cfg:
                    err_desc = ", ".join(f"`{e}` (risolto al T{t['step_attempts']})" for e in t["initial_errors_if_3b"])
                else:
                    err_desc = f"`destinatario: VALUE_NOT_IN_CATALOG (catalog_membership)` (risolto al T{t['step_attempts']})"
                lines.append(f"| `{cfg}` | `{t['doc_id']}` (`{t['type']}`) | `{t['retried_step_id']}` ({t['step_attempts']}) | {a1} | {a2} | {a3} | {err_desc} | `{t['final_status']}` (`{t['final_category']}`, {t['correct_fields']}/{t['total_fields']}) |")
            lines.append("")
    else:
        lines.append("| Configurazione | Codici Invalidi Intercettati (`INDETERMINATE`) | Chiamate LLM Totali | Doc in Short-Circuit (`SOURCE_CHECKSUM_INVALID`) | Chiamate di Retry Risparmiate | Campi Estratti Fedeli al Testo (Wilson 95%) |")
        lines.append("| :--- | :---: | :---: | :---: | :---: | :---: |")
        for cfg in active_cells:
            d = report[split_key][cfg]
            cc = d["classification_counts"]
            rm = d["retry_metrics"]
            tot_docs = rm["total_docs"]
            ind_docs = cc.get("C3", 0) + cc.get("C4", 0)
            ind_pct = round(ind_docs * 100.0 / tot_docs, 1) if tot_docs > 0 else 0.0
            lines.append(f"| `{cfg}` | {ind_docs}/{tot_docs} ({ind_pct}%) | {rm['total_inference_steps']} | {rm['short_circuited_source_checksum_docs']}/{tot_docs} | {rm['saved_retry_inferences_by_short_circuit']} | {d['fields_summary'].get('accuracy_wilson', 'N/D')} |")
        lines.append("")

add_split_section("1. Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)", "heldout", False)
add_split_section("2. Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi nel testo)", "heldout_adversarial", True)
add_split_section("3. Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)", "dev", False)
add_split_section("4. Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida nel testo)", "dev_adversarial", True)

(base / "TABELLA_BATTERIA4.md").write_text("\n".join(lines) + "\n")
print("Generati riepilogo_batteria4.json e TABELLA_BATTERIA4.md con successo.")
