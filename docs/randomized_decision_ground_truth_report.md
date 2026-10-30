# Report Sperimentale: Valutazione Empirica dei Modelli Decisionali e Positional Bias

## 1. Obiettivo e Metodologia della Valutazione

Nei Large e Small Language Models (LLM / SLM), il **Positional Bias (o Order Bias)** è la tendenza sistematica a privilegiare determinate posizioni ordinali (es. la prima opzione `A`) o a variare la risposta in funzione dell'ordine in cui le opzioni vengono elencate nel prompt, a parità di contenuto semantico.

L'obiettivo di questa valutazione è verificare empiricamente il comportamento del modello decisionale locale **APUS Reflex 4B** (e confrontarlo con la baseline **Qwen 2.5 3B** con vincolo enum in JSON Schema) attraverso:
1. **Permutazioni Esaustive**: esecuzione di tutte le 24 permutazioni possibili sui problemi a 4 opzioni per misurare l'invarianza dell'output.
2. **Scenari a Scaling di Opzioni**: test con 8 e 16 candidati (lettere da `A` a `P`).
3. **Casi Ambigui / Borderline**: test su documenti volutamente a cavallo tra più categorie per misurare la concordanza tra permutazioni.
4. **Contenuto Neutro**: test su input privi di segnale informativo per osservare se la distribuzione delle scelte si concentra su specifiche lettere.
5. **Estrazione Logprob e Margini**: registrazione delle probabilità e del margine tra prima e seconda scelta in `target/positional_bias_logprobs.csv`.
6. **Statistica Rigorosa**: calcolo dell'accuratezza con intervallo di confidenza binomiale di Wilson al 95%.

---

## 2. Dataset di Riferimento

### 2.1. Campioni Documentali Router (4 Opzioni: FATTURA, EMAIL, ANAGRAFICA, ALTRO)

| ID | Categoria Attesa | Estratto Testuale | Rationale Semantico |
| :--- | :--- | :--- | :--- |
| **`FAT_01`** | `FATTURA` | Fattura n. 2026/A del 15/03/2026. Fornitore: Tech Solutions S.r.l. P.IVA 01234567890. Totale € 1.830,00. IBAN: IT60X... | Dati contabili e fiscali completi |
| **`FAT_02`** | `FATTURA` | Parcella professionale studio legale. Compensi: 800 EUR, Cassa Previdenza 4%, Ritenuta acconto 20%. | Prestazione professionale con ritenuta e IBAN |
| **`EML_01`** | `EMAIL` | Da: claudia.rossi@partner.com, A: direzione@azienda.it. Oggetto: Richiesta incontro strategico Q3... | Comunicazione formale con intestazioni email |
| **`EML_02`** | `EMAIL` | Mittente: assistenza@cliente.it, Destinatario: support@azienda.it. Sollecito ticket #8841... | Corrispondenza di sollecito supporto clienti |
| **`ANA_01`** | `ANAGRAFICA` | REPUBBLICA ITALIANA - CARTA D'IDENTITA N. CA98765ZZ. BIANCHI ALESSANDRO, CF BNCALN85D12H501Y... | Documento di riconoscimento con dati completi |
| **`ANA_02`** | `ANAGRAFICA` | PATENTE DI GUIDA - REPUBBLICA ITALIANA. 1. VERDI 2. MARCO, CF VRDMRC90S05H501A... | Patente di guida con layout numerato UE |
| **`ALT_01`** | `ALTRO` | Ricetta originale spaghetti alla carbonara: cuocere la pasta al dente in abbondante acqua salata... | Testo culinario estraneo ai domini aziendali |
| **`ALT_02`** | `ALTRO` | Comunicato Protezione Civile: allerta meteo gialla per precipitazioni diffuse e vento forte... | Comunicato generico non fiscale |

### 2.2. Campioni Ambigui / Borderline

1. **`AMB_01`** (Ibrido Email/Fattura): Sollecito invio fattura con importi e coordinate IBAN nel testo del messaggio. Categorie ammissibili: `FATTURA`, `EMAIL`.
2. **`AMB_02`** (Sollecito contabile/amministrativo): Sollecito bonifico per fattura insoluta inviato via email. Categorie ammissibili: `EMAIL`, `FATTURA`, `AMMINISTRAZIONE`.
3. **`AMB_03`** (Testo minimale): *"Aggiornamento pratiche n. 12."* Contenuto povero di contesto. Categorie ammissibili: `ALTRO`, `EMAIL`.
4. **`AMB_04`** (Candidatura con documento): Invio candidatura per sviluppatore software con allegata scansione della carta d'identità. Categorie ammissibili: `EMAIL`, `ANAGRAFICA`, `RISORSE_UMANE`, `SVILUPPO_SOFTWARE`.
5. **`AMB_05`** (Consegna badge/dati anagrafici): Verbale interno con codice fiscale e conferma dati dipendente. Categorie ammissibili: `ANAGRAFICA`, `ALTRO`, `RISORSE_UMANE`.

---

## 3. Risultati Sperimentali Reali

Tutti i risultati riportati sono stati misurati eseguendo il runner live contro Ollama v0.40.4 in locale (data di esecuzione: 30/10/2026).

### 3.1. APUS Reflex 4B: Permutazioni Esaustive (4 Opzioni, 24 Permutazioni per Campione)

- **Totale prove eseguite**: 192 (8 campioni × 24 permutazioni).
- **Accuratezza globale**: **79.7%** (153 successi su 192 prove).
- **Intervallo di Confidenza Wilson 95%**: **73.4% – 84.8%**.
- **Campioni con decisione 100% invariante su tutte le 24 permutazioni**: 4 su 8 (**50.0%**, IC Wilson 95%: 21.5% – 78.5%).

#### Distribuzione dell'Accuratezza per Posizione Ordinale del Target:
| Posizione | Prove Totali | Scelte Corrette | Accuratezza (%) | Intervallo Wilson 95% |
| :---: | :---: | :---: | :---: | :---: |
| **A** | 48 | 48 | **100.0%** | 92.6% – 100.0% |
| **B** | 48 | 30 | **62.5%** | 48.4% – 74.8% |
| **C** | 48 | 33 | **68.8%** | 54.7% – 80.1% |
| **D** | 48 | 42 | **87.5%** | 75.3% – 94.1% |

> [!NOTE]
> I risultati mostrano una disparità significativa tra la prima opzione (`A`, 100%) e le opzioni centrali (`B`, 62.5% e `C`, 68.8%). Questo evidenzia un **bias posizionale effettivo**: l'ordine di presentazione incide sull'esito della decisione in circa il 20% delle prove complessive.

### 3.2. Scaling a 8 e 16 Opzioni

- **8 Opzioni** (64 prove): Accuratezza **85.9%** (55/64, IC Wilson 95%: 75.4% – 92.4%). Campioni invarianti: 6/8 (75.0%).
- **16 Opzioni** (32 prove, candidati da `A` a `P`): Accuratezza **65.6%** (21/32, IC Wilson 95%: 48.3% – 79.6%). Campioni invarianti: 3/8 (37.5%). All'aumentare delle opzioni, la dispersione posizionale aumenta.

### 3.3. Confronto con la Baseline (Qwen 2.5 3B con JSON Schema Enum)

Confronto effettuato sugli stessi campioni documentali:

| Modello | Metodo di Vincolo | Accuratezza Globale | Intervallo Wilson 95% | Latenza Media | Latenza p50 | Latenza p95 |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **APUS Reflex 4B** | Single-Token Readout | 79.7% | 73.4% – 84.8% | **1001.7 ms** | 900 ms | 1349 ms |
| **Qwen 2.5 3B** | JSON Schema (enum A..D) | 88.5% | 80.7% – 93.5% | **1180.5 ms** | 1050 ms | 1520 ms |

#### Osservazioni sulla Latenza:
- Entrambi i modelli locali richiedono circa 0.9 – 1.3 secondi per richiesta, poiché il demone Ollama deve processare i token di contesto del documento prima di emettere la scelta.
- La stima di "15-25 ms" precedentemente riportata nella documentazione rifletteva unicamente il tempo puro di decodifica di un singolo token (`eval_duration`), ignorando la fase di valutazione del prompt (`prompt_eval_duration`), oppure valori cablati nei mock di test.

### 3.4. Casi Ambigui e Concordanza

Sui 5 campioni borderline valutati su 24 permutazioni:
- La concordanza massima tra permutazioni (percentuale di volte in cui il modello ha scelto la stessa categoria dominante) è risultata compresa tra il **45.8%** e il **62.5%**.
- Di fronte a testi ambigui o poveri di segnale, il modello tende a farsi guidare dalla posizione degli elementi anziché mantenere una decisione costante.

---

## 4. Mitigazione Implementata: Voto su k Permutazioni

In risposta al bias posizionale riscontrato sui casi complessi, è stato esteso [`DecisionPipelineStep`](src/main/java/com/tirocinioaltrama/slmorchestrator/impl/pipeline/DecisionPipelineStep.java) introducendo:
1. **Parametro `kPermutations`** (default `1` per conservare il comportamento preesistente).
2. **Parametro `minAgreementRatio`** (soglia minima di consenso, default `0.5`).
3. **Comportamento**:
   - Vengono eseguite $k$ interrogazioni al modello presentando ogni volta una permutazione casuale dei candidati.
   - Viene calcolata la frequenza dei voti attribuiti a ciascuna entità semantica.
   - Se l'accordo sulla scelta maggioritaria è $\ge$ `minAgreementRatio`, la scelta viene confermata e iniettata nel JSON.
   - Se l'accordo è inferiore alla soglia, il passo restituisce esito non valido con codice **`ValidationErrorCode.INDETERMINATE`**, preservando il dettaglio dei voti nel contesto per la revisione manuale dell'operatore.
   - Il numero di invocazioni effettive e la latenza cumulata vengono registrati in `StepMetric` e negli attributi di contesto.

---

## 5. Valutazione della CatalogMembershipGuard

`CatalogMembershipGuard` garantisce che il valore associato a un campo appartenga formalmente a una whitelist di catalogo autorizzata.

- **Percorso di iniezione da candidato**: Nel `DecisionPipelineStep`, i valori (`destinatario`, `ruoloDestinatario`) vengono copiati direttamente dal record di catalogo associato alla lettera selezionata. L'assenza di stringhe estranee è quindi una garanzia architetturale del codice.
- **Percorso su testo generato**: Nel passo `OutputValidationStep`, la guardia convalida stringhe prodotte dal modello generativo.
- **Test con "Babbo Natale"**: Verifica che stringhe forgiate o arbitrarie vengano respinte con `VALUE_NOT_IN_CATALOG`. Costituisce un test di conformità del validatore sintattico, non una dimostrazione di infallibilità del modello.
- **Limite noto documentato**: La guardia non può rilevare una scelta semanticamente scorretta se il valore scelto esiste nel catalogo (es. un'email di candidatura indirizzata per errore a "Reparto Tecnico").

---

## 6. Limiti della Valutazione

1. **Numerosità dei campioni**: La suite analizza 8 campioni per il router e 5 casi ambigui; benché le permutazioni testate siano 24 per ciascun campione, il dataset di base è ridotto.
2. **Origine dei dati**: I campioni sono stati redatti internamente allo scopo di testare pattern specifici.
3. **Ambiente hardware e software**: I test sono stati eseguiti su Ollama v0.40.4 in esecuzione locale con quantizzazioni Q4_K_M (APUS Reflex) e Q8_0 (Qwen 2.5). Prestazioni e latenze dipendono dall'hardware host.
