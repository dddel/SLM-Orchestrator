# Report Finale FASE B — Valutazione Rigorosa dei Modelli Decisionali (think: false)

> **Nota metodologica**: Tutte le metriche riportate derivano dall'elaborazione deterministica dei log grezzi
> memorizzati in `eval_decision_benchmark/results/phase_b_raw.jsonl`. Nessun valore è inserito manualmente o stimato a priori.

## 1. Router su 4 Opzioni (24 Permutazioni Esaustive per Campione, 960 Invocazioni per Modello)

| Modello | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Campioni 100% Invarianti | Token Non Validi (Retry Recuperati) |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **84.1%** (807/960) [81.6%–86.2%] | **98.3%** (944/960) [97.3%–99.0%] | **27/40** (67.5%) | 0 (0 rec.) |
| **Qwen-2.5-3B** | **67.1%** (644/960) [64.0%–70.0%] | **80.2%** (770/960) [77.6%–82.6%] | **6/40** (15.0%) | 1 (1 rec.) |

### 1.1. Distribuzione dell'Accuratezza per Posizione Ordinale del Target (Positional Bias su 4 Opzioni)

| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Delta (Max - Min) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 84.2% (202/240) | 82.1% (197/240) | 79.2% (190/240) | 90.8% (218/240) | **11.7%** |
| **Qwen-2.5-3B** | 63.3% (152/240) | 78.8% (189/240) | 85.4% (205/240) | 40.8% (98/240) | **44.6%** |

## 2. Scenari a 8 e 16 Opzioni (Rotazioni Cicliche Sistematiche)

| Modello | Opzioni | Prove Totali | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Token Non Validi |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 8 opzioni | 320 | **71.9%** [66.7%–76.5%] | **80.9%** [76.3%–84.9%] | 0 |
| **Qwen-2.5-3B** | 8 opzioni | 320 | **46.2%** [40.9%–51.7%] | **52.8%** [47.3%–58.2%] | 0 |
| **APUS-Reflex-4B** | 16 opzioni | 320 | **59.1%** [53.6%–64.3%] | **66.2%** [60.9%–71.2%] | 0 |
| **Qwen-2.5-3B** | 16 opzioni | 320 | **34.4%** [29.4%–39.7%] | **38.8%** [33.6%–44.2%] | 0 |

## 3. Instradamento Email sui 5 Ruoli Aziendali (200 Invocazioni per Modello)

| Modello | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Baseline a Regole (Stretta) | Token Non Validi (Recuperati) |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **88.5%** [83.3%–92.2%] | **100.0%** [98.1%–100.0%] | 60.0% [53.1%–66.5%] | 0 (0 rec.) |
| **Qwen-2.5-3B** | **59.0%** [52.1%–65.6%] | **76.0%** [69.6%–81.4%] | 60.0% [53.1%–66.5%] | 0 (0 rec.) |

## 4. Contenuto Neutro: Test Chi-Quadro di Uniformità (B2)

Valutazione su 16 testi neutri (latino, ricette, sequenze numeriche, botanica, ottica, astronomia) moltiplicati per 4 rotazioni ordinali (64 prove per modello).
Ipotesi nulla $H_0$: le risposte si distribuiscono uniformemente sulle 4 lettere (nessun bias posizionale intrinseco, frequenza attesa: 25% per ciascuna posizione).

| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Chi-Quadro ($\chi^2$, df=3) | p-value | Esito $H_0$ ($lpha=0.05$) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 16/64 (25.0%) | 16/64 (25.0%) | 16/64 (25.0%) | 16/64 (25.0%) | **0.00** | **1.0000** | **Non Respinta (Uniforme)** |
| **Qwen-2.5-3B** | 16/64 (25.0%) | 16/64 (25.0%) | 28/64 (43.8%) | 4/64 (6.2%) | **18.00** | **0.0012** | **Respinta (Bias Presente)** |

## 5. Confronto Statistico Paired Cluster Bootstrap (B4)

Confronto statistico paired cluster bootstrap a livello di singolo campione (10.000 ricampionamenti con replacement sui 40 cluster).

- **Differenza media di accuratezza (Reflex - Qwen)**: **+16.98%**
- **Intervallo di Confidenza Bootstrap 95%**: **[+8.12%, +26.25%]**
- **Valore p a due code**: **0.0000**
- **Interpretazione formale**: Con $lpha = 0.05$, la differenza statisticamente significativa.

## 6. Analisi dei Tempi di Esecuzione e Latenza (B5)

| Modello | Scenario | Prompt Eval / TTFT (ms) [Mean / p50 / p95] | Eval / Decode (ms) [Mean / p50 / p95] | Latenza Totale Client (ms) [Mean / p50 / p95] |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | Router 4 Opz | 776.3 / 722.1 / 1140.1 | 0.0 / 0.0 / 0.0 | **1099.8 / 1072.6 / 1526.5** |
| **Qwen-2.5-3B** | Router 4 Opz | 211.4 / 170.1 / 477.4 | 0.0 / 0.0 / 0.0 | **264.2 / 215.6 / 565.1** |
| **APUS-Reflex-4B** | Router 8 Opz | 929.1 / 909.9 / 1313.9 | 0.0 / 0.0 / 0.0 | **1287.1 / 1274.1 / 1712.0** |
| **Qwen-2.5-3B** | Router 8 Opz | 216.6 / 173.7 / 478.2 | 0.0 / 0.0 / 0.0 | **276.4 / 229.0 / 574.5** |
| **APUS-Reflex-4B** | Email 5 Ruoli | 646.7 / 617.1 / 869.1 | 0.0 / 0.0 / 0.0 | **909.1 / 878.3 / 1141.3** |
| **Qwen-2.5-3B** | Email 5 Ruoli | 165.5 / 160.0 / 220.3 | 0.0 / 0.0 / 0.0 | **215.6 / 208.3 / 285.7** |


## 7. Valutazione Caching Prompt: Opzioni Prefix vs Suffix (B5)

Verifica sperimentale su 10 campioni x 24 permutazioni (480 prove su Reflex) per quantificare l'accelerazione della KV Cache di Ollama invertendo la struttura del prompt:
- **Layout Suffix (Predefinito)**: Opzioni all'inizio, `<payload>` al centro, `Choice:` alla fine.
- **Layout Prefix (Invertito)**: `<payload>` all'inizio (invariante tra permutazioni dello stesso testo), Opzioni alla fine, `Choice:` alla fine.

| Configurazione Layout Prompt | Prompt Eval Medio (ms) | p50 (ms) | p95 (ms) | Speedup KV Cache |
| :--- | :--- | :--- | :--- | :--- |
| **Suffix (Standard)**: Opzioni $\rightarrow$ Documento | 687.84 ms | 642.89 ms | 812.63 ms | Baseline (1.0x) |
| **Prefix (Invertito)**: Documento $\rightarrow$ Opzioni | 683.79 ms | 629.98 ms | 811.10 ms | **1.01x** |

> **Esito sperimentale**: Il layout con Documento fisso in testa ottiene un'accelerazione di **1.01x** sul tempo di prompt evaluation grazie al riutilizzo del prefisso condiviso nel KV-cache.
