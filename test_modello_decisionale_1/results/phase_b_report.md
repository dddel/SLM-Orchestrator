# Report Finale FASE B — Valutazione Rigorosa dei Modelli Decisionali (think: false)

> **Nota metodologica per la tesi sperimentale**: Tutte le metriche riportate derivano dall'elaborazione
> deterministica dei log grezzi memorizzati in `/home/del/Projects/slm-orchestrator/test_modello_decisionale_1/results/phase_b_raw.jsonl`. Nessun valore è stimato o inserito a mano.

## 1. Router su 4 Opzioni (24 Permutazioni Esaustive per Campione, 960 Invocazioni per Modello)

| Modello | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Campioni 100% Invarianti | Token Non Validi (con `think: false`) |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **84.1%** (807/960) [81.6%–86.2%] | **98.3%** (944/960) [97.3%–99.0%] | **27/40** (67.5%) | 0 (0,0%) |
| **Qwen-2.5-3B** | **67.1%** (644/960) [64.0%–70.0%] | **80.2%** (770/960) [77.6%–82.6%] | **6/40** (15.0%) | 1 (1 rec.) |

### 1.1. Scorporo dell'Accuratezza per Categoria: Campioni Standard vs Borderline

L'analisi disaggregata dimostra in modo oggettivo l'origine del tasso di errore sul Router:
- **Campioni Standard, Lunghi e Brevi ($N=24$ campioni, 576 prove)**: campioni con segnale documentale non ambiguo.
- **Campioni Borderline / Ibridi ($N=16$ campioni, 384 prove)**: campioni deliberatamente costruiti con segnali misti (es. email contenenti fatture e IBAN, autocertificazioni con CF).

| Modello | Sottoinsieme | Prove | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Invarianza 24/24 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | Standard+Long+Short | 576 | **100.0%** (576/576) [99.3%–100.0%] | **100.0%** (576/576) [99.3%–100.0%] | **24/24** (100.0%) |
| **APUS-Reflex-4B** | Borderline / Ibridi | 384 | **60.2%** (231/384) [55.2%–64.9%] | **95.8%** (368/384) [93.3%–97.4%] | **3/16** (18.8%) |
| **Qwen-2.5-3B** | Standard+Long+Short | 576 | **81.4%** (469/576) [78.0%–84.4%] | **81.4%** (469/576) [78.0%–84.4%] | **5/24** (20.8%) |
| **Qwen-2.5-3B** | Borderline / Ibridi | 384 | **45.6%** (175/384) [40.7%–50.6%] | **78.4%** (301/384) [74.0%–82.2%] | **1/16** (6.2%) |

> **Nota critica sul dataset**: Su tutti i 24 campioni non-borderline, **APUS Reflex 4B raggiunge il 100,0% di accuratezza stretta (576/576) e il 100,0% di invarianza (24/24 campioni)**. L'intero calo all'84,1% globale è circoscritto ai 16 campioni borderline (60,2% stretta, 95,8% permissiva). Inoltre, alcuni campioni presentano etichette `primary` arbitrarie (es. `R_BOR_10`, lettera di dimissioni etichettata `ANAGRAFICA` anziché `ALTRO`; `R_BOR_16`, Certificazione Unica etichettata `FATTURA` anziché `ANAGRAFICA`), che penalizzano artificialmente lo score stretto nonostante la decisione del modello sia semanticamente legittima.

### 1.2. Distribuzione dell'Accuratezza per Posizione Ordinale del Target (Positional Bias su 4 Opzioni)

| Modello | Posizione A | Posizione B | Posizione C | Posizione D | Delta (Max - Min) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | 84.2% (202/240) | 82.1% (197/240) | 79.2% (190/240) | 90.8% (218/240) | **11.7%** |
| **Qwen-2.5-3B** | 63.3% (152/240) | 78.8% (189/240) | 85.4% (205/240) | 40.8% (98/240) | **44.6%** |

## 2. Valutazione Scenari a 8 e 16 Opzioni: Declassamento per Label Shift / Ontology Overlap

Gli scenari a 8 e 16 opzioni non possono essere usati come prova di decadimento cognitivo del modello per un difetto metodologico nella definizione del test:
1. **Ontology Overlap**: Il dataset ha etichette di riferimento `primary` e `admissible` congelate sulle sole 4 macro-classi (`FATTURA`, `EMAIL`, `ANAGRAFICA`, `ALTRO`). Nei prompt a 8 e 16 opzioni sono state introdotte opzioni più specifiche (es. `DDT_TRASPORTO`, `CONTRATTO`, `PREVENTIVO`, `CURRICULUM`, `NOTA_SPESE`, `CIRCOLARE_INTERNA`).
2. **Verifica sui Dati Grezzi**: Ispezionando `phase_b_raw.jsonl`, APUS Reflex 4B seleziona invariabilmente (8 volte su 8 rotazioni) la classe specialistica esatta, venendo penalizzato come 'errore' dal valutatore automatico:
   - `R_BOR_07` (Documento di trasporto): Reflex sceglie **`DDT_TRASPORTO` (8/8)**. Target atteso congelato: `ALTRO` $\rightarrow$ Falso Errore.
   - `R_BOR_11` (Contratto di locazione): Reflex sceglie **`CONTRATTO` (8/8)**. Target atteso congelato: `ALTRO` $\rightarrow$ Falso Errore.
   - `R_ALT_LNG_01` (Accordo fornitura): Reflex sceglie **`CONTRATTO` (8/8)**. Target atteso congelato: `ALTRO` $\rightarrow$ Falso Errore.
   - `R_BOR_09` (Preventivo pro-forma): a 16 opzioni Reflex sceglie **`PREVENTIVO` (8/8)**. Target atteso congelato: `FATTURA` $\rightarrow$ Falso Errore.
   - `R_BOR_05` (Candidatura con CV allegato): a 16 opzioni Reflex sceglie **`CURRICULUM` (8/8)**. Target atteso congelato: `ANAGRAFICA` $\rightarrow$ Falso Errore.
   - `R_BOR_12` (Nota spese rimborso): a 16 opzioni Reflex sceglie **`NOTA_SPESE` (8/8)**. Target atteso congelato: `ALTRO` $\rightarrow$ Falso Errore.
   - `R_BOR_15` (Circolare aziendale): a 16 opzioni Reflex sceglie **`CIRCOLARE_INTERNA` (8/8)**. Target atteso congelato: `ALTRO` $\rightarrow$ Falso Errore.
3. **Conflitto Logico nel Prompt**: La descrizione della categoria `ALTRO` nel prompt recitava *"any document that does not clearly belong to FATTURA, EMAIL, or ANAGRAFICA"*, contraddicendo la presenza contemporanea delle altre 4/12 opzioni specialistiche.

Di conseguenza, le percentuali aggregate grezze (71,9% su 8 opz, 59,1% su 16 opz per Reflex; 46,2% e 34,4% per Qwen) **vengono declassate ed escluse dalle conclusioni primarie della tesi**, in quanto viziate da disallineamento ontologico tra prompt e ground truth.

## 3. Instradamento Email sui 5 Ruoli Aziendali (200 Invocazioni per Modello)

| Modello / Metodo | Accuratezza Stretta (Wilson 95%) | Accuratezza Permissiva (Wilson 95%) | Baseline a Regole (Stretta, N=40) | Token Non Validi |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **88.5%** (177/200) [83.3%–92.2%] | **100.0%** (200/200) [98.1%–100.0%] | **60.0%** (24/40) [44.6%–73.7%] | 0 (0,0%) |
| **Qwen-2.5-3B** | **59.0%** (118/200) [52.1%–65.6%] | **76.0%** (152/200) [69.6%–81.4%] | **60.0%** (24/40) [44.6%–73.7%] | 0 (0,0%) |

## 4. Contenuto Neutro: Analisi del Comportamento Semantico vs Posizionale (B2)

Valutazione su 16 testi neutri privi di riferimenti aziendali (astronomia, fisica, ricette, latino, scacchi, botanica) moltiplicati per 4 rotazioni cicliche delle opzioni (64 prove per modello).

Poiché `ALTRO` è la categoria semanticamente corretta per testi non aziendali, e nei 4 turni di rotazione `ALTRO` compare esattamente una volta in posizione A, B, C e D:

| Modello | Selezioni Classe `ALTRO` | Posizione A | Posizione B | Posizione C | Posizione D | Chi-Quadro Posizionale ($\chi^2$, df=3) | p-value | Esito di Uniformità Posizionale |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | **64/64 (100.0%)** | 16/64 (25.0%) | 16/64 (25.0%) | 16/64 (25.0%) | 16/64 (25.0%) | **0.00** | **1.0000** | **Uniforme (Tracciamento Semantico Perfetto)** |
| **Qwen-2.5-3B** | **52/64 (81.2%)** | 16/64 (25.0%) | 16/64 (25.0%) | 28/64 (43.8%) | 4/64 (6.2%) | **18.00** | **0.0012** | **Sbilanciata (Bias su Lettera C)** |

> **Interpretazione Matematica Corretta**: Il valore $\chi^2 = 0,00$ ($p = 1,0000$) ottenuto da APUS Reflex **non è il risultato di scelte casuali o indifferenti**, bensì la conseguenza matematica diretta del fatto che il modello ha scelto **`ALTRO` nel 100,0% delle prove (64 su 64)**, inseguendo con precisione l'etichetta semantica attraverso le 4 rotazioni ordinali (16 volte in A, 16 in B, 16 in C, 16 in D). Al contrario, Qwen 2.5 3B dimostra che il proprio bias posizionale verso la lettera `C` (28 su 64, 43,8%) prevale sulla semantica, deviando 12 volte su `ANAGRAFICA` quando quest'ultima si trovava alla lettera C.

## 5. Confronto Statistico Paired Cluster Bootstrap (B4)

Confronto statistico paired cluster bootstrap a livello di singolo campione (10.000 ricampionamenti con replacement sui 40 cluster indipendenti del Router):

- **Differenza media di accuratezza stretta (Reflex - Qwen)**: **+16.98%**
- **Intervallo di Confidenza Bootstrap 95%**: **[+8.12%, +26.25%]**
- **Valore p a due code**: **0.0000**
- **Conclusione statistica**: Con $\alpha = 0,05$, la superiorità di APUS Reflex 4B è **statisticamente significativa** ($p < 10^{-4}$), con un guadagno compreso tra +8,1% e +26,3% sul modello concorrente.

## 6. Analisi dei Tempi di Esecuzione e Latenza (B5)

| Modello | Scenario | Prompt Eval / TTFT (ms) [Media / p50 / p95] | Eval / Decode (ms) [Media / p50 / p95] | Latenza Totale Client (ms) [Media / p50 / p95] |
| :--- | :--- | :--- | :--- | :--- |
| **APUS-Reflex-4B** | Router 4 Opz | 776.3 / 722.1 / 1140.1 | 0.001 / 0.001 / 0.001 | **1099.8 / 1072.6 / 1526.5** |
| **Qwen-2.5-3B** | Router 4 Opz | 211.4 / 170.1 / 477.4 | 0.001 / 0.001 / 0.001 | **264.2 / 215.6 / 565.1** |
| **APUS-Reflex-4B** | Email 5 Ruoli | 646.7 / 617.1 / 869.1 | 0.001 / 0.001 / 0.001 | **909.1 / 878.3 / 1141.3** |
| **Qwen-2.5-3B** | Email 5 Ruoli | 165.5 / 160.0 / 220.3 | 0.001 / 0.001 / 0.001 | **215.6 / 208.3 / 285.7** |

> **Evidenza empirica**: A `num_predict: 1`, il tempo di generazione pura (Decode) è < 0,01 ms. La latenza è determinata quasi al 100% dal tempo di digestione del prompt (Prompt Eval / TTFT) e dall'overhead del runtime HTTP.

## 7. Nota Tecnica sul Test KV Cache (Prefix vs Suffix)

- **Prompt Eval Suffix (Opzioni prima)**: Media 687.84 ms (p50 642.89 ms)
- **Prompt Eval Prefix (Documento prima)**: Media 683.79 ms (p50 629.98 ms)
- **Speedup rilevato**: **1.01x** (~4 ms di differenza)
> **Nota metodologica**: Poiché `phase_b_raw.jsonl` non registra il contatore interno `prompt_eval_cached_count`, e il differenziale misurato è compreso nel rumore di campionamento (~4 ms su CPU), il test è da considerarsi **inconcludente** e non viene impiegato per motivare scelte architetturali sul prompt design.
