# Riepilogo Sperimentale Completo — Test 2 (`test2-recap`)

## Parametri e Configurazione del Protocollo

- **Codebase e Valutatore**: Classi Java congelate al tag/branch `freeze-1` (`ComparisonRunner`, `GroundTruthComparator`, `PostHocGuardrails`, `WilsonScore`, `OllamaMonolithicClient`, `LocalOllamaRouter`). Nessuna modifica a `src/main` o `src/test`.
- **Workspace e Script di Benchmark**: I runner Java dedicati (`Battery1FrozenRunner.java`, `RouterFrozenRunner.java`, `PostHocDeriveRunner.java`), gli script di esecuzione bash (`run_battery1_frozen.sh`, `run_battery2_frozen.sh`, `run_battery4_frozen.sh`), gli aggregatori di metriche (`aggregate_battery*.py`) e le configurazioni di pipeline per l'ablazione risiedono nella sottocartella dedicata [`benchmark_workspace/`](file:///home/del/Projects/slm-orchestrator/risultati/test2/benchmark_workspace).
- **Modelli Testati**:
  - `qwen2.5:3b-instruct-q8_0` (inferenza su iGPU AMD via Vulkan)
  - `qwen2.5:7b-instruct-q8_0` (inferenza in split GPU/CPU; per questo motivo i tempi di risposta del 7B non sono confrontati con quelli del 3B)
- **Parametri di Inferenza**: `temperature=0.0` (con incremento a `0.2` in caso di retry), `seed=42`, `num_ctx=2048`, `num_predict=400`, `repeat_penalty=1.0`, `keep_alive=60m`.
- **Dataset Congelati (`risultati/test2/datasets/`)**:
  1. **Held-Out (`heldout_dataset_v1.json`)**: 42 documenti out-of-sample (14 `FATTURA`, 14 `ANAGRAFICA`, 14 `EMAIL`), 392 campi totali.
  2. **Held-Out Avversariale (`heldout_adversarial_v1.json`)**: 6 documenti con codici formalmente invalidi nel testo sorgente (2 P.IVA con Luhn errato, 2 CF con CIN errato, 2 IBAN con MOD-97 errato), 54 campi totali.
  3. **Held-Out Router `ALTRO` (`heldout_router_altro_v1.json`)**: 8 documenti fuori dominio (verbali, comunicazioni interne, contratti, DDT) uniti ai 42 Held-Out per il test del router a 4 classi (50 documenti totali).
  4. **Dev (`dataset_60_v2.json`)**: 60 documenti di sviluppo (20 `FATTURA`, 20 `ANAGRAFICA`, 20 `EMAIL`), 560 campi totali.
  5. **Dev Avversariale (`adversarial_piva_set.json`)**: 9 fatture con P.IVA formalmente invalida nel testo sorgente, 72 campi totali.
- **Tassonomia degli Esiti Documentali**:
  - `C1`: Stato `OK` e tutti i campi estratti coincidono con la ground truth.
  - `C2`: Stato `OK` ma almeno un campo differisce dalla ground truth (`C2a`: chiavi mancanti/schema parziale; `C2b`: tutti i campi presenti ma almeno un valore non nullo errato o mancante; `C2c`: tutti i campi presenti ma il modello ha valorizzato almeno un campo atteso a `null`, conteggiato anche a livello di singolo campo in `VALORE_SU_NULL_ATTESO`).
  - `C3`: Stato `INDETERMINATE` (bloccato dai guardrail) con estrazione fedele al testo su tutti i campi.
  - `C4`: Stato `INDETERMINATE` con almeno un campo difforme dalla ground truth.
  - `C5`: Errore tecnico o JSON malformato (`0` casi registrati in tutte le batterie).

---

## Batteria 1 — Ablazione Guardrail sul Modello 3B (`qwen2.5:3b-instruct-q8_0`)

Confronto tra quattro configurazioni a parità di modello 3B:
1. `3B_CON_GUARDRAIL`: Orchestratore completo (router + pipeline specializzate + `PreValidationNormalizer` + validazione schema/formati/checksum + `CrossFieldCoherenceGuard` + `CatalogMembershipGuard` + Smart Retry `RF7`).
2. `3B_ABLAZIONE_GUARDRAIL`: Orchestratore con `orchestrator.guards.enabled=false` e `orchestrator.coherence.enabled=false` (rimangono attive la normalizzazione pre-validazione e i validatori strutturali/algebrici di base).
3. `3B_MONOLITICO_SENZA_GUARDRAIL`: Chiamata singola diretta a Ollama con prompt e schema unificati (`prompt-monolitico.txt` + `schema-busta.json`), senza orchestratore né validazione.
4. `3B_MONOLITICO_POSTHOC_GUARDRAIL`: Applicazione deterministica a posteriori (`PostHocGuardrails`) di normalizzazione e validazione sugli output grezzi prodotti da `3B_MONOLITICO_SENZA_GUARDRAIL`.

### 1.1 Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 11 | 6727.0 | 7178.6 | 9360 |
| `3B_ABLAZIONE_GUARDRAIL` | 372/392 (94.9%, IC 95%: 92.3–96.7%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 12 | 6727.5 | 7505.0 | 10062 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 231/392 (58.9%, IC 95%: 54.0–63.7%) | 9/42 (21.4%, IC 95%: 11.7–35.9%) | 33 (20/11/2) | 0 | 0 | 0 | 3 | 7334.0 | 7435.9 | 9188 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 232/392 (59.2%, IC 95%: 54.3–63.9%) | 9/42 (21.4%, IC 95%: 11.7–35.9%) | 21 (14/7/0) | 0 | 12 | 0 | 3 | 7334.0 | 7435.9 | 9188 |

#### Dettaglio per Tipologia Documento (Held-Out)

| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot | Latenza Mediana (ms) | Latenza Media (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 109/112 (97.3%) | 6271.5 | 6623.6 |
| `3B_CON_GUARDRAIL` | `ANAGRAFICA` | 10/14 | 4 | 0 | 0 | 146/154 (94.8%) | 6276.0 | 6188.5 |
| `3B_CON_GUARDRAIL` | `EMAIL` | 9/14 | 5 | 0 | 0 | 118/126 (93.7%) | 8808.0 | 8723.8 |
| `3B_ABLAZIONE_GUARDRAIL` | `FATTURA` | 13/14 | 1 | 0 | 0 | 109/112 (97.3%) | 6293.0 | 6634.1 |
| `3B_ABLAZIONE_GUARDRAIL` | `ANAGRAFICA` | 10/14 | 4 | 0 | 0 | 145/154 (94.2%) | 6331.0 | 6246.9 |
| `3B_ABLAZIONE_GUARDRAIL` | `EMAIL` | 9/14 | 5 | 0 | 0 | 118/126 (93.7%) | 8974.0 | 9633.9 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `FATTURA` | 9/14 | 5 | 0 | 0 | 104/112 (92.9%) | 8025.5 | 7839.6 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `ANAGRAFICA` | 0/14 | 14 | 0 | 0 | 91/154 (59.1%) | 7881.5 | 8418.9 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `EMAIL` | 0/14 | 14 | 0 | 0 | 36/126 (28.6%) | 6095.5 | 6049.0 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `FATTURA` | 9/14 | 4 | 0 | 1 | 104/112 (92.9%) | 8025.5 | 7839.6 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `ANAGRAFICA` | 0/14 | 7 | 0 | 7 | 92/154 (59.7%) | 7881.5 | 8418.9 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `EMAIL` | 0/14 | 10 | 0 | 4 | 36/126 (28.6%) | 6095.5 | 6049.0 |

### 1.2 Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) | 6555.5 | 6510.7 | 6644 |
| `3B_ABLAZIONE_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) | 6782.0 | 6781.0 | 7176 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 6/6 (100.0%) | 0/6 (0.0%) | 2 | 4 | 0 | 0 | 48/54 (88.9%, IC 95%: 77.8–94.8%) | 7945.0 | 7919.0 | 8314 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 2 | 4 | 48/54 (88.9%, IC 95%: 77.8–94.8%) | 7945.0 | 7919.0 | 8314 |

### 1.3 Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 0 | 6203.5 | 6421.9 | 8521 |
| `3B_ABLAZIONE_GUARDRAIL` | 540/560 (96.4%, IC 95%: 94.5–97.7%) | 46/60 (76.7%, IC 95%: 64.6–85.6%) | 13 (0/13/0) | 0 | 1 | 0 | 0 | 6245.0 | 6539.3 | 8658 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 369/560 (65.9%, IC 95%: 61.9–69.7%) | 20/60 (33.3%, IC 95%: 22.7–45.9%) | 40 (22/17/1) | 0 | 0 | 0 | 1 | 6344.0 | 6587.1 | 8211 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 374/560 (66.8%, IC 95%: 62.8–70.6%) | 21/60 (35.0%, IC 95%: 24.2–47.6%) | 35 (20/15/0) | 0 | 4 | 0 | 0 | 6344.0 | 6587.1 | 8211 |

#### Dettaglio per Tipologia Documento (Dev)

| Configurazione | Tipo Documento | Doc C1 / Tot | C2 | C3 | C4 | Campi Corretti / Tot | Latenza Mediana (ms) | Latenza Media (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | `FATTURA` | 15/20 | 4 | 0 | 1 | 155/160 (96.9%) | 4806.5 | 5498.6 |
| `3B_CON_GUARDRAIL` | `ANAGRAFICA` | 17/20 | 3 | 0 | 0 | 215/220 (97.7%) | 5732.0 | 5841.1 |
| `3B_CON_GUARDRAIL` | `EMAIL` | 17/20 | 1 | 2 | 0 | 179/180 (99.4%) | 7290.5 | 7925.9 |
| `3B_ABLAZIONE_GUARDRAIL` | `FATTURA` | 13/20 | 6 | 0 | 1 | 150/160 (93.8%) | 4979.0 | 5613.6 |
| `3B_ABLAZIONE_GUARDRAIL` | `ANAGRAFICA` | 14/20 | 6 | 0 | 0 | 211/220 (95.9%) | 5781.5 | 5885.4 |
| `3B_ABLAZIONE_GUARDRAIL` | `EMAIL` | 19/20 | 1 | 0 | 0 | 179/180 (99.4%) | 7356.5 | 8118.9 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `FATTURA` | 14/20 | 6 | 0 | 0 | 139/160 (86.9%) | 6065.5 | 6390.6 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `ANAGRAFICA` | 6/20 | 14 | 0 | 0 | 191/220 (86.8%) | 7631.5 | 7608.7 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | `EMAIL` | 0/20 | 20 | 0 | 0 | 39/180 (21.7%) | 5764.5 | 5762.0 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `FATTURA` | 14/20 | 6 | 0 | 0 | 140/160 (87.5%) | 6065.5 | 6390.6 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `ANAGRAFICA` | 7/20 | 11 | 0 | 2 | 195/220 (88.6%) | 7631.5 | 7608.7 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | `EMAIL` | 0/20 | 18 | 0 | 2 | 39/180 (21.7%) | 5764.5 | 5762.0 |

### 1.4 Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) | Latenza Mediana (ms) | Latenza Media (ms) | Latenza P95 (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 7 | 2 | 70/72 (97.2%, IC 95%: 90.4–99.2%) | 4725.0 | 4766.3 | 5328 |
| `3B_ABLAZIONE_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 4 | 5 | 65/72 (90.3%, IC 95%: 81.3–95.2%) | 4717.0 | 4787.0 | 5371 |
| `3B_MONOLITICO_SENZA_GUARDRAIL` | 9/9 (100.0%) | 0/9 (0.0%) | 3 | 6 | 0 | 0 | 51/72 (70.8%, IC 95%: 59.5–80.1%) | 6275.0 | 6041.6 | 6708 |
| `3B_MONOLITICO_POSTHOC_GUARDRAIL` | 3/9 (33.3%) | 6/9 (66.7%) | 0 | 3 | 3 | 3 | 52/72 (72.2%, IC 95%: 61.0–81.2%) | 6275.0 | 6041.6 | 6708 |

---

## Batteria 2 — Confronto Architetturale 3B Orchestrato vs 7B Monolitico e 7B Orchestrato

Confronto tra `3B_CON_GUARDRAIL` (Cella A) e quattro varianti basate su `qwen2.5:7b-instruct-q8_0`:
- `7B_MONOLITICO_SENZA_GUARDRAIL` (Cella B): 7B in chiamata singola con `prompt-monolitico.txt` e `schema-busta.json`.
- `7B_MONOLITICO_POSTHOC_GUARDRAIL` (Cella C): 7B monolitico con `PostHocGuardrails` applicati a posteriori.
- `7B_ABLAZIONE_GUARDRAIL`: 7B dentro l'orchestratore con `guards.enabled=false` e `coherence.enabled=false`.
- `7B_CON_GUARDRAIL`: 7B dentro l'orchestratore completo.

### 2.1 Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 11 |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 325/392 (82.9%, IC 95%: 78.9–86.3%) | 18/42 (42.9%, IC 95%: 29.1–57.8%) | 24 (4/18/2) | 0 | 0 | 0 | 3 |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 327/392 (83.4%, IC 95%: 79.4–86.8%) | 19/42 (45.2%, IC 95%: 31.2–60.1%) | 22 (3/17/2) | 0 | 1 | 0 | 3 |
| `7B_ABLAZIONE_GUARDRAIL` | 388/392 (99.0%, IC 95%: 97.4–99.6%) | 38/42 (90.5%, IC 95%: 77.9–96.2%) | 4 (0/4/0) | 0 | 0 | 0 | 0 |
| `7B_CON_GUARDRAIL` | 389/392 (99.2%, IC 95%: 97.8–99.7%) | 36/42 (85.7%, IC 95%: 72.2–93.3%) | 3 (0/3/0) | 3 | 0 | 0 | 0 |

#### Dettaglio per Tipologia Documento (Held-Out)

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

### 2.2 Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 6/6 (100.0%) | 0/6 (0.0%) | 6 | 0 | 0 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_ABLAZIONE_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_CON_GUARDRAIL` | 0/6 (0.0%) | 6/6 (100.0%) | 0 | 0 | 6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |

### 2.3 Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 0 |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 471/560 (84.1%, IC 95%: 80.8–86.9%) | 25/60 (41.7%, IC 95%: 30.1–54.3%) | 35 (7/22/6) | 0 | 0 | 0 | 6 |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 475/560 (84.8%, IC 95%: 81.6–87.6%) | 28/60 (46.7%, IC 95%: 34.6–59.1%) | 29 (7/21/1) | 0 | 3 | 0 | 3 |
| `7B_ABLAZIONE_GUARDRAIL` | 539/560 (96.3%, IC 95%: 94.3–97.5%) | 53/60 (88.3%, IC 95%: 77.8–94.2%) | 7 (2/4/1) | 0 | 0 | 0 | 1 |
| `7B_CON_GUARDRAIL` | 541/560 (96.6%, IC 95%: 94.8–97.8%) | 54/60 (90.0%, IC 95%: 79.9–95.3%) | 6 (2/4/0) | 0 | 0 | 0 | 0 |

#### Dettaglio per Tipologia Documento (Dev)

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

### 2.4 Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti)

| Configurazione | Codici Invalidi Accettati come `OK` (`C1+C2`) | Codici Invalidi Intercettati come `INDETERMINATE` (`C3+C4`) | `C1` | `C2` | `C3` | `C4` | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 7 | 2 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `7B_MONOLITICO_SENZA_GUARDRAIL` | 9/9 (100.0%) | 0/9 (0.0%) | 4 | 5 | 0 | 0 | 53/72 (73.6%, IC 95%: 62.4–82.4%) |
| `7B_MONOLITICO_POSTHOC_GUARDRAIL` | 3/9 (33.3%) | 6/9 (66.7%) | 0 | 3 | 5 | 1 | 54/72 (75.0%, IC 95%: 63.9–83.6%) |
| `7B_ABLAZIONE_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 7 | 2 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `7B_CON_GUARDRAIL` | 0/9 (0.0%) | 9/9 (100.0%) | 0 | 0 | 8 | 1 | 71/72 (98.6%, IC 95%: 92.5–99.8%) |

---

## Batteria 3 — Router Locale a 4 Classi (`LocalOllamaRouter` con `qwen2.5:3b-instruct-q8_0`)

Valutazione isolata dello step di classificazione iniziale (`FATTURA`, `EMAIL`, `ANAGRAFICA`, `ALTRO`) con prompt `prompts/router-system.txt`.

### 3.1 Set Held-Out Out-of-Sample (50 documenti: 14 `FATTURA`, 14 `ANAGRAFICA`, 14 `EMAIL`, 8 `ALTRO`)

| Metrica | Valore |
| :--- | :---: |
| Documenti Totali | 50 |
| Instradamenti Corretti | 49 / 50 |
| Accuratezza di Routing (Wilson 95%) | 49/50 (98.0%, IC 95%: 89.5–99.6%) |
| Latenza Mediana | 421 ms |
| Latenza Media | 435.7 ms |
| Latenza P95 | 635 ms |

#### Matrice di Confusione (Held-Out)

| Tipo Effettivo \ Predetto | FATTURA | EMAIL | ANAGRAFICA | ALTRO | Accuratezza per Classe |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **FATTURA** | 14 | 0 | 0 | 0 | 100.0% (14/14) |
| **EMAIL** | 0 | 14 | 0 | 0 | 100.0% (14/14) |
| **ANAGRAFICA** | 0 | 0 | 14 | 0 | 100.0% (14/14) |
| **ALTRO** | 0 | 1 | 0 | 7 | 87.5% (7/8) |

### 3.2 Set di Sviluppo (60 documenti: 20 `FATTURA`, 20 `ANAGRAFICA`, 20 `EMAIL`)

| Metrica | Valore |
| :--- | :---: |
| Documenti Totali | 60 |
| Instradamenti Corretti | 60 / 60 |
| Accuratezza di Routing (Wilson 95%) | 60/60 (100.0%, IC 95%: 94.0–100.0%) |
| Latenza Mediana | 341 ms |
| Latenza Media | 342.0 ms |
| Latenza P95 | 412 ms |

#### Matrice di Confusione (Dev)

| Tipo Effettivo \ Predetto | FATTURA | EMAIL | ANAGRAFICA | ALTRO | Accuratezza per Classe |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **FATTURA** | 20 | 0 | 0 | 0 | 100.0% (20/20) |
| **EMAIL** | 0 | 20 | 0 | 0 | 100.0% (20/20) |
| **ANAGRAFICA** | 0 | 0 | 20 | 0 | 100.0% (20/20) |
| **ALTRO** | 0 | 0 | 0 | 0 | - |

---

## Batteria 4 — Smart Retry (`RF7`), Cross-Field Coherence e Short-Circuit Avversariale

Confronto tra `3B_CON_GUARDRAIL_SENZA_RETRY` (`max_retries=0`, pipeline prive di blocchi `on_failure`), `3B_CON_GUARDRAIL_E_RETRY` (`max_retries=2` con feedback tipizzato `RF7` senza valore rifiutato e incremento temperatura `0.0 -> 0.2`) e `7B_CON_GUARDRAIL_E_RETRY` (`max_retries=2` + `RF7`).

*(Nota: i documenti `FATTURA` e `ANAGRAFICA` richiedono 1 step LLM base, mentre i documenti `EMAIL` richiedono 2 step LLM sequenziali distinti: `email_inference` + `recipient_inference`. Il numero minimo di chiamate LLM senza retry è quindi 56 su `heldout` e 80 su `dev`. Un retry si verifica quando lo stesso `stepId` viene eseguito `> 1` volta).*

### 4.1 Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | Doc con Retry (`stepId > 1`) | Chiamate LLM Totali (Extra Retry) | Recuperati a `OK` (`C1`) | Esauriti in `INDETERMINATE` | Latenza Media / Mediana (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 31/42 (73.8%, IC 95%: 58.9–84.7%) | 10 (0/2/8) | 1 | 0 | 0 | 56 (+0) | 0 (0 `C1`) | 0 | 7767.7 / 7234.5 |
| `3B_CON_GUARDRAIL_E_RETRY` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 1 | 57 (+1) | 1 (1 `C1`) | 0 | 7173.8 / 6723.0 |
| `7B_CON_GUARDRAIL_E_RETRY` | 389/392 (99.2%, IC 95%: 97.8–99.7%) | 36/42 (85.7%, IC 95%: 72.2–93.3%) | 3 (0/3/0) | 3 | 0 | 5 | 65 (+9) | 2 (2 `C1`) | 3 | N/A (split GPU/CPU) |

#### Tracciato dei Documenti con Attivazione di Retry (Held-Out)

| Configurazione | Doc ID (`Tipo`) | Step in Retry (`Tentativi`) | Tentativo 1 (`prompt`/`compl` tok, ms) | Tentativo 2 (`prompt`/`compl` tok, ms) | Tentativo 3 (`prompt`/`compl` tok, ms) | Causa Trigger / Errore Guardrail | Esito Finale (`Categoria`, Campi) |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| `3B_CON_GUARDRAIL_E_RETRY` | `HO_FAT_06` (`FATTURA`) | `fattura_inference` (2) | `669`/`138` tok (5306 ms) | `789`/`137` tok (5442 ms) | - | `iban: FORMAT_INVALID (iban)` (risolto al T2) | `OK` (`C1`, 8/8) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_01` (`EMAIL`) | `recipient_inference` (3) | `1479`/`68` tok (10637 ms) | `1611`/`58` tok (9596 ms) | `1639`/`50` tok (8269 ms) | `destinatario: VALUE_NOT_IN_CATALOG (catalog_membership)` (risolto al T3) | `OK` (`C1`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_08` (`EMAIL`) | `recipient_inference` (3) | `1424`/`74` tok (10550 ms) | `1556`/`65` tok (9531 ms) | `1565`/`65` tok (9816 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_09` (`EMAIL`) | `recipient_inference` (3) | `1366`/`61` tok (9040 ms) | `1526`/`64` tok (9262 ms) | `1535`/`63` tok (9339 ms) | `destinatario` + `ruoloDestinatario: VALUE_NOT_IN_CATALOG` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_11` (`EMAIL`) | `recipient_inference` (2) | `1439`/`78` tok (16492 ms) | `1571`/`63` tok (14230 ms) | - | `destinatario: VALUE_NOT_IN_CATALOG (catalog_membership)` (risolto al T2) | `OK` (`C1`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_12` (`EMAIL`) | `recipient_inference` (3) | `1386`/`64` tok (9170 ms) | `1546`/`66` tok (9717 ms) | `1555`/`64` tok (9831 ms) | `destinatario` + `ruoloDestinatario: VALUE_NOT_IN_CATALOG` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |

### 4.2 Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti)

| Configurazione | Codici Invalidi Intercettati (`INDETERMINATE`) | Chiamate LLM Totali | Doc in Short-Circuit (`SOURCE_CHECKSUM_INVALID`) | Chiamate di Retry Risparmiate | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 6/6 (100.0%) | 6 | 6/6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `3B_CON_GUARDRAIL_E_RETRY` | 6/6 (100.0%) | 6 | 6/6 | 12 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_CON_GUARDRAIL_E_RETRY` | 6/6 (100.0%) | 6 | 6/6 | 12 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |

### 4.3 Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | Doc con Retry (`stepId > 1`) | Chiamate LLM Totali (Extra Retry) | Recuperati a `OK` (`C1`) | Esauriti in `INDETERMINATE` | Latenza Media / Mediana (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 80 (+0) | 0 (0 `C1`) | 0 | 7405.9 / 7125.5 |
| `3B_CON_GUARDRAIL_E_RETRY` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 3 | 86 (+6) | 0 (0 `C1`) | 3 | 6417.8 / 6197.5 |
| `7B_CON_GUARDRAIL_E_RETRY` | 541/560 (96.6%, IC 95%: 94.8–97.8%) | 54/60 (90.0%, IC 95%: 79.9–95.3%) | 6 (2/4/0) | 0 | 0 | 0 | 78 (+0) | 0 (0 `C1`) | 0 | N/A (split GPU/CPU) |

#### Tracciato dei Documenti con Attivazione di Retry (Dev)

| Configurazione | Doc ID (`Tipo`) | Step in Retry (`Tentativi`) | Tentativo 1 (`prompt`/`compl` tok, ms) | Tentativo 2 (`prompt`/`compl` tok, ms) | Tentativo 3 (`prompt`/`compl` tok, ms) | Causa Trigger / Errore Guardrail | Esito Finale (`Categoria`, Campi) |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| `3B_CON_GUARDRAIL_E_RETRY` | `EML_12` (`EMAIL`) | `recipient_inference` (3) | `1310`/`41` tok (2536 ms) | `1442`/`41` tok (2853 ms) | `1451`/`46` tok (3059 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `3B_CON_GUARDRAIL_E_RETRY` | `EML_15` (`EMAIL`) | `recipient_inference` (3) | `1326`/`39` tok (2478 ms) | `1458`/`39` tok (2612 ms) | `1467`/`39` tok (2841 ms) | `destinatario` + `ruoloDestinatario: VALUE_NOT_IN_CATALOG` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `3B_CON_GUARDRAIL_E_RETRY` | `FAT_17` (`FATTURA`) | `fattura_inference` (3) | `561`/`119` tok (4515 ms) | `685`/`119` tok (4492 ms) | `694`/`119` tok (4499 ms) | `numeroFattura: REQUIRED_EMPTY (required)` (non risolto) | `INDETERMINATE` (`C4`, 7/8) |

### 4.4 Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti)

| Configurazione | Codici Invalidi Intercettati (`INDETERMINATE`) | Chiamate LLM Totali | Doc in Short-Circuit (`SOURCE_CHECKSUM_INVALID`) | Chiamate di Retry Risparmiate | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 9/9 (100.0%) | 9 | 9/9 | 0 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `3B_CON_GUARDRAIL_E_RETRY` | 9/9 (100.0%) | 9 | 9/9 | 18 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `7B_CON_GUARDRAIL_E_RETRY` | 9/9 (100.0%) | 9 | 9/9 | 18 | 71/72 (98.6%, IC 95%: 92.5–99.8%) |

---

## Osservazioni Oggettive sui Dati

### 1. Scomposizione del problema (Orchestratore) vs Prompt Monolitico
Il dato più netto riguarda la differenza tra l'approccio monolitico (un unico prompt e un unico JSON schema per tutte le tipologie documentali) e la scomposizione tramite router + pipeline dedicate:
- A parità di modello 3B, sul set Held-Out si passa da **231/392 campi corretti (58.9%) e 9/42 documenti C1 (21.4%)** nel monolitico a **373/392 campi (95.2%) e 32/42 documenti C1 (76.2%)** nell'orchestratore.
- Anche salendo di scala al modello **7B monolitico** (`325/392` campi = `82.9%`, `18/42` C1 = `42.9%` su Held-Out; `471/560` campi = `84.1%`, `25/60` C1 = `41.7%` su Dev), il modello **3B orchestrato** ottiene risultati superiori sia sui campi (+12.3 punti percentuali su Held-Out, +13.9 punti su Dev) sia sui documenti completi C1 (+33.3 punti su Held-Out, +40.0 punti su Dev).
- Guardando il dettaglio per tipologia documentale, il prompt monolitico regge discretamente sulle fatture (92.9% col 3B, 100.0% col 7B su Held-Out), ma degrada sensibilmente su `ANAGRAFICA` (59.1% col 3B, 79.2% col 7B) e soprattutto su `EMAIL` (28.6% col 3B, 72.2% col 7B, con `0/14` documenti C1 per entrambi). Nelle email, infatti, dover estrarre contemporaneamente i dati dell'evento e scegliere il destinatario dal catalogo aziendale all'interno di un'unica chiamata porta il modello monolitico a omettere chiavi (`C2a`: 20 documenti col 3B, 4 col 7B) o a sbagliare campi numerici/date. Separare l'estrazione (`email_inference`) dall'instradamento (`recipient_inference`) risolve questo collo di bottiglia.

### 2. Limiti dei Guardrail Applicati Solo a Posteriori (`PostHocGuardrails`)
Applicare gli stessi normalizzatori e validatori deterministici sopra l'output grezzo del modello monolitico (`3B_MONOLITICO_POSTHOC_GUARDRAIL` e `7B_MONOLITICO_POSTHOC_GUARDRAIL`) produce miglioramenti marginali sull'accuratezza di estrazione:
- Sul set Held-Out, il 3B passa da `231/392` (58.9%) a `232/392` (59.2%, +1 solo campo recuperato grazie alla normalizzazione) e il 7B passa da `325/392` (82.9%) a `327/392` (83.4%, +2 campi).
- I guardrail post-hoc convertono parte degli errori silenziosi `C2` in blocchi espliciti `C4` (12 documenti sul 3B Held-Out, 1 sul 7B Held-Out) quando il modello viola un formato o un campo obbligatorio, ma non possono recuperare i campi mancanti o errati se a monte il prompt monolitico ha prodotto uno schema incompleto.
- Inoltre, sul set avversariale Dev (`adversarial_piva_set.json`, 9 fatture con P.IVA invalida nel testo), il monolitico post-hoc intercetta solo **6/9 documenti (66.7%)** sia col 3B sia col 7B: nei restanti **3/9 casi (33.3%)**, il modello monolitico omette o sposta la P.IVA in un campo sbagliato, lasciando `fornitore.partitaIva` a `null` (che essendo opzionale nello schema supera il controllo post-hoc e viene accettato come `OK` / `C2`). Nell'orchestratore, invece, la maggiore fedeltà di estrazione (97.2%–100.0% sui set avversariali) fa sì che il codice corrotto venga sempre estratto nel campo giusto e quindi intercettato al **100% (6/6 su Held-Out avversariale, 9/9 su Dev avversariale)**.

### 3. Impatto dei Guardrail di Coerenza (`3B_CON_GUARDRAIL` vs `3B_ABLAZIONE_GUARDRAIL`)
Confrontando `3B_CON_GUARDRAIL` e `3B_ABLAZIONE_GUARDRAIL` (dove vengono disattivati `CrossFieldCoherenceGuard` e `CatalogMembershipGuard`, ma restano attivi pipeline dedicate e validatori di formato/checksum):
- Sul set Dev (60 doc), l'attivazione delle guardie di coerenza porta i campi corretti da `540/560` (96.4%) a `549/560` (98.0%) e i documenti C1 da `46/60` (76.7%) a `49/60` (81.7%), riducendo i falsi positivi `C2` da 13 a 8 (recuperando 2 fatture e 3 anagrafiche, e bloccando in `C3` 2 email con destinatario fuori catalogo).
- Sul set Held-Out (42 doc), invece, la differenza tra `3B_CON_GUARDRAIL` (`373/392`, 32 C1) e `3B_ABLAZIONE_GUARDRAIL` (`372/392`, 32 C1) è minima (+1 campo). Il motivo si vede nella distribuzione dei sottocodici `C2`: su Held-Out, 8 dei 10 errori `C2` del 3B sono di tipo **`C2c` (`VALORE_SU_NULL_ATTESO` = 11 campi)**, ovvero casi in cui un campo opzionale è assente nel documento (es. `codiceFiscale` del cliente quando è presente solo la P.IVA, o `dataNascita` assente) e il modello 3B copia nel campo un altro codice presente nel testo o deduce un valore formalmente valido. Quando il valore copiato rispetta il formato/checksum e non viola una regola esplicita di `CrossFieldCoherenceGuard`, passa come `OK` (`C2c`). Il modello 7B orchestrato (`7B_CON_GUARDRAIL`), al contrario, azzera completamente questo fenomeno (`0` casi di `VALORE_SU_NULL_ATTESO` sia su Held-Out sia su Dev), arrivando a **389/392 campi corretti (99.2%)** su Held-Out.

### 4. Comportamento del Router Locale a 4 Classi (Batteria 3)
- Il router basato su `qwen2.5:3b-instruct-q8_0` classifica correttamente **60/60 documenti (100.0%)** sul set Dev e **49/50 documenti (98.0%)** sul set Held-Out comprensivo dei documenti fuori dominio `ALTRO`, con una latenza mediana tra **341 ms e 421 ms** (~5–6% del tempo totale di elaborazione di un documento).
- Tutte le 42 istanze Held-Out appartenenti alle tre classi operative (`14/14 FATTURA`, `14/14 EMAIL`, `14/14 ANAGRAFICA`) vengono instradate senza errori; l'unica imprecisione su 50 documenti riguarda 1 documento `ALTRO` su 8 (una comunicazione testuale classificata come `EMAIL`).
- Curiosamente, quando nella Batteria 2/4 il router viene eseguito con il modello **7B** sul set Dev (`7B_CON_GUARDRAIL`), il 7B classifica erroneamente 2 email (`EML_14` e `EML_17`) come `ALTRO`, causando i 2 errori `C2a` e spiegando perché sul set Dev il 7B orchestrato abbia meno campi corretti nelle email rispetto al 3B orchestrato (`165/180` = 91.7% col 7B contro `179/180` = 99.4% col 3B). Il prompt del router è stato calibrato sul 3B e il 3B risulta più stabile su quel compito specifico.

### 5. Impatto dello Smart Retry (`RF7`) e dello Short-Circuit Avversariale (Batteria 4)
I dati della Batteria 4 mostrano che il meccanismo di retry con feedback tipizzato (`RF7`) interviene in un numero circoscritto di casi, poiché la maggior parte dei documenti supera la validazione già al primo tentativo:
- **Dove il retry risolve l'errore**: quando il modello commette un errore sintattico di formato o di selezione dal catalogo correggibile rileggendo il prompt con temperatura `0.2`. Sul set Held-Out, il 3B recupera `HO_FAT_06` al 2° tentativo (dove al 1° tentativo aveva scritto la stringa `"null"` invece di `null` JSON nel campo `iban`, passando da `C3` a `C1`), mentre il 7B attiva il retry su 5 email bloccate da `CatalogMembershipGuard` nello step `recipient_inference` e ne recupera **2 su 5 (`HO_EML_01` e `HO_EML_11`)** a `C1`.
- **Dove il retry non può risolvere l'errore**: quando il dato manca del tutto o il destinatario corretto non viene individuato nel catalogo neppure dopo 2 tentativi aggiuntivi (es. `EML_12`, `EML_15`, `FAT_17` sul 3B Dev; `HO_EML_08`, `HO_EML_09`, `HO_EML_12` sul 7B Held-Out). In questi casi il sistema esaurisce i 3 tentativi e termina in `INDETERMINATE` (`C3`/`C4`), evitando comunque di emettere un output non valido come `OK`.
- **Risparmio netto dello Short-Circuit (`SOURCE_CHECKSUM_INVALID`)**: sui 15 documenti avversariali complessivi (`6` Held-Out + `9` Dev) in cui l'errore di checksum (Luhn, CIN, MOD-97) è scritto letteralmente nel testo di partenza, qualsiasi retry chiederebbe al modello di inventare o alterare un codice fiscale/P.IVA/IBAN non presente nel documento. Il controllo `DocumentContext.containsInSource` rileva la presenza letterale della stringa nel testo sorgente e interrompe il ciclo al **1° tentativo su 15/15 documenti** sia col 3B sia col 7B, risparmiando **30 chiamate LLM di retry su 30 (riduzione del 66.7% delle chiamate sui documenti avversariali)** a parità di intercettazione (100% `INDETERMINATE`).
