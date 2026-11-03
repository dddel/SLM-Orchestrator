# Batteria 1: Qwen 2.5 3B Con Guardrail vs Ablazione Guardrail vs Monolitico Senza Guardrail vs Monolitico Post-Hoc Guardrail

- **Commit Git (`freeze-1`)**: `254ae10d695e3160596026be7294cb2e9589c8f7`
- **Modello**: `qwen2.5:3b-instruct-q8_0` (`temperature=0.0`, `seed=42`, `num_ctx=2048`, `num_predict=400`, `repeat_penalty=1.0`)
- **Runner e Valutatore**: Classi Java congelate (`ComparisonRunner`, `GroundTruthComparator`, `PostHocGuardrails`, `WilsonScore`, `OllamaMonolithicClient`) con pipeline YAML originali (`src/main/resources/pipelines/*.yaml`).
- **Configurazioni**:
  - `3B_CON_GUARDRAIL`: `orchestrator.guards.enabled=true`, `orchestrator.coherence.enabled=true`
  - `3B_ABLAZIONE_GUARDRAIL`: `orchestrator.guards.enabled=false`, `orchestrator.coherence.enabled=false`
  - `3B_MONOLITICO_SENZA_GUARDRAIL`: chiamata singola `OllamaMonolithicClient` con `prompt-monolitico.txt` e `schema-busta.json`
  - `3B_MONOLITICO_POSTHOC_GUARDRAIL`: derivazione deterministica tramite `PostHocGuardrails` applicata agli output grezzi di `3B_MONOLITICO_SENZA_GUARDRAIL`

## 1. Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 11 | 6727.0 | 7178.6 | 9360 |
| `3B_ABLAZIONE_GUARDRAIL` | 372/392 (94.9%, IC 95%: 92.3–96.7%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 12 | 6727.5 | 7505.0 | 10062 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 231/392 (58.9%, IC 95%: 54.0–63.7%) | 9/42 (21.4%, IC 95%: 11.7–35.9%) | 33 (20/11/2) | 0 | 0 | 0 | 3 | 7334.0 | 7435.9 | 9188 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 232/392 (59.2%, IC 95%: 54.3–63.9%) | 9/42 (21.4%, IC 95%: 11.7–35.9%) | 21 (14/7/0) | 0 | 12 | 0 | 3 | 7334.0 | 7435.9 | 9188 |

### Dettaglio per Tipologia Documento

| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot | Latenza Mediana (ms) | Latenza Media (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 109/112 (97.3%) | 6271.5 | 6623.6 |
| `3B_CON_GUARDRAIL` | `ANAGRAFICA` | 10/14 | 4 | 0 | 0 | 146/154 (94.8%) | 6276 | 6188.5 |
| `3B_CON_GUARDRAIL` | `EMAIL` | 9/14 | 5 | 0 | 0 | 118/126 (93.7%) | 8808 | 8723.8 |
| `3B_ABLAZIONE_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 109/112 (97.3%) | 6293 | 6634.1 |
| `3B_ABLAZIONE_GUARDRAIL` | `ANAGRAFICA` | 10/14 | 4 | 0 | 0 | 145/154 (94.2%) | 6331 | 6246.9 |
| `3B_ABLAZIONE_GUARDRAIL` | `EMAIL` | 9/14 | 5 | 0 | 0 | 118/126 (93.7%) | 8974 | 9633.9 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `FATTURA` | 9/14 | 5 | 0 | 0 | 104/112 (92.9%) | 8025.5 | 7839.6 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `ANAGRAFICA` | 0/14 | 14 | 0 | 0 | 91/154 (59.1%) | 7881.5 | 8418.9 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `EMAIL` | 0/14 | 14 | 0 | 0 | 36/126 (28.6%) | 6095.5 | 6049.0 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `FATTURA` | 9/14 | 4 | 0 | 1 | 104/112 (92.9%) | 8025.5 | 7839.6 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `ANAGRAFICA` | 0/14 | 7 | 0 | 7 | 92/154 (59.7%) | 7881.5 | 8418.9 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `EMAIL` | 0/14 | 10 | 0 | 4 | 36/126 (28.6%) | 6095.5 | 6049.0 |

## 2. Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) | 6555.5 | 6510.7 | 6644 |
| `3B_ABLAZIONE_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) | 6782.0 | 6781.0 | 7176 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 6/6 (100.0%) | 0/6 (0.0%) | 2 | 4 | 0 | 0 | 48/54 (88.9%, IC 95%: 77.8–94.8%) | 7945.0 | 7919.0 | 8314 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 2 | 4 | 48/54 (88.9%, IC 95%: 77.8–94.8%) | 7945.0 | 7919.0 | 8314 |

## 3. Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 0 | 6203.5 | 6421.9 | 8521 |
| `3B_ABLAZIONE_GUARDRAIL` | 540/560 (96.4%, IC 95%: 94.5–97.7%) | 46/60 (76.7%, IC 95%: 64.6–85.6%) | 13 (0/13/0) | 0 | 1 | 0 | 0 | 6245.0 | 6539.3 | 8658 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 369/560 (65.9%, IC 95%: 61.9–69.7%) | 20/60 (33.3%, IC 95%: 22.7–45.9%) | 40 (22/17/1) | 0 | 0 | 0 | 1 | 6344.0 | 6587.1 | 8211 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 374/560 (66.8%, IC 95%: 62.8–70.6%) | 21/60 (35.0%, IC 95%: 24.2–47.6%) | 35 (20/15/0) | 0 | 4 | 0 | 0 | 6344.0 | 6587.1 | 8211 |

### Dettaglio per Tipologia Documento

| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot | Latenza Mediana (ms) | Latenza Media (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | `FATTURA` | 15/20 | 4 | 0 | 1 | 155/160 (96.9%) | 4806.5 | 5498.6 |
| `3B_CON_GUARDRAIL` | `ANAGRAFICA` | 17/20 | 3 | 0 | 0 | 215/220 (97.7%) | 5732 | 5841.1 |
| `3B_CON_GUARDRAIL` | `EMAIL` | 17/20 | 1 | 2 | 0 | 179/180 (99.4%) | 7290.5 | 7925.9 |
| `3B_ABLAZIONE_GUARDRAIL` | `FATTURA` | 13/20 | 6 | 0 | 1 | 150/160 (93.8%) | 4979 | 5613.6 |
| `3B_ABLAZIONE_GUARDRAIL` | `ANAGRAFICA` | 14/20 | 6 | 0 | 0 | 211/220 (95.9%) | 5781.5 | 5885.4 |
| `3B_ABLAZIONE_GUARDRAIL` | `EMAIL` | 19/20 | 1 | 0 | 0 | 179/180 (99.4%) | 7356.5 | 8118.9 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `FATTURA` | 14/20 | 6 | 0 | 0 | 139/160 (86.9%) | 6065.5 | 6390.6 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `ANAGRAFICA` | 6/20 | 14 | 0 | 0 | 191/220 (86.8%) | 7631.5 | 7608.7 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `EMAIL` | 0/20 | 20 | 0 | 0 | 39/180 (21.7%) | 5764.5 | 5762.0 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `FATTURA` | 14/20 | 6 | 0 | 0 | 140/160 (87.5%) | 6065.5 | 6390.6 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `ANAGRAFICA` | 7/20 | 11 | 0 | 2 | 195/220 (88.6%) | 7631.5 | 7608.7 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `EMAIL` | 0/20 | 18 | 0 | 2 | 39/180 (21.7%) | 5764.5 | 5762.0 |

## 4. Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 7 | 2 | 70/72 (97.2%, IC 95%: 90.4–99.2%) | 4725.0 | 4766.3 | 5328 |
| `3B_ABLAZIONE_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 4 | 5 | 65/72 (90.3%, IC 95%: 81.3–95.2%) | 4717.0 | 4787.0 | 5371 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 9/9 (100.0%) | 0/9 (0.0%) | 3 | 6 | 0 | 0 | 51/72 (70.8%, IC 95%: 59.5–80.1%) | 6275.0 | 6041.6 | 6708 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 3/9 (33.3%) | 6/9 (66.7%) | 0 | 3 | 3 | 3 | 52/72 (72.2%, IC 95%: 61.0–81.2%) | 6275.0 | 6041.6 | 6708 |

