#!/usr/bin/env python3
import json
import math
import os
import sys

def wilson_score(k, n, z=1.96):
    if n == 0:
        return 0.0, 0.0, 0.0
    p = k / n
    denominator = 1 + z**2 / n
    centre_adjusted_probability = p + z**2 / (2 * n)
    adjusted_standard_deviation = math.sqrt((p * (1 - p) + z**2 / (4 * n)) / n)
    lower_bound = (centre_adjusted_probability - z * adjusted_standard_deviation) / denominator
    upper_bound = (centre_adjusted_probability + z * adjusted_standard_deviation) / denominator
    return p, max(0.0, lower_bound), min(1.0, upper_bound)

def analyze_step1(jsonl_path):
    print("=" * 80)
    print("ANALISI FASE A - STEP 1 (3 Campioni x 3 Permutazioni across Configs)")
    print("=" * 80)
    records = []
    with open(jsonl_path, "r", encoding="utf-8") as f:
        for line in f:
            records.append(json.loads(line))

    # Group by config
    by_config = {}
    for r in records:
        by_config.computeIfAbsent if False else by_config.setdefault(r["config_name"], []).append(r)

    print(f"{'Configurazione':<32} | {'Dove Compare':<18} | {'eval_cnt':<8} | {'done_reason':<11} | {'Acc (k/9)':<10}")
    print("-" * 88)
    for cfg_name, rows in by_config.items():
        where_counts = {}
        eval_counts = {}
        done_reasons = {}
        correct_cnt = sum(1 for r in rows if r["correct"])
        for r in rows:
            w = r["where_letter_appears"]
            where_counts[w] = where_counts.get(w, 0) + 1
            ec = r["eval_count"]
            eval_counts[ec] = eval_counts.get(ec, 0) + 1
            dr = r["done_reason"]
            done_reasons[dr] = done_reasons.get(dr, 0) + 1
        
        where_str = ", ".join(f"{k}:{v}" for k, v in where_counts.items())
        ec_str = ", ".join(f"{k}:{v}" for k, v in eval_counts.items())
        dr_str = ", ".join(f"{k}:{v}" for k, v in done_reasons.items())
        print(f"{cfg_name:<32} | {where_str:<18} | {ec_str:<8} | {dr_str:<11} | {correct_cnt}/9 ({correct_cnt/9*100:.1f}%)")

def analyze_step2(jsonl_path):
    print("\n" + "=" * 80)
    print("ANALISI FASE A - STEP 2 (8 Campioni x 24 Permutazioni = 192 Prove per Config)")
    print("=" * 80)
    records = []
    with open(jsonl_path, "r", encoding="utf-8") as f:
        for line in f:
            records.append(json.loads(line))

    by_config = {}
    for r in records:
        by_config.setdefault(r["config_name"], []).append(r)

    for cfg_name, rows in by_config.items():
        total_trials = len(rows)
        correct_trials = sum(1 for r in rows if r["correct"])
        acc_p, acc_lo, acc_hi = wilson_score(correct_trials, total_trials)

        # Accuracy per sample
        by_sample = {}
        for r in rows:
            by_sample.setdefault(r["sample_id"], []).append(r)

        sample_stats = {}
        invariant_samples = 0
        for s_id, s_rows in by_sample.items():
            s_corr = sum(1 for r in s_rows if r["correct"])
            s_tot = len(s_rows)
            sample_stats[s_id] = (s_corr, s_tot, s_corr / s_tot)
            if s_corr == s_tot:
                invariant_samples += 1

        # Accuracy per target position
        by_target_pos = {'A': [], 'B': [], 'C': [], 'D': []}
        for r in rows:
            tp = r.get("target_position")
            if tp in by_target_pos:
                by_target_pos[tp].append(r)

        # Chosen position distribution
        chosen_dist = {'A': 0, 'B': 0, 'C': 0, 'D': 0, 'INVALID': 0}
        for r in rows:
            cp = r.get("chosen_position")
            if cp in chosen_dist:
                chosen_dist[cp] += 1
            else:
                chosen_dist['INVALID'] += 1

        # Latencies
        lats = sorted([r["client_latency_ms"] for r in rows])
        avg_lat = sum(lats) / len(lats) if lats else 0.0
        p50_lat = lats[int(len(lats) * 0.50)] if lats else 0
        p95_lat = lats[int(len(lats) * 0.95)] if lats else 0

        # Eval duration (Ollama internal)
        eval_lats = sorted([r.get("eval_duration_ms", 0.0) for r in rows])
        avg_eval = sum(eval_lats) / len(eval_lats) if eval_lats else 0.0

        print(f"\n>>> CONFIGURAZIONE: {cfg_name}")
        print(f"  Totale prove: {total_trials}")
        print(f"  Accuratezza aggregata: {correct_trials}/{total_trials} ({acc_p*100:.1f}%, 95% Wilson: [{acc_lo*100:.1f}% - {acc_hi*100:.1f}%])")
        print(f"  Campioni 100% invarianti (24/24): {invariant_samples}/{len(by_sample)} ({invariant_samples/len(by_sample)*100:.1f}%)")
        print("  Accuratezza per campione:")
        for s_id, (sc, st, spct) in sample_stats.items():
            print(f"    - {s_id}: {sc}/{st} ({spct*100:.1f}%)")
        print("  Accuratezza per posizione target:")
        for pos in ['A', 'B', 'C', 'D']:
            p_rows = by_target_pos[pos]
            p_corr = sum(1 for r in p_rows if r["correct"])
            p_tot = len(p_rows)
            pp, plo, phi = wilson_score(p_corr, p_tot)
            print(f"    - Posizione {pos}: {p_corr}/{p_tot} ({pp*100:.1f}%, 95% Wilson: [{plo*100:.1f}% - {phi*100:.1f}%])")
        print("  Distribuzione posizioni scelte (frequenza A..D):")
        for pos in ['A', 'B', 'C', 'D', 'INVALID']:
            cnt = chosen_dist[pos]
            print(f"    - Scelta {pos}: {cnt}/{total_trials} ({cnt/total_trials*100:.1f}%)")
        print(f"  Latenza client: media={avg_lat:.1f} ms, p50={p50_lat} ms, p95={p95_lat} ms")
        print(f"  Tempo eval token (Ollama): media={avg_eval:.1f} ms")

def main():
    s1_path = os.path.join("target", "phase_a", "phase_a_step1_raw.jsonl")
    s2_path = os.path.join("target", "phase_a", "phase_a_step2_raw.jsonl")

    if os.path.exists(s1_path):
        analyze_step1(s1_path)
    if os.path.exists(s2_path):
        analyze_step2(s2_path)

if __name__ == "__main__":
    main()
