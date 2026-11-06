#!/usr/bin/env python3
import json
import math
import os
import re
import sys
import time
import urllib.request

OLLAMA_BASE_URL = "http://localhost:11434"
MODEL_NAME = "hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M"

CANDIDATES = [
    {"code": "FATTURA", "label": "FATTURA", "description": "commercial invoices, tax receipts, VAT/IVA numbers, IBAN, amounts"},
    {"code": "EMAIL", "label": "EMAIL", "description": "correspondence, letters, communications with sender/recipient/subject, formal emails"},
    {"code": "ANAGRAFICA", "label": "ANAGRAFICA", "description": "identity cards, driver licenses, passports, Italian codice fiscale, personal documents"},
    {"code": "ALTRO", "label": "ALTRO", "description": "any document that does not clearly belong to FATTURA, EMAIL, or ANAGRAFICA"}
]

STANDARD_ROUTER_SAMPLES = [
    {
        "id": "RF_FAT_1",
        "text": "Fattura n. 2026/A del 15/03/2026. Fornitore: Tech Solutions S.r.l. P.IVA 01234567890. Totale imponibile: € 1.500,00, IVA 22%: € 330,00. Totale fattura: € 1.830,00. IBAN: IT60X0542811101000000123456.",
        "target": "FATTURA"
    },
    {
        "id": "RF_FAT_2",
        "text": "Parcella professionale studio legale. Spett.le Committente, si richiede il pagamento per prestazioni legali. Compensi: 800 EUR, Cassa Previdenza 4%, Ritenuta acconto 20%. Coordinate bonifico IT02L1234512345000000012345.",
        "target": "FATTURA"
    },
    {
        "id": "RF_EML_1",
        "text": "Da: claudia.rossi@partner.com\nA: direzione@azienda.it\nOggetto: Richiesta incontro strategico Q3\nGentile Dott. Vena,\nvorremmo concordare una riunione di allineamento per la prossima settimana.\nCordiali saluti,\nClaudia Rossi",
        "target": "EMAIL"
    },
    {
        "id": "RF_EML_2",
        "text": "Mittente: assistenza@cliente.it\nDestinatario: support@azienda.it\nOggetto: Sollecito risposta ticket #8841\nSalve, siamo ancora in attesa di un vostro riscontro circa l'anomalia segnalata ieri.\nGrazie e buona giornata.",
        "target": "EMAIL"
    },
    {
        "id": "RF_ANA_1",
        "text": "REPUBBLICA ITALIANA - CARTA D'IDENTITA N. CA98765ZZ\nCognome: BIANCHI\nNome: ALESSANDRO\nNato a: ROMA (RM) il 12/04/1985\nSesso: M - Statura: 180\nCodice Fiscale: BNCALN85D12H501Y\nComune di Roma - Scadenza: 12/04/2035",
        "target": "ANAGRAFICA"
    },
    {
        "id": "RF_ANA_2",
        "text": "PATENTE DI GUIDA - REPUBBLICA ITALIANA\n1. VERDI 2. MARCO\n3. 05/11/1990 ROMA\n4a. 10/01/2021 4b. 10/01/2031 4c. UCO MIT\n5. U12345678X\nCodice Fiscale: VRDMRC90S05H501A",
        "target": "ANAGRAFICA"
    },
    {
        "id": "RF_ALT_1",
        "text": "Ricetta originale spaghetti alla carbonara: cuocere la pasta al dente in abbondante acqua salata. Nel frattempo rosolare il guanciale in padella senza olio. Sbattere i tuorli con pecorino romano e pepe nero macinato fresco.",
        "target": "ALTRO"
    },
    {
        "id": "RF_ALT_2",
        "text": "Comunicato della Protezione Civile: allerta meteo gialla per precipitazioni diffuse e vento forte sulle zone costiere a partire dalla mezzanotte di domani. Si raccomanda la massima prudenza negli spostamenti.",
        "target": "ALTRO"
    }
]

def permute_helper(arr, k, result):
    for i in range(k, len(arr)):
        arr[i], arr[k] = arr[k], arr[i]
        permute_helper(arr, k + 1, result)
        arr[i], arr[k] = arr[k], arr[i]
    if k == len(arr) - 1:
        result.append(list(arr))

def get_all_permutations():
    result = []
    permute_helper([0, 1, 2, 3], 0, result)
    return result

def build_prompt(sample_text, perm_indices):
    sb = ["Given the following document text, classify it into exactly one of the four categories below:"]
    for i, idx in enumerate(perm_indices):
        letter = chr(ord('A') + i)
        c = CANDIDATES[idx]
        sb.append(f"{letter}: {c['label']} ({c['description']})")
    sb.append("\nDocument:\n<payload>")
    sb.append(sample_text)
    sb.append("</payload>\n")
    sb.append("Respond ONLY with the single uppercase letter of your choice (A, B, C, or D).\nChoice:")
    return "\n".join(sb)

def extract_first_choice_letter(text):
    if not text:
        return None
    clean = text.strip()
    m = re.search(r'\b([A-D])\b', clean)
    if m:
        return m.group(1)
    if clean and clean[0] in "ABCD":
        return clean[0]
    return None

def compute_margin(logprob_item):
    if not logprob_item or "top_logprobs" not in logprob_item:
        return 0.0
    allowed = {"A", "B", "C", "D"}
    # Extract logprobs for allowed letters
    scores = {}
    for entry in logprob_item["top_logprobs"]:
        tok = entry.get("token", "").strip().upper()
        lp = entry.get("logprob", -99.0)
        if tok in allowed and tok not in scores:
            scores[tok] = lp
    if len(scores) >= 2:
        sorted_lps = sorted(scores.values(), reverse=True)
        return sorted_lps[0] - sorted_lps[1]
    return 0.0

def call_ollama(url, payload):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    t0 = time.time()
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    t1 = time.time()
    client_latency_ms = int((t1 - t0) * 1000)
    return data, client_latency_ms

def main():
    out_dir = os.path.join(os.getcwd(), "target", "phase_a")
    os.makedirs(out_dir, exist_ok=True)
    out_file = os.path.join(out_dir, "phase_a_step2_raw.jsonl")

    all_permutations = get_all_permutations()
    assert len(all_permutations) == 24

    configs = [
        {"name": "chat_default_np1", "endpoint": "/api/chat", "np": 1, "think": None, "use_messages": True},
        {"name": "generate_np1", "endpoint": "/api/generate", "np": 1, "think": None, "use_messages": False},
        {"name": "chat_think_false_np1", "endpoint": "/api/chat", "np": 1, "think": False, "use_messages": True},
        {"name": "chat_think_false_np32", "endpoint": "/api/chat", "np": 32, "think": False, "use_messages": True},
    ]

    total_runs = len(configs) * len(STANDARD_ROUTER_SAMPLES) * len(all_permutations)
    print(f"Starting Phase A Step 2 evaluation: {len(configs)} configs x {len(STANDARD_ROUTER_SAMPLES)} samples x 24 perms = {total_runs} total runs...")

    records = []
    run_count = 0
    start_time = time.time()

    with open(out_file, "w", encoding="utf-8") as out_f:
        for cfg in configs:
            print(f"\n>>> Running Configuration: {cfg['name']} ({cfg['endpoint']}, np={cfg['np']}, think={cfg['think']})")
            cfg_start = time.time()
            for s_idx, sample in enumerate(STANDARD_ROUTER_SAMPLES):
                for p_idx, perm in enumerate(all_permutations):
                    run_count += 1
                    prompt = build_prompt(sample["text"], perm)

                    expected_letter = None
                    expected_pos = None
                    for l_idx, c_idx in enumerate(perm):
                        if CANDIDATES[c_idx]["code"] == sample["target"]:
                            expected_letter = chr(ord('A') + l_idx)
                            expected_pos = expected_letter
                            break

                    perm_map = {chr(ord('A') + i): CANDIDATES[idx]["code"] for i, idx in enumerate(perm)}

                    url = OLLAMA_BASE_URL + cfg["endpoint"]
                    payload = {
                        "model": MODEL_NAME,
                        "stream": False,
                        "logprobs": True,
                        "top_logprobs": 5,
                        "options": {
                            "temperature": 0.0,
                            "num_predict": cfg["np"],
                            "repeat_penalty": 1.0
                        },
                        "keep_alive": "60m"
                    }
                    if cfg["use_messages"]:
                        payload["messages"] = [{"role": "user", "content": prompt}]
                        if cfg["think"] is not None:
                            payload["think"] = cfg["think"]
                    else:
                        payload["prompt"] = prompt

                    raw_resp, client_lat = call_ollama(url, payload)

                    # Determine letter
                    content_val = ""
                    thinking_val = ""
                    response_val = ""
                    where = "none"
                    extracted = None

                    if "message" in raw_resp:
                        content_val = raw_resp["message"].get("content") or ""
                        thinking_val = raw_resp["message"].get("thinking") or ""
                        if content_val and any(c in content_val for c in "ABCD"):
                            where = "message.content"
                            extracted = extract_first_choice_letter(content_val)
                        elif thinking_val and any(c in thinking_val for c in "ABCD"):
                            where = "message.thinking"
                            extracted = extract_first_choice_letter(thinking_val)
                        else:
                            extracted = extract_first_choice_letter(content_val or thinking_val)
                    else:
                        response_val = raw_resp.get("response") or ""
                        if response_val and any(c in response_val for c in "ABCD"):
                            where = "response"
                            extracted = extract_first_choice_letter(response_val)
                        else:
                            extracted = extract_first_choice_letter(response_val)

                    correct = (extracted == expected_letter) if extracted else False
                    chosen_pos = extracted if (extracted and extracted in "ABCD") else "INVALID"
                    chosen_code = perm_map.get(chosen_pos, "UNKNOWN")

                    # Margin from logprobs
                    margin = 0.0
                    lps = raw_resp.get("logprobs")
                    if lps and len(lps) > 0:
                        margin = compute_margin(lps[0])

                    rec = {
                        "config_name": cfg["name"],
                        "endpoint": cfg["endpoint"],
                        "num_predict": cfg["np"],
                        "think_param": cfg["think"],
                        "sample_id": sample["id"],
                        "target_code": sample["target"],
                        "permutation_index": p_idx,
                        "permutation_mapping": perm_map,
                        "target_position": expected_pos,
                        "chosen_position": chosen_pos,
                        "chosen_code": chosen_code,
                        "expected_letter": expected_letter,
                        "extracted_letter": extracted,
                        "where_letter_appears": where,
                        "raw_content": content_val,
                        "raw_thinking": thinking_val,
                        "raw_response": response_val,
                        "correct": correct,
                        "margin": margin,
                        "done_reason": raw_resp.get("done_reason"),
                        "eval_count": raw_resp.get("eval_count"),
                        "prompt_eval_count": raw_resp.get("prompt_eval_count"),
                        "prompt_eval_cached_count": raw_resp.get("prompt_eval_cached_count"),
                        "prompt_eval_duration_ms": (raw_resp.get("prompt_eval_duration") or 0) / 1_000_000,
                        "eval_duration_ms": (raw_resp.get("eval_duration") or 0) / 1_000_000,
                        "total_duration_ms": (raw_resp.get("total_duration") or 0) / 1_000_000,
                        "client_latency_ms": client_lat,
                        "raw_response_full": raw_resp
                    }
                    out_f.write(json.dumps(rec, ensure_ascii=False) + "\n")
                    out_f.flush()

                print(f"  Finished sample {sample['id']} ({s_idx+1}/8)")
            print(f"Finished {cfg['name']} in {time.time() - cfg_start:.1f}s")

    print(f"\nAll {total_runs} trials completed in {time.time() - start_time:.1f}s! Data saved to {out_file}")

if __name__ == "__main__":
    main()
