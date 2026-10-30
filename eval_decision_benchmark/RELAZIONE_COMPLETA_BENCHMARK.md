# Relazione Tecnica Completa — Benchmark e Validazione Empirica dei Modelli Decisionali

**Data**: 30 Ottobre 2026  
**Autore**: Antigravity Assistant & Engineering Team  
**Progetto**: SLM Orchestrator (Spring Boot 4 / Java 21 / Ollama)  
**Cartella Dedicata**: `eval_decision_benchmark/`  
**Hash SHA-256 Dataset Congelato**: `7192e7752015c914e72756fe02c0209c5a1a184545cb676b79f51d213995c754`

---

## Indice
1. [Obiettivo e Contesto dell'Indagine](#1-obiettivo-e-contesto-dellindagine)
2. [Fase A — Scoperta e Dimostrazione dell'Artefatto di Misura](#2-fase-a--scoperta-e-dimostrazione-dellartefatto-di-misura)
3. [Fasi A2, A2-bis e A2-ter — Correzioni Architetturali nel Codice di Produzione](#3-fasi-a2-a2-bis-e-a2-ter--correzioni-architetturali-nel-codice-di-produzione)
4. [Fase B — Costruzione e Congelamento del Dataset Difficile (Condizione B0)](#4-fase-b--costruzione-e-congelamento-del-dataset-difficile-condizione-b0)
5. [Risultati Sperimentali Dettagliati della Fase B (4.208 Prove Reali)](#5-risultati-sperimentali-dettagliati-della-fase-b-4208-prove-reali)
6. [Analisi Critica: Perché queste Percentuali sono Elevate e Cosa Insegnano](#6-analisi-critica-perch%C3%A9-queste-percentuali-sono-elevate-e-cosa-insegnano)
7. [Conclusioni e Linee Guida Architetturali per la Produzione](#7-conclusioni-e-linee-guida-architetturali-per-la-produzione)
8. [Mappa dei File e Istruzioni di Riproducibilità](#8-mappa-dei-file-e-istruzioni-di-riproducibilit%C3%A0)

---

## 1. Obiettivo e Contesto dell'Indagine

Nel sistema **SLM Orchestrator**, l'architettura ibrida neuro-simbolica assegna compiti diversi a due famiglie di modelli:
- **Modello Generativo (Qwen 2.5 3B/7B)**: estrazione strutturata di campi testo aperti in formato JSON Schema.
- **Modello Decisionale Discreto (APUS Reflex 4B)**: classificazione categorica a singolo token (`num_predict: 1`, `temperature: 0.0`) impiegato a monte come router globale ([`ApusReflexRouter`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/router/ApusReflexRouter.java)) e all'interno delle pipeline per la selezione deterministica da catalogo ([`DecisionPipelineStep`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/pipeline/DecisionPipelineStep.java)).

Una prima revisione empirica aveva evidenziato anomalie critiche:
1. Una presunta accuratezza del 79,7% (153/192 prove) su 8 campioni semplici;
2. Un apparente forte bias posizionale verso la prima opzione presentata (`A` al 100%, posizioni intermedie `B` e `C` al 62–68%);
3. Una frequente emissione della stringa `"The"` anziché della singola lettera `A`..`D`.

L'obiettivo di questa indagine è stato verificare se tali anomalie derivassero da un effettivo limite cognitivo del modello o da un **artefatto di misura**, correggere il codice di produzione e sottoporre il sistema a un benchmark rigoroso e non saturante su dataset complesso.

---

## 2. Fase A — Scoperta e Dimostrazione dell'Artefatto di Misura

### 2.1. L'Ipotesi del Troncamento del Ragionamento
APUS Reflex 4B è un modello derivato con capacità di *reasoning*. Con l'endpoint standard `/api/chat` di Ollama:
- Il runtime apre una sezione interna di pensiero (`<think> ... </think>`).
- Quando si impone `num_predict: 1`, il modello non genera la risposta finale, bensì il **primo token della catena di pensiero**, che tipicamente inizia con `"The"` (es. *"The user wants to classify..."*).
- Il token `"The"` finiva nel campo `message.thinking`, mentre `message.content` risultava vuoto. Di conseguenza, il client estraeva `"The"` o stringa vuota, interpretandola erroneamente come scelta fallita o attivando il fallback euristico.

### 2.2. Verifica Sperimentale (Step 1 e Step 2)
Nello Step 1 (72 chiamate esplorative) e nello Step 2 (768 chiamate sistematiche registrate in `results/phase_a_step2_raw.jsonl`), sono state messe a confronto 4 configurazioni candidate su tutte le 24 permutazioni di 8 campioni:
1. `chat_default_np1`: `/api/chat` default, `num_predict: 1`
2. `generate_np1`: `/api/generate`, `num_predict: 1`
3. `chat_think_false_np1`: `/api/chat` con `"think": false`, `num_predict: 1`
4. `chat_think_false_np32`: `/api/chat` con `"think": false`, `num_predict: 32`

### 2.3. Risultati della Fase A
| Configurazione | Token Validi (A..D) | Token Spuri ("The") | Accuratezza sui Token Validi | Accuratezza Globale | Invarianza Permutazioni |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `chat_default_np1` | 147 / 192 (76,6%) | 45 / 192 (23,4%) | **100,0%** (147/147) | 76,6% | 4 / 8 campioni |
| `generate_np1` | 147 / 192 (76,6%) | 45 / 192 (23,4%) | **100,0%** (147/147) | 76,6% | 4 / 8 campioni |
| **`chat_think_false_np1`** | **192 / 192 (100,0%)** | **0 / 192 (0,0%)** | **100,0%** (192/192) | **100,0% (192/192)** | **8 / 8 campioni (100%)** |
| `chat_think_false_np32` | 192 / 192 (100,0%) | 0 / 192 (0,0%) | **100,0%** (192/192) | **100,0% (192/192)** | **8 / 8 campioni (100%)** |

**Conclusione della Fase A**: Il bias posizionale e gli errori storici erano un **puro artefatto di misura**. Disattivando esplicitamente la modalità di pensiero discorsivo (`"think": false`), APUS Reflex 4B risponde con la singola lettera `A`..`D` direttamente in `message.content`, azzerando i token non ammessi ed eliminando ogni apparente positional bias sui campioni base (25,0% esatto per ciascuna posizione ordinale).

---

## 3. Fasi A2, A2-bis e A2-ter — Correzioni Architetturali nel Codice di Produzione

Le correzioni sono state integrate nella codebase Java con suite di test offline interamente verde (279 test passati):

1. **Supporto Dinamico a `think: false` (Commits `2b990be`, `d804a49`, `a37ca84`)**:
   - Parametro `Boolean think` aggiunto a [`ModelRequest`](src/main/java/com/tirocinioaltrama/slmorchestrator/core/model/ModelRequest.java) e [`OllamaRuntimeOptions`](src/main/java/com/tirocinioaltrama/slmorchestrator/config/ollama/OllamaRuntimeOptions.java).
   - In [`OllamaModelClient`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/model/OllamaModelClient.java) e [`ApusReflexRouter`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/router/ApusReflexRouter.java), la presenza della capability `thinking` viene verificata dinamicamente interrogando `/api/show` di Ollama, **senza liste hardcoded di modelli**.
   - **Politica di Cache Resiliente**: Solo le risposte riuscite con capabilities vengono memorizzate in cache; eventuali timeout o fallimenti di `/api/show` all'avvio non vengono memorizzati, consentendo il recupero trasparente della modalità `think: false` alle chiamate successive.
2. **Retry con Permutazione Ciclica Alternativa nello Step di Pipeline**:
   - In [`DecisionPipelineStep`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/pipeline/DecisionPipelineStep.java), ripetere la stessa richiesta identica a `temperature: 0.0` è deterministico e spreca solo tempo. Se il modello restituisce un token non ammesso, lo step effettua un retry **ruotando ciclicamente i candidati** ($shift = 1$):
     $$\text{Candidato}_i \rightarrow \text{Lettera}_i, \quad \text{IndiceOriginale} = (\text{IndiceRetry} + 1) \pmod N$$
   - Se anche il retry non produce un'opzione valida, lo step non tira a indovinare ma restituisce `ValidationErrorCode.INDETERMINATE`, preservando il testo originale per la revisione operatore.
3. **Mappatura Esplicita di `INDETERMINATE`**:
   - Tracciata esplicitamente in [`BasePipeline`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/pipeline/BasePipeline.java) e [`DefaultOrchestrator`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/DefaultOrchestrator.java), con test unitari dedicati.
4. **Riconciliazione dei Numeri Storici di Audit**:
   - La discrepanza tra il vecchio 153/192 (79,7%) e il 147/192 (76,6%) derivava dalla formula finale del prompt (`(A..D)` vs `(A, B, C, or D)`), che a thinking attivo spostava 6 prove dalla lettera secca al troncamento `"The"`.
   - Il valore **181/192 (94,3%)** rappresenta l'accuratezza del **Router comprensivo del Fallback Euristico a regole**, e non l'accuratezza del modello isolato.

---

## 4. Fase B — Costruzione e Congelamento del Dataset Difficile (Condizione B0)

Per evitare saturazioni (tutti 100%), è stato costruito il dataset [`dataset/phase_b_dataset.json`](dataset/phase_b_dataset.json) con le seguenti caratteristiche:
- **Numerosità**: 40 campioni per il Router (4 classi), 40 email per i 5 Ruoli aziendali, 16 campioni a contenuto Neutro.
- **Validazione Algebrica dei Checksum (B0.a)**:
  - Tutti i 12 Codici Fiscali verificati con l'algoritmo ministeriale (tabella caratteri pari/dispari).
  - Tutte le 4 Partite IVA verificate con l'algoritmo di Luhn.
  - Tutti i 4 IBAN verificati con lo standard ISO 7064 mod 97-10 (eliminato ogni IBAN placeholder `IT60X...`).
- **Composizione Bilanciata**: 10 campioni chiari/standard, 16 campioni borderline/ibridi (es. email che citano fatture e coordinate bancarie), 4 campioni a basso segnale/brevissimi, 10 campioni lunghi e multi-argomento.

### 12 Campioni Segnalati per la Revisione
1. `R_BOR_04` (Borderline): Autocertificazione di residenza con CF valido (`MCNRRT64S27E388Q`). Primary: `ANAGRAFICA`, Admissible: `['ANAGRAFICA', 'ALTRO']`.
2. `R_BOR_09` (Borderline): Fattura pro-forma / preventivo con clausola di non validità fiscale. Primary: `FATTURA`, Admissible: `['FATTURA', 'ALTRO']`.
3. `R_FAT_BRV_01` (Basso Segnale): `"Ft. 120/26: 450€ + IVA 22%. Saldo bonifico."`
4. `R_EML_BRV_01` (Basso Segnale): `"Re: ok per domani alle 15. Ti chiamo io. Ciao, L."`
5. `R_ANA_BRV_01` (Basso Segnale): `"CF: RSSMRA85T10H501O - MARIO ROSSI, ROMA 10/12/1985"`
6. `R_ALT_BRV_01` (Basso Segnale): `"Promemoria: sostituire toner stampante piano 2 entro venerdì."`
7. `R_BOR_01` (Ibrido): Sollecito di pagamento via email con fattura, importo e IBAN (`IT49Q0542811101000000984512`). Primary: `EMAIL`, Admissible: `['EMAIL', 'FATTURA']`.
8. `R_BOR_02` (Ibrido): Lettera di trasmissione nota pro-forma. Primary: `EMAIL`, Admissible: `['EMAIL', 'FATTURA']`.
9. `R_BOR_03` (Ibrido): Richiesta documenti HR con CF candidato (`BNCMRC88A12H501C`). Primary: `EMAIL`, Admissible: `['EMAIL', 'ANAGRAFICA']`.
10. `R_BOR_06` (Ibrido): Accordo transattivo legale a saldo e stralcio con bonifico. Primary: `ALTRO`, Admissible: `['ALTRO', 'FATTURA']`.
11. `R_BOR_07` (Ibrido): Documento di Trasporto (DDT) merci con valore assicurativo. Primary: `ALTRO`, Admissible: `['ALTRO', 'FATTURA']`.
12. `R_BOR_16` (Ibrido): Certificazione Unica (CU) con CF, imponibili e ritenute IRPEF. Primary: `FATTURA`, Admissible: `['FATTURA', 'ANAGRAFICA', 'ALTRO']`.

---

## 5. Risultati Sperimentali Dettagliati della Fase B (4.208 Prove Reali)

I dati derivano dall'elaborazione dei log grezzi archiviati in [`results/phase_b_raw.jsonl`](results/phase_b_raw.jsonl) tramite lo script [`scripts/analyze_phase_b.py`](scripts/analyze_phase_b.py).

### 5.1. Router su 4 Opzioni (24 Permutazioni Esaustive per Campione, 960 Invocazioni per Modello)
| Modello / Metodo | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Campioni 100% Invarianti | Token Non Validi (con `think: false`) |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **84,1%** (807/960) [81,6%–86,2%] | **98,3%** (944/960) [97,3%–99,0%] | **27 / 40** (67,5%) | **0 su 960 (0,0%)** |
| **Qwen-2.5-3B** | **67,1%** (644/960) [64,0%–70,0%] | **80,2%** (770/960) [77,6%–82,6%] | **6 / 40** (15,0%) | **1 su 960 (0,1%)** |
| *Baseline a Regole (Regex)* | 55,0% (22/40) [39,8%–69,3%] | 67,5% (27/40) [52,0%–79,9%] | Deterministica | N/A |
| *Baseline Casuale ($1/4$)* | 25,0% | 25,0% – 50,0% | N/A | N/A |

### 5.2. Distribuzione dell'Accuratezza per Posizione Ordinale (Positional Bias)
| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Delta (Max - Min) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 84,2% (202/240) | 82,1% (197/240) | 79,2% (190/240) | 90,8% (218/240) | **11,7%** |
| **Qwen-2.5-3B** | 63,3% (152/240) | 78,8% (189/240) | 85,4% (205/240) | 40,8% (98/240) | **44,6%** |

### 5.3. Scenari a 8 e 16 Opzioni (Rotazioni Cicliche Sistematiche)
| Modello | Opzioni | Prove Totali | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Baseline Casuale |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 8 opzioni | 320 | **71,9%** (230/320) [66,7%–76,5%] | **80,9%** (259/320) [76,3%–84,9%] | 12,5% |
| **Qwen-2.5-3B** | 8 opzioni | 320 | **46,2%** (148/320) [40,9%–51,7%] | **52,8%** (169/320) [47,3%–58,2%] | 12,5% |
| **APUS-Reflex-4B** | 16 opzioni | 320 | **59,1%** (189/320) [53,6%–64,3%] | **66,2%** (212/320) [60,9%–71,2%] | 6,25% |
| **Qwen-2.5-3B** | 16 opzioni | 320 | **34,4%** (110/320) [29,4%–39,7%] | **38,8%** (124/320) [33,6%–44,2%] | 6,25% |

### 5.4. Instradamento Email sui 5 Ruoli Aziendali (200 Invocazioni per Modello)
| Modello / Metodo | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Baseline Casuale |
| :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **88,5%** (177/200) [83,3%–92,2%] | **100,0%** (200/200) [98,1%–100,0%] | 20,0% |
| **Qwen-2.5-3B** | **59,0%** (118/200) [52,1%–65,6%] | **76,0%** (152/200) [69,6%–81,4%] | 20,0% |
| *Baseline a Regole* | 60,0% (24/40) [44,6%–73,7%] | 72,5% (29/40) [57,2%–83,9%] | 20,0% |

### 5.5. Contenuto Neutro: Test Chi-Quadro di Uniformità (B2)
Valutazione su 16 testi neutri $\times$ 4 rotazioni ordinali (64 prove per modello). Ipotesi nulla $H_0$: distribuzione uniforme sulle posizioni ($p = 0,25$).
| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Chi-Quadro ($\chi^2$) | p-value | Esito $H_0$ ($\alpha=0,05$) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 16/64 (25,0%) | 16/64 (25,0%) | 16/64 (25,0%) | 16/64 (25,0%) | **0,00** | **1,0000** | **Non Respinta (Uniforme)** |
| **Qwen-2.5-3B** | 16/64 (25,0%) | 16/64 (25,0%) | 28/64 (43,8%) | 4/64 (6,2%) | **18,00** | **0,0012** | **Respinta (Forte Bias su C e contro D)** |

### 5.6. Confronto Statistico Paired Cluster Bootstrap (B4)
Confronto a livello di cluster per singolo campione (10.000 ricampionamenti):
- **Differenza media di accuratezza ($\text{Reflex} - \text{Qwen}$)**: **+16,98%**
- **Intervallo di Confidenza Bootstrap 95%**: **[+8,12%, +26,25%]**
- **Valore p a due code**: **0,0000** ($p < 10^{-4}$)
- **Esito**: La superiorità di APUS Reflex rispetto a Qwen 2.5 3B è statisticamente significativa con $p < 0,001$.

### 5.7. Analisi della Latenza e TTFT (B5)
| Modello | Scenario | TTFT / Prompt Eval (ms) [Media / p50 / p95] | Decode (ms) | Latenza Totale Client (ms) [Media / p50 / p95] |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | Router 4 Opz | 776,3 / 722,1 / 1.140,1 | < 0,01 | **1.099,8 / 1.072,6 / 1.526,5** |
| **Qwen-2.5-3B** | Router 4 Opz | 211,4 / 170,1 / 477,4 | < 0,01 | **264,2 / 215,6 / 565,1** |
| **APUS-Reflex-4B** | Email 5 Ruoli | 646,7 / 617,1 / 869,1 | < 0,01 | **909,1 / 878,3 / 1.141,3** |
| **Qwen-2.5-3B** | Email 5 Ruoli | 165,5 / 160,0 / 220,3 | < 0,01 | **215,6 / 208,3 / 285,7** |

---

## 6. Analisi Critica: Perché queste Percentuali sono Elevate e Cosa Insegnano

1. **La presenza intenzionale di casi ibridi e borderline**:
   - Nei 40 campioni sono stati inseriti volutamente 16 casi trappola (email di sollecito con fattura allegata e IBAN, autocertificazioni con CF, documenti para-fiscali).
   - Sui campioni chiari e non ambigui, APUS Reflex supera il **95%**.
   - Sui casi ibridi, la scelta tra le due classi in gioco è semanticamente aperta; accettando entrambe le classi ammissibili (*accuratezza permissiva*), Reflex raggiunge il **98,3% sul router** e il **100,0% sull'assegnazione ruoli**.
2. **Il test a 24 permutazioni complete**:
   - Ogni testo è stato valutato scambiando l'ordine di tutte le opzioni in tutti i modi possibili. L'84,1% stretta su 960 prove riflette una stabilità decisionale reale, non un colpo di fortuna sull'ordine predefinito.
3. **Il limite architetturale a 8 e 16 opzioni**:
   - Al crescere delle categorie, i confini semantici sfumano (es. *Fattura* vs *Preventivo* vs *Ordine d'acquisto* vs *Nota spese*). Con un singolo token di readout e zero passaggi intermedi di pensiero, un modello SLM compatto non può discriminare 16 alternative sottili.
   - Il 59,1% a 16 opzioni resta comunque quasi 10 volte superiore alla scelta casuale (6,25%) e quasi il doppio di Qwen (34,4%).

---

## 7. Conclusioni e Linee Guida Architetturali per la Produzione

1. **Modello Confermato**: APUS Reflex 4B con `"think": false`, `"temperature": 0.0`, `"num_predict": 1` è confermato come standard per router e passi decisionali discreti.
2. **Architettura Gerarchica (Massimo 4–5 opzioni per step)**:
   - Evitare menù unici estesi a 8–16 alternative.
   - Adottare sempre un instradamento a due livelli: prima Router macro (4 categorie: Fattura, Email, Anagrafica, Altro), poi eventuale raffinamento specialistico all'interno della singola pipeline.
3. **Mitigazione Voto a k-Permutazioni**:
   - `kPermutations: 1` per il default operativo (latenza ~1 s).
   - `kPermutations: 3` (`minAgreementRatio: 0.66`) riservato a contesti ad alta criticità per intercettare l'oscillazione decisionale e deviare in sicurezza su `INDETERMINATE`.

---

## 8. Mappa dei File e Istruzioni di Riproducibilità

Tutti gli elementi del benchmark risiedono esclusivamente in questa cartella:

```
eval_decision_benchmark/
├── RELAZIONE_COMPLETA_BENCHMARK.md          # La presente relazione tecnica integrale
├── dataset/
│   └── phase_b_dataset.json                 # Dataset congelato con SHA-256 e checksum validi
├── scripts/
│   ├── run_phase_b_benchmark.py             # Esecutore delle 4.208 prove live su Ollama
│   ├── analyze_phase_b.py                   # Script di ricalcolo statistico deterministico
│   ├── sanitize_and_freeze_phase_b_dataset.py # Generatore e validatore algebrico dei checksum
│   ├── generate_phase_b_dataset.py          # Generatore sorgente del dataset
│   ├── phase_a_step1.py                     # Script Step 1 Fase A (analisi artefatto thinking)
│   ├── phase_a_step2.py                     # Script Step 2 Fase A (768 prove comparative)
│   └── analyze_phase_a.py                   # Analizzatore Fase A
└── results/
    ├── phase_b_raw.jsonl                    # Log grezzo di tutte le 4.208 chiamate live Fase B
    ├── phase_b_report.md                    # Report sintetico generato dallo script di analisi
    ├── phase_a_step1_raw.jsonl              # Log grezzo Step 1 Fase A
    └── phase_a_step2_raw.jsonl              # Log grezzo Step 2 Fase A
```

### Come Riprodurre l'Analisi
Per ricalcolare tutte le tabelle e metriche direttamente dai log grezzi senza rieseguire le chiamate a Ollama:
```bash
python3 eval_decision_benchmark/scripts/analyze_phase_b.py
```

### Come Rieseguire l'Intero Benchmark Live
Con Ollama attivo su `http://localhost:11434`:
```bash
python3 eval_decision_benchmark/scripts/run_phase_b_benchmark.py
```
*(Lo script supporta la ripresa automatica in append nel caso in cui il file di log contenga già record parziali).*
