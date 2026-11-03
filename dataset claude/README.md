# Dataset Sintetico per il Benchmark Comparativo (Modelli Decisionali vs Generativi)

Questo repository locale contiene un dataset di valutazione composto da **1.470 campioni**, progettato specificamente per confrontare in modo imparziale e rigoroso modelli decisionali (es. modelli vincolati con `think: false`, `num_predict: 1` tipo Reflex) e modelli generativi standard (es. Qwen con schema JSON ed enum vincolati).

Tutti i dati, le etichette e gli script di generazione e audit sono autocontenuti in questa cartella.

---

## 1. Natura del Dataset: Generazione Sintetica Procedurale

Il dataset **non deriva da scraping incontrollato né da annotazioni manuali soggette a bias**, ma è **generato proceduralmente via script deterministico** ([`generate_decision_dataset.py`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/generate_decision_dataset.py)).

### Perché un dataset sintetico generato?
1. **Controllo totale delle variabili sperimentali**: Permette di isolare con precisione chirurgica specifici fenomeni linguistici, strutturali e di formattazione (es. posizione ordinale delle opzioni, conflitti lessicali, rumore OCR, thread annidati).
2. **Ground Truth esatta per costruzione (*by design*)**: A differenza dei dataset annotati da esseri umani (frequentemente affetti da disaccordo tra annotatori e ambiguità), ogni campione nasce da una specifica formale precisa:
   - Classe o ruolo target assegnato deterministicamente prima della stesura del testo.
   - Template sintattici e semantici controllati con parametri randomizzati tramite generatore pseudo-casuale deterministico.
3. **Validità algebrica dei token critici**: I documenti aziendali contengono identificativi fiscali e bancari reali. Gli algoritmi di generazione implementati in [`generate_decision_dataset.py`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/generate_decision_dataset.py) generano token formalmente e algebricamente validi al 100%:
   - **Codice Fiscale** (157 token nel dataset): validati con l'algoritmo ministeriale per i caratteri pari/dispari e codice di controllo CIN finale.
   - **Partita IVA** (236 token): conformi al calcolo del checksum con formula di Luhn (modulo 10).
   - **IBAN** (236 token): conformi allo standard ISO 13616 / modulo 97 con codice CIN di controllo e prefisso internazionale `IT`.
   - *Garanzia sul rumore OCR*: Il rumore OCR introdotto artificialmente per simulare scansioni difettose altera unicamente il testo perimetrale, senza mai corrompere i token numerici/fiscali.

---

## 2. Perché Funziona: Il Ruolo Fondamentale degli Hash Crittografici (SHA-256)

L'intero valore scientifico e la replicabilità del benchmark poggiano sulla **tracciabilità crittografica bit-a-bit tramite hash SHA-256**.

### Riproducibilità Assoluta (Deterministica al 100%)
Il generatore accetta un seed pseudocasuale fissato a priori:
```bash
SEED: 20261030
```
Fissando questo seed, qualsiasi esecuzione del generatore produce riga per riga e byte per byte l'identico output su qualsiasi macchina o sistema operativo:
```bash
python3 generate_decision_dataset.py --seed 20261030 --n-route 160 --n-email 200 --out OUT_DIR
```

### Il Sigillo del Manifest (`manifest.json`)
Il file [`manifest.json`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/manifest.json) congela in modo immutabile l'impronta crittografica SHA-256 del dataset completo:
```json
{
 "seed": 20261030,
 "sha256_dataset_all": "be1f22eca003d174aa13ee6bed336795dd99b41152cc8a14d817006fe46e86e6",
 "n": 1470,
 "counts": {
  "router": 640,
  "email_role": 800,
  "neutral": 30
 }
}
```

### Perché gli SHA garantiscono la validità scientifica:
- **Zero Cherry-Picking / Anti-Manipolazione**: Impedisce qualsiasi ritocco manuale *post-hoc* di testi, prompt o etichette. Nessun dato può essere modificato per "accomodare" o favorire l'uno o l'altro modello di intelligenza artificiale.
- **Rilevazione Immediata delle Manomissioni**: Anche la modifica di un singolo spazio, accento o etichetta altera irreversibilmente l'hash SHA-256, determinando il fallimento immediato dell'audit di integrità.
- **Isolamento Rigido tra Dev e Test (Zero Data Leakage)**: Gli split `dataset_dev.jsonl` e `dataset_test.jsonl` sono generati a partire da partizioni rigorosamente disgiunte di template e richieste. Il seed congelato e verificato con SHA garantisce che nessun template di test sia mai trapelato nella fase di sviluppo o taratura dei prompt.

### Audit Indipendente Automatizzato ([`audit_decision_dataset.py`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/audit_decision_dataset.py))
Per eliminare qualsiasi rischio di circolarità o autocertificazione, il codice di audit è scritto separatamente dal generatore:
1. **Riesecuzione in Sandbox**: Crea una directory temporanea, richiama il generatore subprocess e calcola l'hash SHA-256 dell'output generato, confrontandolo con il file locale (`same = hashlib.sha256(...) == hashlib.sha256(...)`). Se differiscono anche di un solo bit, l'audit solleva un errore bloccante.
2. **Verifica Indipendente dei Checksum**: Riesegue gli algoritmi di controllo (CF, P.IVA, IBAN) con logica autonoma su tutti i testi.
3. **Verifica delle Regole di Etichettatura**: Controlla la coerenza formale delle etichette sul testo pulito (es. intestazioni Da/A per EMAIL, marcatori fiscali per FATTURA, assenza di richieste per ruoli non assegnati).
4. **Mutation Testing**: Lo script include un test di mutazione che altera artificialmente la ground truth in 6 modi (es. scambio etichetta, alterazione checksum, ID duplicato); l'audit ha intercettato con successo il 100% delle mutazioni.

Esecuzione dell'audit:
```bash
python3 audit_decision_dataset.py . 20261030 160 200 generate_decision_dataset.py
```
Risultato registrato in [`audit_results.txt`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/audit_results.txt):
```text
campioni: 1470 {'email_role': 800, 'router': 640, 'neutral': 30}
token verificati: {'CF': 157, 'IBAN': 236, 'PIVA': 236}
router/dev classi: {'ANAGRAFICA': 40, 'FATTURA': 40, 'EMAIL': 40, 'ALTRO': 40}
router/test classi: {'ANAGRAFICA': 120, 'FATTURA': 120, 'ALTRO': 120, 'EMAIL': 120}
router strati: {'standard': 238, 'conflitto_lessicale': 203, 'breve': 129, 'lungo_distrattori': 70}
router con rumore OCR: 60
email_role/dev classi: {'risorse_umane': 40, 'reparto_tecnico': 40, 'amministrazione': 40, 'legale': 40, 'direzione': 40}
email_role/test classi: {'risorse_umane': 120, 'reparto_tecnico': 120, 'legale': 120, 'direzione': 120, 'amministrazione': 120}
email_role strati: {'lungo_distrattori': 130, 'breve': 135, 'thread': 130, 'conflitto_contesto': 270, 'standard': 135}
email_role con rumore OCR: 76
testi duplicati (eccedenza): 0 {}
rigenerazione identica: True
ANOMALIE: 0
```

---

## 3. Struttura del Dataset e Task di Valutazione

### File Inclusi
| File | Descrizione |
|---|---|
| [`dataset_all.jsonl`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/dataset_all.jsonl) | Tutti i 1.470 campioni uniti (Router + Email + Neutrali). |
| [`dataset_dev.jsonl`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/dataset_dev.jsonl) | 360 campioni (160 Router + 200 Email) riservati allo sviluppo di prompt e taratura soglie. |
| [`dataset_test.jsonl`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/dataset_test.jsonl) | 1.080 campioni (480 Router + 600 Email) congelati per il benchmark finale a cieco. |
| [`neutral.jsonl`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/neutral.jsonl) | 30 testi scientifico/letterari privi di segnale aziendale, usati per verificare il bias posizionale con test del Chi-Quadro ($\chi^2$). |
| [`manifest.json`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/manifest.json) | Metadati del dataset, conteggi per classe e hash SHA-256 di riferimento. |
| [`generate_decision_dataset.py`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/generate_decision_dataset.py) | Script generatore procedurale guidato da seed. |
| [`audit_decision_dataset.py`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/audit_decision_dataset.py) | Script di audit indipendente con verifica crittografica SHA-256 e validazione formale. |
| [`baseline_lexical.py`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/baseline_lexical.py) | Script di baseline a regole / parole-spia. |
| [`baseline_results.txt`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/baseline_results.txt) | Risultati empirici delle baseline a regole sul set di test. |

### Schema dei Campioni JSONL
Ogni record JSONL contiene i seguenti campi:
- `id`: Identificativo univoco del campione (es. `R_DEV_0001`, `E_TST_0042`).
- `task`: Compito di riferimento (`router` oppure `email_role`).
- `split`: Partizione di appartenenza (`dev` oppure `test`).
- `label`: Etichetta target di ground truth (es. `FATTURA`, `risorse_umane`).
- `strata`: Strato di complessità (es. `standard`, `conflitto_lessicale`, `lungo_distrattori`, `thread`).
- `template`: Nome del template generatore utilizzato.
- `text`: **Il testo documentale completo da fornire ai modelli in ingresso.**
- `clean_text`: Versione priva di rumore OCR (utilizzata esclusivamente per l'audit).
- `ask`, `ctx_roles`: Presenti nei compiti email per tracciare la richiesta attiva e i reparti menzionati nel contesto.

> [!IMPORTANT]
> Ai sistemi LLM in fase di valutazione va fornito esclusivamente il campo `text`. Gli altri campi servono unicamente al framework di valutazione e all'audit.

---

## 4. Specifiche dei Task e Regole Formali

### Task 1 — Router Documentale a 4 Classi (`FATTURA`, `EMAIL`, `ANAGRAFICA`, `ALTRO`)
Regola fondante stabilita prima della generazione: **la classe segue la forma del documento**.
- `FATTURA`: Titolo formale (Fattura/Invoice), presenza di imponibile e IVA/VAT.
- `EMAIL`: Presenza delle intestazioni `Da:` / `A:` (`From:` / `To:`), anche se nel corpo cita o include documenti terzi.
- `ANAGRAFICA`: Scheda anagrafica o dati identificativi di persone o enti, in assenza di transazioni o comunicazioni.
- `ALTRO`: Tutti gli altri documenti. **Per definizione esplicita di business, preventivi e fatture pro-forma ricadono in `ALTRO`** (non sono fatture definitive, anche in presenza di imponibile e IVA).

**Strati di complessità (Test Set: 480 campioni)**:
- `standard` (182): documenti con struttura canonica chiara.
- `breve` (77): note o testi sintetici ad alta densità informativa.
- `conflitto_lessicale` (151): casi sfidanti (es. preventivi con importi ed esenzione IVA, DDT con causali, email che inoltrano fatture contabili, schede fornitore con coordinate bancarie).
- `lungo_distrattori` (70): documenti prolissi con sezioni multiple, tabelle e date eterogenee.
- Circa il 15% dei casi standard e brevi include rumore OCR controllato.

### Task 2 — Instradamento Email sui 5 Ruoli Aziendali
Classi target: `direzione`, `risorse_umane`, `reparto_tecnico`, `amministrazione`, `legale`.

Regola fondante: **il ruolo assegnato è unicamente quello responsabile dell'azione richiesta nelle righe NON citate (corpo attivo del messaggio).**
- Il contesto può evocare altri reparti o citare comunicazioni pregresse.
- Ogni testo contiene richieste di un solo ruolo; l'audit verifica l'assenza di richieste confliggenti nella porzione attiva.

**Strati di complessità (Test Set: 600 campioni)**:
- `standard` (100) e `breve` (100).
- `conflitto_contesto` (200): contiene 2 o più frasi di contesto relative ad altri reparti, ma la richiesta di fondo è indirizzata a un singolo ufficio.
- `thread` (100): messaggio nuovo in testa con richiesta specifica, seguito sotto da un thread inoltrato con argomenti di reparti differenti.
- `lungo_distrattori` (100): 3 o più frasi di contesto fuorvianti su altri reparti.

---

## 5. Dimostrazione di Difficoltà: Baseline Lessicali

Per confermare che il dataset richieda effettiva capacità decisionale o comprensione semantica, e non sia banalmente aggirabile con espressioni regolari o parole-chiave ("keyword matching"), sono state eseguite baseline deterministiche sul test set ([`baseline_results.txt`](file:///home/del/Projects/slm-orchestrator/dataset%20claude/baseline_results.txt)):

| Baseline | Accuratezza Globale | Dove crolla l'euristica naif |
|---|---|---|
| **Router**: 4 regex con regola sulle intestazioni | **86,2%** | Nello strato `conflitto_lessicale`: crolla al **57,6%** |
| **Router**: Solo parole-spia senza intestazioni | **70,0%** | Nello strato `lungo_distrattori`: crolla al **42,9%** |
| **Email**: Parole-spia sull'intero corpo del testo | **38,5%** *(caso base casuale: 20%)* | Nei `thread`: **13%**; `lungo`: **15%**; `conflitto`: **22%** |

Questi risultati confermano empiricamente che il dataset sintetico offre un solido banco di prova per misurare la robustezza ai distrattori e il superamento dei bias contestuali da parte degli SLM.

---

## 6. Istruzioni di Verifica e Riproducibilità

### Verifica Automatica del Dataset
```bash
cd "dataset claude"
python3 audit_decision_dataset.py . 20261030 160 200 generate_decision_dataset.py
```
L'audit verificherà crittograficamente l'hash SHA-256 e confermerà l'assenza di anomalie.

### Rigenerazione da Zero
Per rigenerare il dataset completo in una directory a scelta e confrontare l'hash con `manifest.json`:
```bash
cd "dataset claude"
python3 generate_decision_dataset.py --seed 20261030 --n-route 160 --n-email 200 --out /tmp/dataset_test_gen
sha256sum /tmp/dataset_test_gen/dataset_all.jsonl
# Deve restituire esattamente: be1f22eca003d174aa13ee6bed336795dd99b41152cc8a14d817006fe46e86e6
```

### Esecuzione Baseline
```bash
cd "dataset claude"
python3 baseline_lexical.py dataset_test.jsonl
```
