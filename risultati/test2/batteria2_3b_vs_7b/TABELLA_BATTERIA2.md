# Batteria 2: Qwen 2.5 3B Con Guardrail (Cella A) vs Qwen 2.5 7B Senza Guardrail (Cella B) e 7B Post-Hoc Guardrail (Cella C) e 7B Orchestratore

- **Commit Git (`freeze-1`)**: `254ae10d695e3160596026be7294cb2e9589c8f7`
- **Modelli confrontati**: `qwen2.5:3b-instruct-q8_0` (Cella A) vs `qwen2.5:7b-instruct-q8_0` (Celle B, C, 7B_ABLAZIONE, 7B_CON_GUARDRAIL)
- **Parametri di campionamento**: `temperature=0.0`, `seed=42`, `num_ctx=2048`, `num_predict=400`, `repeat_penalty=1.0`
- **Nota Hardware**: Come da protocollo, non viene effettuato alcun confronto di latenza tra 3B e 7B poiché `qwen2.5:7b-instruct-q8_0` viene eseguito in split GPU/CPU sulla macchina di test.
- **Runner e Valutatore**: Classi Java congelate (`ComparisonRunner`, `GroundTruthComparator`, `PostHocGuardrails`, `WilsonScore`, `OllamaMonolithicClient`).

## 1. Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 11 |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 325/392 (82.9%, IC 95%: 78.9–86.3%) | 18/42 (42.9%, IC 95%: 29.1–57.8%) | 24 (4/18/2) | 0 | 0 | 0 | 3 |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 327/392 (83.4%, IC 95%: 79.4–86.8%) | 19/42 (45.2%, IC 95%: 31.2–60.1%) | 22 (3/17/2) | 0 | 1 | 0 | 3 |
| `7B_ABLAZIONE_GUARDRAIL` | 388/392 (99.0%, IC 95%: 97.4–99.6%) | 38/42 (90.5%, IC 95%: 77.9–96.2%) | 4 (0/4/0) | 0 | 0 | 0 | 0 |
| `7B_CON_GUARDRAIL` | 389/392 (99.2%, IC 95%: 97.8–99.7%) | 36/42 (85.7%, IC 95%: 72.2–93.3%) | 3 (0/3/0) | 3 | 0 | 0 | 0 |

### Dettaglio per Tipologia Documento

| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 109/112 (97.3%) |
| `3B_CON_GUARDRAIL` | `ANAGRAFICA` | 10/14 | 4 | 0 | 0 | 146/154 (94.8%) |
| `3B_CON_GUARDRAIL` | `EMAIL` | 9/14 | 5 | 0 | 0 | 118/126 (93.7%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | `FATTURA` | 14/14 | 0 | 0 | 0 | 112/112 (100.0%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | `ANAGRAFICA` | 4/14 | 10 | 0 | 0 | 122/154 (79.2%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | `EMAIL` | 0/14 | 14 | 0 | 0 | 91/126 (72.2%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | `FATTURA` | 14/14 | 0 | 0 | 0 | 112/112 (100.0%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | `ANAGRAFICA` | 5/14 | 8 | 0 | 1 | 124/154 (80.5%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | `EMAIL` | 0/14 | 14 | 0 | 0 | 91/126 (72.2%) |
| `7B_ABLAZIONE_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 111/112 (99.1%) |
| `7B_ABLAZIONE_GUARDRAIL` | `ANAGRAFICA` | 12/14 | 2 | 0 | 0 | 152/154 (98.7%) |
| `7B_ABLAZIONE_GUARDRAIL` | `EMAIL` | 13/14 | 1 | 0 | 0 | 125/126 (99.2%) |
| `7B_CON_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 111/112 (99.1%) |
| `7B_CON_GUARDRAIL` | `ANAGRAFICA` | 13/14 | 1 | 0 | 0 | 153/154 (99.4%) |
| `7B_CON_GUARDRAIL` | `EMAIL` | 10/14 | 1 | 3 | 0 | 125/126 (99.2%) |

## 2. Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 6/6 (100.0%) | 0/6 (0.0%) | 6 | 0 | 0 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_ABLAZIONE_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_CON_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |

## 3. Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 0 |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 471/560 (84.1%, IC 95%: 80.8–86.9%) | 25/60 (41.7%, IC 95%: 30.1–54.3%) | 35 (7/22/6) | 0 | 0 | 0 | 6 |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 475/560 (84.8%, IC 95%: 81.6–87.6%) | 28/60 (46.7%, IC 95%: 34.6–59.1%) | 29 (7/21/1) | 0 | 3 | 0 | 3 |
| `7B_ABLAZIONE_GUARDRAIL` | 539/560 (96.3%, IC 95%: 94.3–97.5%) | 53/60 (88.3%, IC 95%: 77.8–94.2%) | 7 (2/4/1) | 0 | 0 | 0 | 1 |
| `7B_CON_GUARDRAIL` | 541/560 (96.6%, IC 95%: 94.8–97.8%) | 54/60 (90.0%, IC 95%: 79.9–95.3%) | 6 (2/4/0) | 0 | 0 | 0 | 0 |

### Dettaglio per Tipologia Documento

| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | `FATTURA` | 15/20 | 4 | 0 | 1 | 155/160 (96.9%) |
| `3B_CON_GUARDRAIL` | `ANAGRAFICA` | 17/20 | 3 | 0 | 0 | 215/220 (97.7%) |
| `3B_CON_GUARDRAIL` | `EMAIL` | 17/20 | 1 | 2 | 0 | 179/180 (99.4%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | `FATTURA` | 15/20 | 5 | 0 | 0 | 141/160 (88.1%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | `ANAGRAFICA` | 10/20 | 10 | 0 | 0 | 205/220 (93.2%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | `EMAIL` | 0/20 | 20 | 0 | 0 | 125/180 (69.4%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | `FATTURA` | 16/20 | 4 | 0 | 0 | 142/160 (88.8%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | `ANAGRAFICA` | 12/20 | 5 | 0 | 3 | 208/220 (94.5%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | `EMAIL` | 0/20 | 20 | 0 | 0 | 125/180 (69.4%) |
| `7B_ABLAZIONE_GUARDRAIL` | `FATTURA` | 18/20 | 2 | 0 | 0 | 158/160 (98.8%) |
| `7B_ABLAZIONE_GUARDRAIL` | `ANAGRAFICA` | 18/20 | 2 | 0 | 0 | 216/220 (98.2%) |
| `7B_ABLAZIONE_GUARDRAIL` | `EMAIL` | 17/20 | 3 | 0 | 0 | 165/180 (91.7%) |
| `7B_CON_GUARDRAIL` | `FATTURA` | 19/20 | 1 | 0 | 0 | 159/160 (99.4%) |
| `7B_CON_GUARDRAIL` | `ANAGRAFICA` | 18/20 | 2 | 0 | 0 | 217/220 (98.6%) |
| `7B_CON_GUARDRAIL` | `EMAIL` | 17/20 | 3 | 0 | 0 | 165/180 (91.7%) |

## 4. Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 7 | 2 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 9/9 (100.0%) | 0/9 (0.0%) | 4 | 5 | 0 | 0 | 53/72 (73.6%, IC 95%: 62.4–82.4%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 3/9 (33.3%) | 6/9 (66.7%) | 0 | 3 | 5 | 1 | 54/72 (75.0%, IC 95%: 63.9–83.6%) |
| `7B_ABLAZIONE_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 7 | 2 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `7B_CON_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 8 | 1 | 71/72 (98.6%, IC 95%: 92.5–99.8%) |

