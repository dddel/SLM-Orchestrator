#!/usr/bin/env python3
"""
scripts/run_phase_b_benchmark.py

Esecutore empirico della FASE B del benchmark decisionale.
Esegue le prove su Ollama salvando ogni risposta grezza in JSONL:
- target/phase_b/phase_b_raw.jsonl

Esegue prima tutti i test su APUS-Reflex-4B e poi su Qwen-2.5-3B per evitare
il costoso swapping continuo dei pesi in VRAM.
"""

import json
import os
import sys
import time
import urllib.request
import re
from itertools import permutations

OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://localhost:11434")
REFLEX_MODEL = "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M"
QWEN_MODEL = "qwen2.5:3b-instruct-q8_0"

DATASET_PATH = os.environ.get("PHASE_B_DATASET", "eval_decision_benchmark/dataset/phase_b_dataset.json" if os.path.exists("eval_decision_benchmark/dataset/phase_b_dataset.json") else "scripts/data/phase_b_dataset.json")
OUTPUT_DIR = os.environ.get("PHASE_B_OUTPUT_DIR", "eval_decision_benchmark/results" if os.path.exists("eval_decision_benchmark/results") else "target/phase_b")
RAW_OUTPUT_PATH = os.path.join(OUTPUT_DIR, "phase_b_raw.jsonl")

def load_dataset():
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

def call_ollama_chat(model, user_prompt, think=None, temperature=0.0, num_predict=1, max_retries=3):
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": user_prompt}],
        "stream": False,
        "keep_alive": "60m",
        "options": {
            "temperature": temperature,
            "num_predict": num_predict,
            "repeat_penalty": 1.0
        }
    }
    if think is not None:
        payload["think"] = think

    req_data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        f"{OLLAMA_URL}/api/chat",
        data=req_data,
        headers={"Content-Type": "application/json"}
    )

    last_exc = None
    for attempt in range(max_retries):
        t0 = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=45) as resp:
                body = json.loads(resp.read().decode("utf-8"))
            client_latency_ms = (time.perf_counter() - t0) * 1000.0
            break
        except Exception as e:
            last_exc = e
            time.sleep(1.5 * (attempt + 1))
    else:
        raise last_exc

    msg = body.get("message", {})
    raw_content = msg.get("content", "")
    raw_thinking = msg.get("thinking", "")
    
    extracted_token = ""
    if raw_content and raw_content.strip():
        extracted_token = raw_content.strip()
    elif raw_thinking and raw_thinking.strip():
        extracted_token = raw_thinking.strip()

    first_letter = ""
    if extracted_token:
        m = re.search(r'\b([A-P])\b', extracted_token.upper())
        if m:
            first_letter = m.group(1)
        elif extracted_token[0].upper() in "ABCDEFGHIJKLMNOP":
            first_letter = extracted_token[0].upper()

    prompt_eval_dur = body.get("prompt_eval_duration", 0) / 1_000_000.0
    eval_dur = body.get("eval_duration", 0) / 1_000_000.0
    total_dur = body.get("total_duration", 0) / 1_000_000.0

    return {
        "raw_response": body,
        "raw_content": raw_content,
        "raw_thinking": raw_thinking,
        "extracted_token": extracted_token,
        "first_letter": first_letter,
        "done_reason": body.get("done_reason"),
        "eval_count": body.get("eval_count", 0),
        "prompt_eval_count": body.get("prompt_eval_count", 0),
        "prompt_eval_duration_ms": prompt_eval_dur,
        "eval_duration_ms": eval_dur,
        "total_duration_ms": total_dur,
        "client_latency_ms": client_latency_ms
    }

def build_prompt_standard(candidates, payload_text, num_options, task_type="category"):
    sb = []
    if task_type == "category":
        sb.append(f"Given the following document text, classify it into exactly one of the {num_options} categories below:")
    else:
        sb.append(f"Given the following email text, select the most appropriate department or role to handle it:")

    for i, c in enumerate(candidates):
        letter = chr(ord('A') + i)
        sb.append(f"{letter}: {c['label']} ({c['description']})")

    sb.append("\nDocument:\n<payload>")
    sb.append(payload_text)
    sb.append("</payload>\n")
    
    max_letter = chr(ord('A') + num_options - 1)
    sb.append(f"Respond ONLY with the single uppercase letter of your choice (A..{max_letter}).\nChoice:")
    return "\n".join(sb)

def build_prompt_inverted(candidates, payload_text, num_options, task_type="category"):
    sb = []
    sb.append("Document:\n<payload>")
    sb.append(payload_text)
    sb.append("</payload>\n")
    
    if task_type == "category":
        sb.append(f"Classify the document above into exactly one of the {num_options} categories below:")
    else:
        sb.append(f"Select the most appropriate role to handle the email above:")

    for i, c in enumerate(candidates):
        letter = chr(ord('A') + i)
        sb.append(f"{letter}: {c['label']} ({c['description']})")

    max_letter = chr(ord('A') + num_options - 1)
    sb.append(f"\nRespond ONLY with the single uppercase letter of your choice (A..{max_letter}).\nChoice:")
    return "\n".join(sb)

def run_rules_baseline_router(text):
    clean = text.lower()
    if any(k in clean for k in ["fattura", "imponibile", "iva 22%", "iban", "totale dovuto", "quietanza", "parcella pro-forma"]):
        return "FATTURA"
    if any(k in clean for k in ["mittente:", "da:", "to:", "oggetto:", "subject:", "cordiali saluti", "gentile"]):
        return "EMAIL"
    if any(k in clean for k in ["carta d'identità", "codice fiscale", "nato a", "residente in", "stato civile", "cie n."]):
        return "ANAGRAFICA"
    return "ALTRO"

def run_rules_baseline_role(text):
    clean = text.lower()
    if any(k in clean for k in ["fattura", "bonifico", "pagamento", "iban", "contabilità", "rimborso", "spese", "insoluto"]):
        return "amministrazione"
    if any(k in clean for k in ["curriculum", "candidatura", "colloquio", "ferie", "busta paga", "assunzione", "stage"]):
        return "risorse_umane"
    if any(k in clean for k in ["server", "cloud", "firewall", "vpn", "backup", "password", "ticket", "malware", "crash"]):
        return "reparto_tecnico"
    if any(k in clean for k in ["contratto", "accordo", "clausola", "nda", "diffida", "legale", "avvocato", "dpo", "gdpr"]):
        return "legale"
    return "direzione"

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    dataset = load_dataset()

    print("================================================================================")
    print("AVVIO ESECUZIONE BENCHMARK FASE B")
    print(f"Ollama URL: {OLLAMA_URL}")
    print(f"Modello Reflex: {REFLEX_MODEL} (think: false)")
    print(f"Modello Baseline: {QWEN_MODEL} (think: null)")
    print(f"Output raw: {RAW_OUTPUT_PATH}")
    print("================================================================================")

    completed_keys = set()
    if os.path.exists(RAW_OUTPUT_PATH):
        with open(RAW_OUTPUT_PATH, "r", encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    try:
                        rec = json.loads(line)
                        sec = rec.get("benchmark_section")
                        m = rec.get("model_name", "APUS-Reflex-4B")
                        sid = rec.get("sample_id")
                        idx = rec.get("permutation_index", rec.get("shift_index", 0))
                        completed_keys.add((sec, m, sid, idx))
                    except Exception:
                        pass
        print(f"Rilevati {len(completed_keys)} record già completati. Ripresa in append...")

    raw_file = open(RAW_OUTPUT_PATH, "a", encoding="utf-8")

    def record_trial(data):
        raw_file.write(json.dumps(data, ensure_ascii=False) + "\n")
        raw_file.flush()

    models_config = [
        {"name": "APUS-Reflex-4B", "model_id": REFLEX_MODEL, "think": False},
        {"name": "Qwen-2.5-3B", "model_id": QWEN_MODEL, "think": None}
    ]

    total_trials = 0

    router_cands_4 = dataset["router_candidates_4"]
    all_perms_4 = list(permutations(range(4))) # 24 permutations
    router_cands_8 = dataset["router_candidates_8"]
    router_cands_16 = dataset["router_candidates_16"]
    email_cands_5 = dataset["email_role_candidates_5"]
    neutral_cands_4 = router_cands_4

    for m in models_config:
        m_name = m["name"]
        print(f"\n=======================================================")
        print(f"INIZIO ESECUZIONE MODELLO: {m_name}")
        print(f"=======================================================")
        t_model_start = time.time()

        # 1. Router 4 Opzioni (40 campioni x 24 perms = 960 prove)
        print(f"\n[{m_name}] 1/5 Router 4 Opzioni (960 prove)...")
        for s_idx, sample in enumerate(dataset["router_samples"]):
            sid = sample["id"]
            primary = sample["primary"]
            admissible = sample["admissible"]
            text = sample["text"]
            rules_pred = run_rules_baseline_router(text)

            for p_idx, perm in enumerate(all_perms_4):
                if ("router_4_options", m["name"], sid, p_idx) in completed_keys:
                    total_trials += 1
                    continue
                ordered_cands = [router_cands_4[i] for i in perm]
                target_pos = None
                for i, c in enumerate(ordered_cands):
                    if c["code"] == primary:
                        target_pos = chr(ord('A') + i)
                        break

                prompt = build_prompt_standard(ordered_cands, text, 4, task_type="category")
                res = call_ollama_chat(
                    model=m["model_id"],
                    user_prompt=prompt,
                    think=m["think"],
                    temperature=0.0,
                    num_predict=1
                )

                chosen_letter = res["first_letter"]
                chosen_code = "UNKNOWN"
                if chosen_letter and 'A' <= chosen_letter <= 'D':
                    c_idx = ord(chosen_letter) - ord('A')
                    chosen_code = ordered_cands[c_idx]["code"]

                strict_correct = (chosen_code == primary)
                permissive_correct = (chosen_code in admissible)
                is_invalid = (chosen_letter not in ["A", "B", "C", "D"])

                retry_recovered = False
                retry_letter = None
                retry_code = None
                if is_invalid:
                    retry_cands = [ordered_cands[(i + 1) % 4] for i in range(4)]
                    retry_prompt = build_prompt_standard(retry_cands, text, 4, task_type="category")
                    retry_res = call_ollama_chat(
                        model=m["model_id"],
                        user_prompt=retry_prompt,
                        think=m["think"],
                        temperature=0.0,
                        num_predict=1
                    )
                    retry_letter = retry_res["first_letter"]
                    if retry_letter in ["A", "B", "C", "D"]:
                        ret_idx = (ord(retry_letter) - ord('A') + 1) % 4
                        retry_code = ordered_cands[ret_idx]["code"]
                        if retry_code == primary:
                            retry_recovered = True

                record_trial({
                    "benchmark_section": "router_4_options",
                    "model_name": m["name"],
                    "model_id": m["model_id"],
                    "think_param": m["think"],
                    "sample_id": sid,
                    "sample_category": sample.get("category", "standard"),
                    "sample_text_length": len(text),
                    "permutation_index": p_idx,
                    "target_code": primary,
                    "admissible_codes": admissible,
                    "target_position": target_pos,
                    "chosen_position": chosen_letter,
                    "chosen_code": chosen_code,
                    "strict_correct": strict_correct,
                    "permissive_correct": permissive_correct,
                    "rules_baseline_pred": rules_pred,
                    "rules_strict_correct": (rules_pred == primary),
                    "rules_permissive_correct": (rules_pred in admissible),
                    "is_invalid_token": is_invalid,
                    "retry_recovered": retry_recovered,
                    "retry_letter": retry_letter,
                    "retry_code": retry_code,
                    "prompt_eval_duration_ms": res["prompt_eval_duration_ms"],
                    "eval_duration_ms": res["eval_duration_ms"],
                    "client_latency_ms": res["client_latency_ms"],
                    "done_reason": res["done_reason"],
                    "eval_count": res["eval_count"]
                })
                total_trials += 1

            if (s_idx + 1) % 10 == 0:
                print(f"  [{m_name}] Campioni 4-opz: {s_idx + 1}/40 ({total_trials} prove, {time.time() - t_model_start:.1f}s)...")

        # 2. Router 8 Opzioni (40 campioni x 8 rotazioni = 320 prove)
        print(f"\n[{m_name}] 2/5 Router 8 Opzioni (320 prove)...")
        for s_idx, sample in enumerate(dataset["router_samples"]):
            sid = sample["id"]
            primary = sample["primary"]
            admissible = sample["admissible"]
            text = sample["text"]

            for shift in range(8):
                if ("router_8_options", m["name"], sid, shift) in completed_keys:
                    total_trials += 1
                    continue
                ordered_cands = [router_cands_8[(i + shift) % 8] for i in range(8)]
                target_pos = None
                for i, c in enumerate(ordered_cands):
                    if c["code"] == primary:
                        target_pos = chr(ord('A') + i)
                        break

                prompt = build_prompt_standard(ordered_cands, text, 8, task_type="category")
                res = call_ollama_chat(
                    model=m["model_id"],
                    user_prompt=prompt,
                    think=m["think"],
                    temperature=0.0,
                    num_predict=1
                )
                chosen_letter = res["first_letter"]
                chosen_code = "UNKNOWN"
                valid_letters = [chr(ord('A') + i) for i in range(8)]
                if chosen_letter in valid_letters:
                    c_idx = ord(chosen_letter) - ord('A')
                    chosen_code = ordered_cands[c_idx]["code"]

                strict_correct = (chosen_code == primary)
                permissive_correct = (chosen_code in admissible)
                is_invalid = (chosen_letter not in valid_letters)

                record_trial({
                    "benchmark_section": "router_8_options",
                    "model_name": m["name"],
                    "model_id": m["model_id"],
                    "sample_id": sid,
                    "shift_index": shift,
                    "target_code": primary,
                    "admissible_codes": admissible,
                    "target_position": target_pos,
                    "chosen_position": chosen_letter,
                    "chosen_code": chosen_code,
                    "strict_correct": strict_correct,
                    "permissive_correct": permissive_correct,
                    "is_invalid_token": is_invalid,
                    "prompt_eval_duration_ms": res["prompt_eval_duration_ms"],
                    "eval_duration_ms": res["eval_duration_ms"],
                    "client_latency_ms": res["client_latency_ms"]
                })
                total_trials += 1

        # 3. Router 16 Opzioni (40 campioni x 8 rotazioni = 320 prove)
        print(f"\n[{m_name}] 3/5 Router 16 Opzioni (320 prove)...")
        for s_idx, sample in enumerate(dataset["router_samples"]):
            sid = sample["id"]
            primary = sample["primary"]
            admissible = sample["admissible"]
            text = sample["text"]

            for shift in range(8):
                if ("router_16_options", m["name"], sid, shift) in completed_keys:
                    total_trials += 1
                    continue
                ordered_cands = [router_cands_16[(i + shift) % 16] for i in range(16)]
                target_pos = None
                for i, c in enumerate(ordered_cands):
                    if c["code"] == primary:
                        target_pos = chr(ord('A') + i)
                        break

                prompt = build_prompt_standard(ordered_cands, text, 16, task_type="category")
                res = call_ollama_chat(
                    model=m["model_id"],
                    user_prompt=prompt,
                    think=m["think"],
                    temperature=0.0,
                    num_predict=1
                )
                chosen_letter = res["first_letter"]
                chosen_code = "UNKNOWN"
                valid_letters = [chr(ord('A') + i) for i in range(16)]
                if chosen_letter in valid_letters:
                    c_idx = ord(chosen_letter) - ord('A')
                    chosen_code = ordered_cands[c_idx]["code"]

                strict_correct = (chosen_code == primary)
                permissive_correct = (chosen_code in admissible)
                is_invalid = (chosen_letter not in valid_letters)

                record_trial({
                    "benchmark_section": "router_16_options",
                    "model_name": m["name"],
                    "model_id": m["model_id"],
                    "sample_id": sid,
                    "shift_index": shift,
                    "target_code": primary,
                    "admissible_codes": admissible,
                    "target_position": target_pos,
                    "chosen_position": chosen_letter,
                    "chosen_code": chosen_code,
                    "strict_correct": strict_correct,
                    "permissive_correct": permissive_correct,
                    "is_invalid_token": is_invalid,
                    "prompt_eval_duration_ms": res["prompt_eval_duration_ms"],
                    "eval_duration_ms": res["eval_duration_ms"],
                    "client_latency_ms": res["client_latency_ms"]
                })
                total_trials += 1

        # 4. Email Role Routing (40 campioni x 5 rotazioni = 200 prove)
        print(f"\n[{m_name}] 4/5 Email Role Routing (200 prove)...")
        for s_idx, sample in enumerate(dataset["email_role_samples"]):
            sid = sample["id"]
            primary = sample["primary"]
            admissible = sample["admissible"]
            text = sample["text"]
            rules_pred = run_rules_baseline_role(text)

            for shift in range(5):
                if ("email_role_5_options", m["name"], sid, shift) in completed_keys:
                    total_trials += 1
                    continue
                ordered_cands = [email_cands_5[(i + shift) % 5] for i in range(5)]
                target_pos = None
                for i, c in enumerate(ordered_cands):
                    if c["code"] == primary:
                        target_pos = chr(ord('A') + i)
                        break

                prompt = build_prompt_standard(ordered_cands, text, 5, task_type="role")
                res = call_ollama_chat(
                    model=m["model_id"],
                    user_prompt=prompt,
                    think=m["think"],
                    temperature=0.0,
                    num_predict=1
                )
                chosen_letter = res["first_letter"]
                chosen_code = "UNKNOWN"
                valid_letters = [chr(ord('A') + i) for i in range(5)]
                if chosen_letter in valid_letters:
                    c_idx = ord(chosen_letter) - ord('A')
                    chosen_code = ordered_cands[c_idx]["code"]

                strict_correct = (chosen_code == primary)
                permissive_correct = (chosen_code in admissible)
                is_invalid = (chosen_letter not in valid_letters)

                retry_recovered = False
                retry_letter = None
                retry_code = None
                if is_invalid:
                    retry_cands = [ordered_cands[(i + 1) % 5] for i in range(5)]
                    retry_prompt = build_prompt_standard(retry_cands, text, 5, task_type="role")
                    retry_res = call_ollama_chat(
                        model=m["model_id"],
                        user_prompt=retry_prompt,
                        think=m["think"],
                        temperature=0.0,
                        num_predict=1
                    )
                    retry_letter = retry_res["first_letter"]
                    if retry_letter in valid_letters:
                        ret_idx = (ord(retry_letter) - ord('A') + 1) % 5
                        retry_code = ordered_cands[ret_idx]["code"]
                        if retry_code == primary:
                            retry_recovered = True

                record_trial({
                    "benchmark_section": "email_role_5_options",
                    "model_name": m["name"],
                    "model_id": m["model_id"],
                    "sample_id": sid,
                    "shift_index": shift,
                    "target_code": primary,
                    "admissible_codes": admissible,
                    "target_position": target_pos,
                    "chosen_position": chosen_letter,
                    "chosen_code": chosen_code,
                    "strict_correct": strict_correct,
                    "permissive_correct": permissive_correct,
                    "rules_baseline_pred": rules_pred,
                    "rules_strict_correct": (rules_pred == primary),
                    "rules_permissive_correct": (rules_pred in admissible),
                    "is_invalid_token": is_invalid,
                    "retry_recovered": retry_recovered,
                    "prompt_eval_duration_ms": res["prompt_eval_duration_ms"],
                    "eval_duration_ms": res["eval_duration_ms"],
                    "client_latency_ms": res["client_latency_ms"]
                })
                total_trials += 1

        # 5. Contenuto Neutro (16 campioni x 4 rotazioni = 64 prove)
        print(f"\n[{m_name}] 5/5 Contenuto Neutro (64 prove)...")
        for s_idx, sample in enumerate(dataset["neutral_samples"]):
            sid = sample["id"]
            text = sample["text"]

            for shift in range(4):
                if ("neutral_content_4_options", m["name"], sid, shift) in completed_keys:
                    total_trials += 1
                    continue
                ordered_cands = [neutral_cands_4[(i + shift) % 4] for i in range(4)]
                prompt = build_prompt_standard(ordered_cands, text, 4, task_type="category")
                res = call_ollama_chat(
                    model=m["model_id"],
                    user_prompt=prompt,
                    think=m["think"],
                    temperature=0.0,
                    num_predict=1
                )
                chosen_letter = res["first_letter"]

                record_trial({
                    "benchmark_section": "neutral_content_4_options",
                    "model_name": m["name"],
                    "model_id": m["model_id"],
                    "sample_id": sid,
                    "shift_index": shift,
                    "chosen_position": chosen_letter,
                    "prompt_eval_duration_ms": res["prompt_eval_duration_ms"],
                    "eval_duration_ms": res["eval_duration_ms"],
                    "client_latency_ms": res["client_latency_ms"]
                })
                total_trials += 1

        print(f"Modello {m_name} completato in {time.time() - t_model_start:.1f}s.")

    # -------------------------------------------------------------------------
    # 6. Test Caching: Suffix vs Prefix su Reflex (10 campioni x 24 perms = 240 prove x 2)
    # -------------------------------------------------------------------------
    print(f"\n=======================================================")
    print("TEST CACHING PROMPT: Suffix vs Prefix su Reflex...")
    print(f"=======================================================")
    caching_samples = dataset["router_samples"][:10]
    
    for s_idx, sample in enumerate(caching_samples):
        sid = sample["id"]
        text = sample["text"]

        for p_idx, perm in enumerate(all_perms_4):
            if ("caching_layout_comparison", "APUS-Reflex-4B", sid, p_idx) in completed_keys:
                total_trials += 2
                continue
            ordered_cands = [router_cands_4[i] for i in perm]
            
            prompt_suffix = build_prompt_standard(ordered_cands, text, 4, task_type="category")
            res_suffix = call_ollama_chat(
                model=REFLEX_MODEL,
                user_prompt=prompt_suffix,
                think=False,
                temperature=0.0,
                num_predict=1
            )

            prompt_prefix = build_prompt_inverted(ordered_cands, text, 4, task_type="category")
            res_prefix = call_ollama_chat(
                model=REFLEX_MODEL,
                user_prompt=prompt_prefix,
                think=False,
                temperature=0.0,
                num_predict=1
            )

            record_trial({
                "benchmark_section": "caching_layout_comparison",
                "sample_id": sid,
                "permutation_index": p_idx,
                "suffix_prompt_eval_ms": res_suffix["prompt_eval_duration_ms"],
                "suffix_eval_ms": res_suffix["eval_duration_ms"],
                "suffix_client_latency_ms": res_suffix["client_latency_ms"],
                "prefix_prompt_eval_ms": res_prefix["prompt_eval_duration_ms"],
                "prefix_eval_ms": res_prefix["eval_duration_ms"],
                "prefix_client_latency_ms": res_prefix["client_latency_ms"]
            })
            total_trials += 2

    raw_file.close()
    print("================================================================================")
    print(f"BENCHMARK COMPLETATO CON SUCCESSO: {total_trials} prove totali registrate.")
    print(f"File salvato: {RAW_OUTPUT_PATH}")
    print("================================================================================")

if __name__ == "__main__":
    main()
