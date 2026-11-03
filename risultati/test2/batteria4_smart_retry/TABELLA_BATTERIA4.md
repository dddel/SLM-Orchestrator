# Batteria 4: Efficacia dello Smart Retry (`RF7`), Cross-Field Coherence e Short-Circuit Avversariale

- **Commit Git (`freeze-1`)**: `254ae10d695e3160596026be7294cb2e9589c8f7`
- **Configurazioni a confronto**:
  1. `3B_CON_GUARDRAIL_SENZA_RETRY`: `qwen2.5:3b-instruct-q8_0`, Guardrail deterministico attivo, Cross-Field Coherence attiva, `max_retries=0` (pipeline caricate da `risultati/test2/benchmark_workspace/pipelines_no_retry` senza blocchi `on_failure`).
  2. `3B_CON_GUARDRAIL_E_RETRY`: `qwen2.5:3b-instruct-q8_0`, Guardrail deterministico attivo, Cross-Field Coherence attiva, `max_retries=2` con iniezione feedback errori tipizzati (`RF7`, senza valore rifiutato) e incremento temperatura `0.0 -> 0.2`.
  3. `7B_CON_GUARDRAIL_E_RETRY`: `qwen2.5:7b-instruct-q8_0`, Guardrail deterministico attivo, Cross-Field Coherence attiva, `max_retries=2` con `RF7` e incremento temperatura `0.0 -> 0.2`.
- **Nota sugli Step di Inferenza**: I documenti `FATTURA` e `ANAGRAFICA` prevedono **1 step base** (`fattura_inference` / `anagrafica_inference`), mentre i documenti `EMAIL` prevedono **2 step sequenziali distinti** (`email_inference` per i 9 campi di estrazione + `recipient_inference` per l'instradamento verso il catalogo `recipients_catalog.json`). Pertanto, su `heldout` (14 FATTURA + 14 ANAGRAFICA + 14 EMAIL) il numero minimo fisiologico di chiamate LLM senza alcun retry è `14 + 14 + 28 = 56`; su `dev` (20 FATTURA + 20 ANAGRAFICA + 20 EMAIL) è `20 + 20 + 40 = 80`. Un retry si attiva esclusivamente quando uno stesso `stepId` viene invocato `> 1` volta.
- **Meccanismo di Short-Circuit Avversariale (`SOURCE_CHECKSUM_INVALID`)**: Se un codice formalmente invalido (P.IVA Luhn, CF CIN, IBAN MOD-97) è presente *letteralmente* nel testo sorgente (`DocumentContext.containsInSource`), `OutputValidationStep` emette il codice `SOURCE_CHECKSUM_INVALID` e `BasePipeline.canShortCircuitSourceErrors` interrompe immediatamente il ciclo di retry dopo **1 sola chiamata LLM** (`n_inference_steps = 1`), evitando 2 retry inutili per documento.

## 1. Dataset Held-Out (`heldout_dataset_v1.json` — 42 documenti, 392 campi)

### Metriche di Accuratezza e Classificazione Documentale

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 31/42 (73.8%, IC 95%: 58.9–84.7%) | 10 (0/2/8) | 1 | 0 | 0 | 11 |
| `3B_CON_GUARDRAIL_E_RETRY` | 373/392 (95.2%, IC 95%: 92.6–96.9%) | 32/42 (76.2%, IC 95%: 61.5–86.5%) | 10 (0/2/8) | 0 | 0 | 0 | 11 |
| `7B_CON_GUARDRAIL_E_RETRY` | 389/392 (99.2%, IC 95%: 97.8–99.7%) | 36/42 (85.7%, IC 95%: 72.2–93.3%) | 3 (0/3/0) | 3 | 0 | 0 | 0 |

### Metriche di Smart Retry (`RF7`) e Chiamate di Inferenza

| Configurazione | Doc Totali | Chiamate LLM Totali | Doc con Retry (`stepId > 1`) | Chiamate Extra di Retry | Recuperati a `OK` (`C1`) | Esauriti in `INDETERMINATE` (`C3`/`C4`) | Latenza Media / Mediana (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 42 | 56 | 0 | +0 | 0 (0 `C1`) | 0 | 7767.7 / 7234.5 |
| `3B_CON_GUARDRAIL_E_RETRY` | 42 | 57 | 1 | +1 | 1 (1 `C1`) | 0 | 7173.8 / 6723.0 |
| `7B_CON_GUARDRAIL_E_RETRY` | 42 | 65 | 5 | +9 | 2 (2 `C1`) | 3 | N/A (split GPU/CPU) |

### Tracciato Analitico dei Documenti con Attivazione di Smart Retry (`RF7`)

| Configurazione | Doc ID (`Tipo`) | Step in Retry (`Tentativi`) | Tentativo 1 (`prompt`/`compl` tok, ms) | Tentativo 2 (`prompt`/`compl` tok, ms) | Tentativo 3 (`prompt`/`compl` tok, ms) | Causa Trigger / Errore Guardrail | Esito Finale (`Categoria`, Campi) |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| `3B_CON_GUARDRAIL_E_RETRY` | `HO_FAT_06` (`FATTURA`) | `fattura_inference` (2) | `669`/`138` tok (5306 ms) | `789`/`137` tok (5442 ms) | - | `iban: FORMAT_INVALID (iban)` (risolto al T2) | `OK` (`C1`, 8/8) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_01` (`EMAIL`) | `recipient_inference` (3) | `1479`/`68` tok (10637 ms) | `1611`/`58` tok (9596 ms) | `1639`/`50` tok (8269 ms) | `destinatario: VALUE_NOT_IN_CATALOG (catalog_membership)` (risolto al T3) | `OK` (`C1`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_08` (`EMAIL`) | `recipient_inference` (3) | `1424`/`74` tok (10550 ms) | `1556`/`65` tok (9531 ms) | `1565`/`65` tok (9816 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_09` (`EMAIL`) | `recipient_inference` (3) | `1366`/`61` tok (9040 ms) | `1526`/`64` tok (9262 ms) | `1535`/`63` tok (9339 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto), `ruoloDestinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_11` (`EMAIL`) | `recipient_inference` (2) | `1439`/`78` tok (16492 ms) | `1571`/`63` tok (14230 ms) | - | `destinatario: VALUE_NOT_IN_CATALOG (catalog_membership)` (risolto al T2) | `OK` (`C1`, 9/9) |
| `7B_CON_GUARDRAIL_E_RETRY` | `HO_EML_12` (`EMAIL`) | `recipient_inference` (3) | `1386`/`64` tok (9170 ms) | `1546`/`66` tok (9717 ms) | `1555`/`64` tok (9831 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto), `ruoloDestinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |

## 2. Set Avversariale Held-Out (`heldout_adversarial_v1.json` — 6 documenti: 2 P.IVA, 2 CF, 2 IBAN invalidi nel testo)

| Configurazione | Codici Invalidi Intercettati (`INDETERMINATE`) | Chiamate LLM Totali | Doc in Short-Circuit (`SOURCE_CHECKSUM_INVALID`) | Chiamate di Retry Risparmiate | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 6/6 (100.0%) | 6 | 6/6 | 0 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `3B_CON_GUARDRAIL_E_RETRY` | 6/6 (100.0%) | 6 | 6/6 | 12 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |
| `7B_CON_GUARDRAIL_E_RETRY` | 6/6 (100.0%) | 6 | 6/6 | 12 | 54/54 (100.0%, IC 95%: 93.4–100.0%) |

## 3. Dataset di Sviluppo (`dataset_60_v2.json` — 60 documenti, 560 campi)

### Metriche di Accuratezza e Classificazione Documentale

| Configurazione | Campi Corretti / Totali (Wilson 95%) | Doc C1 (Wilson 95%) | C2 (`C2a`/`C2b`/`C2c`) | C3 | C4 | C5 | `VALORE_SU_NULL_ATTESO` |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 0 |
| `3B_CON_GUARDRAIL_E_RETRY` | 549/560 (98.0%, IC 95%: 96.5–98.9%) | 49/60 (81.7%, IC 95%: 70.1–89.4%) | 8 (0/8/0) | 2 | 1 | 0 | 0 |
| `7B_CON_GUARDRAIL_E_RETRY` | 541/560 (96.6%, IC 95%: 94.8–97.8%) | 54/60 (90.0%, IC 95%: 79.9–95.3%) | 6 (2/4/0) | 0 | 0 | 0 | 0 |

### Metriche di Smart Retry (`RF7`) e Chiamate di Inferenza

| Configurazione | Doc Totali | Chiamate LLM Totali | Doc con Retry (`stepId > 1`) | Chiamate Extra di Retry | Recuperati a `OK` (`C1`) | Esauriti in `INDETERMINATE` (`C3`/`C4`) | Latenza Media / Mediana (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 60 | 80 | 0 | +0 | 0 (0 `C1`) | 0 | 7405.9 / 7125.5 |
| `3B_CON_GUARDRAIL_E_RETRY` | 60 | 86 | 3 | +6 | 0 (0 `C1`) | 3 | 6417.8 / 6197.5 |
| `7B_CON_GUARDRAIL_E_RETRY` | 60 | 78 | 0 | +0 | 0 (0 `C1`) | 0 | N/A (split GPU/CPU) |

### Tracciato Analitico dei Documenti con Attivazione di Smart Retry (`RF7`)

| Configurazione | Doc ID (`Tipo`) | Step in Retry (`Tentativi`) | Tentativo 1 (`prompt`/`compl` tok, ms) | Tentativo 2 (`prompt`/`compl` tok, ms) | Tentativo 3 (`prompt`/`compl` tok, ms) | Causa Trigger / Errore Guardrail | Esito Finale (`Categoria`, Campi) |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- | :---: |
| `3B_CON_GUARDRAIL_E_RETRY` | `EML_12` (`EMAIL`) | `recipient_inference` (3) | `1310`/`41` tok (2536 ms) | `1442`/`41` tok (2853 ms) | `1451`/`46` tok (3059 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `3B_CON_GUARDRAIL_E_RETRY` | `EML_15` (`EMAIL`) | `recipient_inference` (3) | `1326`/`39` tok (2478 ms) | `1458`/`39` tok (2612 ms) | `1467`/`39` tok (2841 ms) | `destinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto), `ruoloDestinatario: VALUE_NOT_IN_CATALOG (CatalogMembershipGuard)` (non risolto) | `INDETERMINATE` (`C3`, 9/9) |
| `3B_CON_GUARDRAIL_E_RETRY` | `FAT_17` (`FATTURA`) | `fattura_inference` (3) | `561`/`119` tok (4515 ms) | `685`/`119` tok (4492 ms) | `694`/`119` tok (4499 ms) | `numeroFattura: REQUIRED_EMPTY (required)` (non risolto) | `INDETERMINATE` (`C4`, 7/8) |

## 4. Set Avversariale Dev (`adversarial_piva_set.json` — 9 documenti con P.IVA invalida nel testo)

| Configurazione | Codici Invalidi Intercettati (`INDETERMINATE`) | Chiamate LLM Totali | Doc in Short-Circuit (`SOURCE_CHECKSUM_INVALID`) | Chiamate di Retry Risparmiate | Campi Estratti Fedeli al Testo (Wilson 95%) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `3B_CON_GUARDRAIL_SENZA_RETRY` | 9/9 (100.0%) | 9 | 9/9 | 0 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `3B_CON_GUARDRAIL_E_RETRY` | 9/9 (100.0%) | 9 | 9/9 | 18 | 70/72 (97.2%, IC 95%: 90.4–99.2%) |
| `7B_CON_GUARDRAIL_E_RETRY` | 9/9 (100.0%) | 9 | 9/9 | 18 | 71/72 (98.6%, IC 95%: 92.5–99.8%) |

