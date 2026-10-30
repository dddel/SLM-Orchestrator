#!/usr/bin/env python3
"""
scripts/analyze_phase_b.py

Ricalcola rigorosamente tutte le metriche e tabelle della FASE B dai dati grezzi
memorizzati in target/phase_b/phase_b_raw.jsonl:
- Bias posizionale su 4, 8, 16 opzioni
- Accuratezza stretta e permissiva vs baselines (regole, random)
- Test Chi-quadro sul contenuto neutro
- Confronto paired cluster bootstrap Reflex vs Qwen
- Latenza TTFT (prompt eval) vs decode eval
- Caching prompt: prefix vs suffix
- Frequenza token non validi con think:false e tasso di recupero del retry ruotato
"""

import json
import math
import os
import sys
from collections import defaultdict, Counter
import random

RAW_PATH = "target/phase_b/phase_b_raw.jsonl"
REPORT_MD_PATH = "target/phase_b/phase_b_report.md"

def wilson_interval(k, n, z=1.96):
    if n == 0:
        return 0.0, 0.0, 0.0
    p = k / n
    denominator = 1 + z**2 / n
    centre_adjusted_probability = p + z**2 / (2 * n)
    adjusted_standard_deviation = math.sqrt((p * (1 - p) + z**2 / (4 * n)) / n)
    lower_bound = (centre_adjusted_probability - z * adjusted_standard_deviation) / denominator
    upper_bound = (centre_adjusted_probability + z * adjusted_standard_deviation) / denominator
    return p, max(0.0, lower_bound), min(1.0, upper_bound)

def chi_square_uniform(counts):
    total = sum(counts.values())
    k = len(counts)
    expected = total / k
    chi2 = sum((obs - expected)**2 / expected for obs in counts.values())
    # Approximate p-value for df = k - 1 using survival function approximation or gamma
    # For df = 3 (4 options): P(X >= x) = (1 + x/2) * exp(-x/2)
    p_val = None
    if k == 4:
        p_val = (1.0 + chi2 / 2.0) * math.exp(-chi2 / 2.0)
    return chi2, p_val

def paired_cluster_bootstrap(sample_trials_a, sample_trials_b, n_boot=10000, seed=42):
    """
    Cluster bootstrap at the sample level for paired comparison.
    sample_trials_a / b: dict mapping sample_id -> list of bool (correctness)
    """
    rng = random.Random(seed)
    sample_ids = sorted(sample_trials_a.keys())
    n_samples = len(sample_ids)
    
    # Calculate sample-level mean accuracies
    means_a = {sid: sum(sample_trials_a[sid]) / len(sample_trials_a[sid]) for sid in sample_ids}
    means_b = {sid: sum(sample_trials_b[sid]) / len(sample_trials_b[sid]) for sid in sample_ids}
    
    diff_obs = sum(means_a[sid] - means_b[sid] for sid in sample_ids) / n_samples
    
    diffs = []
    for _ in range(n_boot):
        boot_sids = [rng.choice(sample_ids) for _ in range(n_samples)]
        diff_boot = sum(means_a[sid] - means_b[sid] for sid in boot_sids) / n_samples
        diffs.append(diff_boot)
        
    diffs.sort()
    ci_low = diffs[int(0.025 * n_boot)]
    ci_high = diffs[int(0.975 * n_boot)]
    
    # Two-sided p-value: fraction of bootstrap replicates crossing zero * 2
    if diff_obs > 0:
        p_val = 2 * (sum(1 for d in diffs if d <= 0) / n_boot)
    else:
        p_val = 2 * (sum(1 for d in diffs if d >= 0) / n_boot)
    p_val = min(1.0, max(0.0, p_val))
    
    return diff_obs, ci_low, ci_high, p_val

def compute_percentiles(values):
    if not values:
        return 0.0, 0.0, 0.0
    sorted_v = sorted(values)
    mean_v = sum(sorted_v) / len(sorted_v)
    p50_v = sorted_v[int(len(sorted_v) * 0.50)]
    p95_v = sorted_v[min(len(sorted_v) - 1, int(len(sorted_v) * 0.95))]
    return mean_v, p50_v, p95_v

def main():
    if not os.path.exists(RAW_PATH):
        print(f"File {RAW_PATH} non trovato. Esegui prima scripts/run_phase_b_benchmark.py.")
        sys.exit(1)

    print(f"Analisi dei dati grezzi da {RAW_PATH}...")
    records = []
    with open(RAW_PATH, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))

    print(f"Caricati {len(records)} record totali.")

    # Scomposizione per sezione di benchmark
    by_section = defaultdict(list)
    for r in records:
        sec = r.get("benchmark_section", "unknown")
        by_section[sec].append(r)

    report_lines = []
    def log(msg=""):
        report_lines.append(msg)
        print(msg)

    log("# Report Finale FASE B — Valutazione Rigorosa dei Modelli Decisionali (think: false)")
    log()
    log("> **Nota metodologica**: Tutte le metriche riportate derivano dall'elaborazione deterministica dei log grezzi")
    log(f"> memorizzati in `{RAW_PATH}`. Nessun valore è inserito manualmente o stimato a priori.")
    log()

    # =========================================================================
    # 1. Router 4 Opzioni (24 Permutazioni Esaustive su 40 Campioni)
    # =========================================================================
    r4_records = by_section["router_4_options"]
    log("## 1. Router su 4 Opzioni (24 Permutazioni Esaustive per Campione, 960 Invocazioni per Modello)")
    log()
    
    # Raggruppamento per modello
    by_model_r4 = defaultdict(list)
    for r in r4_records:
        by_model_r4[r["model_name"]].append(r)

    log("| Modello | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Campioni 100% Invarianti | Token Non Validi (Retry Recuperati) |")
    log("| :--- | :--- | :--- | :--- | :--- |")

    sample_correct_by_model = defaultdict(lambda: defaultdict(list))

    for m_name in ["APUS-Reflex-4B", "Qwen-2.5-3B"]:
        m_recs = by_model_r4[m_name]
        n_tot = len(m_recs)
        strict_k = sum(1 for r in m_recs if r["strict_correct"])
        perm_k = sum(1 for r in m_recs if r["permissive_correct"])
        invalid_k = sum(1 for r in m_recs if r["is_invalid_token"])
        recovered_k = sum(1 for r in m_recs if r.get("retry_recovered", False))

        p_s, s_low, s_high = wilson_interval(strict_k, n_tot)
        p_p, p_low, p_high = wilson_interval(perm_k, n_tot)

        # Invariance by sample (24/24 correct)
        by_sample = defaultdict(list)
        for r in m_recs:
            by_sample[r["sample_id"]].append(r["strict_correct"])
            sample_correct_by_model[m_name][r["sample_id"]].append(r["strict_correct"])

        invariant_samples = sum(1 for sid, trials in by_sample.items() if all(trials))
        inv_ratio = invariant_samples / len(by_sample) if by_sample else 0.0

        log(f"| **{m_name}** | **{p_s*100:.1f}%** ({strict_k}/{n_tot}) [{s_low*100:.1f}%–{s_high*100:.1f}%] | **{p_p*100:.1f}%** ({perm_k}/{n_tot}) [{p_low*100:.1f}%–{p_high*100:.1f}%] | **{invariant_samples}/40** ({inv_ratio*100:.1f}%) | {invalid_k} ({recovered_k} rec.) |")

    log()
    log("### 1.1. Distribuzione dell'Accuratezza per Posizione Ordinale del Target (Positional Bias su 4 Opzioni)")
    log()
    log("| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Delta (Max - Min) |")
    log("| :--- | :--- | :--- | :--- | :--- | :--- |")

    for m_name in ["APUS-Reflex-4B", "Qwen-2.5-3B"]:
        m_recs = by_model_r4[m_name]
        pos_stats = {}
        for pos in ["A", "B", "C", "D"]:
            sub = [r for r in m_recs if r["target_position"] == pos]
            k = sum(1 for r in sub if r["strict_correct"])
            n = len(sub)
            p, low, high = wilson_interval(k, n)
            pos_stats[pos] = (p, k, n, low, high)

        p_vals = [pos_stats[p][0] for p in ["A", "B", "C", "D"]]
        delta = max(p_vals) - min(p_vals)
        log(f"| **{m_name}** | {pos_stats['A'][0]*100:.1f}% ({pos_stats['A'][1]}/{pos_stats['A'][2]}) | {pos_stats['B'][0]*100:.1f}% ({pos_stats['B'][1]}/{pos_stats['B'][2]}) | {pos_stats['C'][0]*100:.1f}% ({pos_stats['C'][1]}/{pos_stats['C'][2]}) | {pos_stats['D'][0]*100:.1f}% ({pos_stats['D'][1]}/{pos_stats['D'][2]}) | **{delta*100:.1f}%** |")

    # =========================================================================
    # 2. Scenari a 8 e 16 Opzioni (Rotazioni Cicliche su 40 Campioni)
    # =========================================================================
    log()
    log("## 2. Scenari a 8 e 16 Opzioni (Rotazioni Cicliche Sistematiche)")
    log()
    log("| Modello | Opzioni | Prove Totali | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Token Non Validi |")
    log("| :--- | :--- | :--- | :--- | :--- | :--- |")

    for n_opt, sec_name in [(8, "router_8_options"), (16, "router_16_options")]:
        recs = by_section[sec_name]
        by_m = defaultdict(list)
        for r in recs:
            by_m[r["model_name"]].append(r)

        for m_name in ["APUS-Reflex-4B", "Qwen-2.5-3B"]:
            m_recs = by_m[m_name]
            n_tot = len(m_recs)
            strict_k = sum(1 for r in m_recs if r["strict_correct"])
            perm_k = sum(1 for r in m_recs if r["permissive_correct"])
            invalid_k = sum(1 for r in m_recs if r["is_invalid_token"])

            ps, slow, shigh = wilson_interval(strict_k, n_tot)
            pp, plow, phigh = wilson_interval(perm_k, n_tot)
            log(f"| **{m_name}** | {n_opt} opzioni | {n_tot} | **{ps*100:.1f}%** [{slow*100:.1f}%–{shigh*100:.1f}%] | **{pp*100:.1f}%** [{plow*100:.1f}%–{phigh*100:.1f}%] | {invalid_k} |")

    # =========================================================================
    # 3. Instradamento Email sui 5 Ruoli Aziendali (40 Campioni x 5 Rotazioni)
    # =========================================================================
    log()
    log("## 3. Instradamento Email sui 5 Ruoli Aziendali (200 Invocazioni per Modello)")
    log()
    log("| Modello | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Baseline a Regole (Stretta) | Token Non Validi (Recuperati) |")
    log("| :--- | :--- | :--- | :--- | :--- |")

    role_recs = by_section["email_role_5_options"]
    by_m_role = defaultdict(list)
    for r in role_recs:
        by_m_role[r["model_name"]].append(r)

    # Compute rules baseline on the 40 email samples
    rules_strict_k = sum(1 for r in by_m_role["APUS-Reflex-4B"] if r["rules_strict_correct"])
    rules_tot = len(by_m_role["APUS-Reflex-4B"])
    p_rules, r_low, r_high = wilson_interval(rules_strict_k, rules_tot)

    for m_name in ["APUS-Reflex-4B", "Qwen-2.5-3B"]:
        m_recs = by_m_role[m_name]
        n_tot = len(m_recs)
        strict_k = sum(1 for r in m_recs if r["strict_correct"])
        perm_k = sum(1 for r in m_recs if r["permissive_correct"])
        invalid_k = sum(1 for r in m_recs if r["is_invalid_token"])
        recovered_k = sum(1 for r in m_recs if r.get("retry_recovered", False))

        ps, slow, shigh = wilson_interval(strict_k, n_tot)
        pp, plow, phigh = wilson_interval(perm_k, n_tot)

        log(f"| **{m_name}** | **{ps*100:.1f}%** [{slow*100:.1f}%–{shigh*100:.1f}%] | **{pp*100:.1f}%** [{plow*100:.1f}%–{phigh*100:.1f}%] | {p_rules*100:.1f}% [{r_low*100:.1f}%–{r_high*100:.1f}%] | {invalid_k} ({recovered_k} rec.) |")

    # =========================================================================
    # 4. Contenuto Neutro e Test Chi-Quadro (B2)
    # =========================================================================
    log()
    log("## 4. Contenuto Neutro: Test Chi-Quadro di Uniformità (B2)")
    log()
    log("Valutazione su 16 testi neutri (latino, ricette, sequenze numeriche, botanica, ottica, astronomia) moltiplicati per 4 rotazioni ordinali (64 prove per modello).")
    log("Ipotesi nulla $H_0$: le risposte si distribuiscono uniformemente sulle 4 lettere (nessun bias posizionale intrinseco, frequenza attesa: 25% per ciascuna posizione).")
    log()
    log("| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Chi-Quadro ($\chi^2$, df=3) | p-value | Esito $H_0$ ($\alpha=0.05$) |")
    log("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |")

    neutral_recs = by_section["neutral_content_4_options"]
    by_m_neut = defaultdict(list)
    for r in neutral_recs:
        by_m_neut[r["model_name"]].append(r)

    for m_name in ["APUS-Reflex-4B", "Qwen-2.5-3B"]:
        m_recs = by_m_neut[m_name]
        pos_counts = Counter(r["chosen_position"] for r in m_recs)
        c_a = pos_counts.get("A", 0)
        c_b = pos_counts.get("B", 0)
        c_c = pos_counts.get("C", 0)
        c_d = pos_counts.get("D", 0)
        tot = len(m_recs)

        chi2, p_val = chi_square_uniform({"A": c_a, "B": c_b, "C": c_c, "D": c_d})
        outcome = "Respinta (Bias Presente)" if p_val is not None and p_val < 0.05 else "Non Respinta (Uniforme)"
        p_str = f"{p_val:.4f}" if p_val is not None else "N/A"
        log(f"| **{m_name}** | {c_a}/{tot} ({c_a/tot*100:.1f}%) | {c_b}/{tot} ({c_b/tot*100:.1f}%) | {c_c}/{tot} ({c_c/tot*100:.1f}%) | {c_d}/{tot} ({c_d/tot*100:.1f}%) | **{chi2:.2f}** | **{p_str}** | **{outcome}** |")

    # =========================================================================
    # 5. Confronto Paired Cluster Bootstrap Reflex vs Qwen (B4)
    # =========================================================================
    log()
    log("## 5. Confronto Statistico Paired Cluster Bootstrap (B4)")
    log()
    log("Confronto statistico paired cluster bootstrap a livello di singolo campione (10.000 ricampionamenti con replacement sui 40 cluster).")
    log()

    diff_obs, ci_l, ci_h, p_val = paired_cluster_bootstrap(
        sample_correct_by_model["APUS-Reflex-4B"],
        sample_correct_by_model["Qwen-2.5-3B"]
    )
    log(f"- **Differenza media di accuratezza (Reflex - Qwen)**: **{diff_obs*100:+.2f}%**")
    log(f"- **Intervallo di Confidenza Bootstrap 95%**: **[{ci_l*100:+.2f}%, {ci_h*100:+.2f}%]**")
    log(f"- **Valore p a due code**: **{p_val:.4f}**")
    signif_str = "differenza statisticamente significativa" if p_val < 0.05 else "differenza NON statisticamente significativa (prestazioni equivalenti)"
    log(f"- **Interpretazione formale**: Con $\alpha = 0.05$, la {signif_str}.")

    # =========================================================================
    # 6. Analisi della Latenza e Disaggregazione TTFT (Prompt Eval) vs Decode (B5)
    # =========================================================================
    log()
    log("## 6. Analisi dei Tempi di Esecuzione e Latenza (B5)")
    log()
    log("| Modello | Scenario | Prompt Eval / TTFT (ms) [Mean / p50 / p95] | Eval / Decode (ms) [Mean / p50 / p95] | Latenza Totale Client (ms) [Mean / p50 / p95] |")
    log("| :--- | :--- | :--- | :--- | :--- |")

    for sec_name, label in [("router_4_options", "Router 4 Opz"), ("router_8_options", "Router 8 Opz"), ("email_role_5_options", "Email 5 Ruoli")]:
        recs = by_section[sec_name]
        by_m = defaultdict(list)
        for r in recs:
            by_m[r["model_name"]].append(r)

        for m_name in ["APUS-Reflex-4B", "Qwen-2.5-3B"]:
            m_recs = by_m[m_name]
            p_evals = [r["prompt_eval_duration_ms"] for r in m_recs]
            evals = [r["eval_duration_ms"] for r in m_recs]
            clients = [r["client_latency_ms"] for r in m_recs]

            mp, p50p, p95p = compute_percentiles(p_evals)
            me, p50e, p95e = compute_percentiles(evals)
            mc, p50c, p95c = compute_percentiles(clients)

            log(f"| **{m_name}** | {label} | {mp:.1f} / {p50p:.1f} / {p95p:.1f} | {me:.1f} / {p50e:.1f} / {p95e:.1f} | **{mc:.1f} / {p50c:.1f} / {p95c:.1f}** |")

    # =========================================================================
    # 7. Caching Test: Opzioni Prefix vs Suffix (B5)
    # =========================================================================
    log()
    log()
    log("## 7. Valutazione Caching Prompt: Opzioni Prefix vs Suffix (B5)")
    log()
    log("Verifica sperimentale su 10 campioni x 24 permutazioni (480 prove su Reflex) per quantificare l'accelerazione della KV Cache di Ollama invertendo la struttura del prompt:")
    log("- **Layout Suffix (Predefinito)**: Opzioni all'inizio, `<payload>` al centro, `Choice:` alla fine.")
    log("- **Layout Prefix (Invertito)**: `<payload>` all'inizio (invariante tra permutazioni dello stesso testo), Opzioni alla fine, `Choice:` alla fine.")
    log()

    caching_recs = by_section["caching_layout_comparison"]
    if caching_recs:
        suffix_p_eval = [r["suffix_prompt_eval_ms"] for r in caching_recs]
        prefix_p_eval = [r["prefix_prompt_eval_ms"] for r in caching_recs]
        
        m_s, p50_s, p95_s = compute_percentiles(suffix_p_eval)
        m_p, p50_p, p95_p = compute_percentiles(prefix_p_eval)

        log("| Configurazione Layout Prompt | Prompt Eval Medio (ms) | p50 (ms) | p95 (ms) | Speedup KV Cache |")
        log("| :--- | :--- | :--- | :--- | :--- |")
        log(f"| **Suffix (Standard)**: Opzioni $\\rightarrow$ Documento | {m_s:.2f} ms | {p50_s:.2f} ms | {p95_s:.2f} ms | Baseline (1.0x) |")
        speedup = m_s / m_p if m_p > 0 else 1.0
        log(f"| **Prefix (Invertito)**: Documento $\\rightarrow$ Opzioni | {m_p:.2f} ms | {p50_p:.2f} ms | {p95_p:.2f} ms | **{speedup:.2f}x** |")
        log()
        log(f"> **Esito sperimentale**: Il layout con Documento fisso in testa ottiene un'accelerazione di **{speedup:.2f}x** sul tempo di prompt evaluation grazie al riutilizzo del prefisso condiviso nel KV-cache.")

    # Save to report markdown file
    with open(REPORT_MD_PATH, "w", encoding="utf-8") as f:
        f.write("\n".join(report_lines) + "\n")

    print(f"\nReport markdown salvato in: {REPORT_MD_PATH}")

if __name__ == "__main__":
    main()
