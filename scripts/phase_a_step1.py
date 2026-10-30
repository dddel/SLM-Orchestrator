#!/usr/bin/env python3
import json
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

SAMPLES = [
    {
        "id": "RF_FAT_1",
        "text": "Fattura n. 2026/A del 15/03/2026. Fornitore: Tech Solutions S.r.l. P.IVA 01234567890. Totale imponibile: € 1.500,00, IVA 22%: € 330,00. Totale fattura: € 1.830,00. IBAN: IT60X0542811101000000123456.",
        "target": "FATTURA"
    },
    {
        "id": "RF_EML_1",
        "text": "Da: claudia.rossi@partner.com\nA: direzione@azienda.it\nOggetto: Richiesta incontro strategico Q3\nGentile Dott. Vena,\nvorremmo concordare una riunione di allineamento per la prossima settimana.\nCordiali saluti,\nClaudia Rossi",
        "target": "EMAIL"
    },
    {
        "id": "RF_ANA_1",
        "text": "REPUBBLICA ITALIANA - CARTA D'IDENTITA N. CA98765ZZ\nCognome: BIANCHI\nNome: ALESSANDRO\nNato a: ROMA (RM) il 12/04/1985\nSesso: M - Statura: 180\nCodice Fiscale: BNCALN85D12H501Y\nComune di Roma - Scadenza: 12/04/2035",
        "target": "ANAGRAFICA"
    }
]

# 3 distinct permutations of the 4 candidates
PERMUTATIONS = [
    [0, 1, 2, 3],  # P0: A=FATTURA, B=EMAIL, C=ANAGRAFICA, D=ALTRO
    [1, 2, 3, 0],  # P1: A=EMAIL, B=ANAGRAFICA, C=ALTRO, D=FATTURA
    [2, 3, 0, 1]   # P2: A=ANAGRAFICA, B=ALTRO, C=FATTURA, D=EMAIL
]

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
    # Look for the first single A, B, C, or D character
    clean = text.strip()
    # Match first standalone letter A-D or letter following Choice: / Option:
    m = re.search(r'\b([A-D])\b', clean)
    if m:
        return m.group(1)
    # Fallback to checking first non-whitespace character
    if clean and clean[0] in "ABCD":
        return clean[0]
    return None

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
    out_file = os.path.join(out_dir, "phase_a_step1_raw.jsonl")

    # Configurations to test:
    # (a) Current configuration: /api/chat, num_predict: 1
    # (b) /api/generate: num_predict: 1
    # (c) /api/chat with think: False, num_predict: 1
    # (d1) /api/chat, num_predict: 8
    # (d2) /api/chat, num_predict: 32
    # (d3) /api/generate, num_predict: 8
    # (d4) /api/generate, num_predict: 32
    # (d5) /api/chat with think: False, num_predict: 32
    configs = [
        {"name": "(a) chat np=1 (attuale)", "endpoint": "/api/chat", "np": 1, "think": None, "use_messages": True},
        {"name": "(b) generate np=1", "endpoint": "/api/generate", "np": 1, "think": None, "use_messages": False},
        {"name": "(c) chat think=false np=1", "endpoint": "/api/chat", "np": 1, "think": False, "use_messages": True},
        {"name": "(d1) chat np=8", "endpoint": "/api/chat", "np": 8, "think": None, "use_messages": True},
        {"name": "(d2) chat np=32", "endpoint": "/api/chat", "np": 32, "think": None, "use_messages": True},
        {"name": "(d3) generate np=8", "endpoint": "/api/generate", "np": 8, "think": None, "use_messages": False},
        {"name": "(d4) generate np=32", "endpoint": "/api/generate", "np": 32, "think": None, "use_messages": False},
        {"name": "(d5) chat think=false np=32", "endpoint": "/api/chat", "np": 32, "think": False, "use_messages": True},
    ]

    records = []
    print(f"Starting Phase A Step 1 evaluation on {len(SAMPLES)} samples x {len(PERMUTATIONS)} permutations across {len(configs)} configs...")

    for sample in SAMPLES:
        for p_idx, perm in enumerate(PERMUTATIONS):
            prompt = build_prompt(sample["text"], perm)
            # Find expected letter
            expected_letter = None
            for l_idx, c_idx in enumerate(perm):
                if CANDIDATES[c_idx]["code"] == sample["target"]:
                    expected_letter = chr(ord('A') + l_idx)
                    break

            perm_map = {chr(ord('A') + i): CANDIDATES[idx]["code"] for i, idx in enumerate(perm)}

            for cfg in configs:
                url = OLLAMA_BASE_URL + cfg["endpoint"]
                payload = {
                    "model": MODEL_NAME,
                    "stream": False,
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

                # Determine where letter appears
                content_val = ""
                thinking_val = ""
                response_val = ""
                where = "none"

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
                        extracted = None
                else:
                    response_val = raw_resp.get("response") or ""
                    if response_val and any(c in response_val for c in "ABCD"):
                        where = "response"
                        extracted = extract_first_choice_letter(response_val)
                    else:
                        extracted = None

                correct = (extracted == expected_letter)

                # Template applied description
                if cfg["endpoint"] == "/api/chat":
                    if cfg["think"] is False:
                        template_desc = "Qwen3.5 chat template with enable_thinking=False -> `<think>\\n\\n</think>\\n\\n` prefix before generation"
                    else:
                        template_desc = "Qwen3.5 chat template default -> `<think>\\n` prefix (generation starts inside thinking)"
                else:
                    template_desc = "None (raw prompt evaluated directly without chat tags)"

                rec = {
                    "config_name": cfg["name"],
                    "endpoint": cfg["endpoint"],
                    "num_predict": cfg["np"],
                    "think_param": cfg["think"],
                    "sample_id": sample["id"],
                    "target_code": sample["target"],
                    "permutation_index": p_idx,
                    "permutation_mapping": perm_map,
                    "expected_letter": expected_letter,
                    "where_letter_appears": where,
                    "extracted_letter": extracted,
                    "raw_content": content_val,
                    "raw_thinking": thinking_val,
                    "raw_response": response_val,
                    "correct": correct,
                    "done_reason": raw_resp.get("done_reason"),
                    "eval_count": raw_resp.get("eval_count"),
                    "prompt_eval_count": raw_resp.get("prompt_eval_count"),
                    "prompt_eval_cached_count": raw_resp.get("prompt_eval_cached_count"),
                    "prompt_eval_duration_ms": (raw_resp.get("prompt_eval_duration") or 0) / 1_000_000,
                    "eval_duration_ms": (raw_resp.get("eval_duration") or 0) / 1_000_000,
                    "total_duration_ms": (raw_resp.get("total_duration") or 0) / 1_000_000,
                    "client_latency_ms": client_lat,
                    "chat_template_applied": template_desc,
                    "raw_response_full": raw_resp
                }
                records.append(rec)
                print(f"[{cfg['name']}][{sample['id']}][P{p_idx}] exp={expected_letter} got={extracted} ({where}) eval_cnt={rec['eval_count']} done={rec['done_reason']} lat={client_lat}ms")

    with open(out_file, "w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print(f"\nStep 1 finished! Wrote {len(records)} records to {out_file}")

if __name__ == "__main__":
    main()
