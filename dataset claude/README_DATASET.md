# Dataset per confrontare modello decisionale e generativo

Seed 20261030. SHA-256 di `dataset_all.jsonl`: vedi `manifest.json`. Si rigenera identico con:
`python3 generate_decision_dataset.py --seed 20261030 --n-route 160 --n-email 200 --out OUT`
Audit: `python3 audit_decision_dataset.py OUT 20261030 160 200 generate_decision_dataset.py`

## Cosa contiene
| File | Contenuto |
|---|---|
| `dataset_dev.jsonl` | 160 router + 200 email (per mettere a punto prompt e soglie) |
| `dataset_test.jsonl` | 480 router + 600 email (da usare una volta sola, a configurazione congelata) |
| `neutral.jsonl` | 30 testi privi di segnale, senza etichetta (solo per la distribuzione delle lettere) |
| `dataset_all.jsonl` | unione di tutto |

Campi: `id, task, split, label, strata, template, text, clean_text` (+ `ask, ctx_roles` per le email).
**Ai sistemi va dato solo `text`.** `clean_text` (presente nei casi con rumore OCR) e gli altri campi servono all'audit.

## Task 1 - router (FATTURA / EMAIL / ANAGRAFICA / ALTRO)
Regola, scritta prima di generare: **la classe segue la forma del documento.**
- FATTURA: titolo fattura/invoice, imponibile, IVA/VAT.
- EMAIL: intestazioni Da/A (From/To), anche se cita o inoltra altri documenti.
- ANAGRAFICA: scheda di dati identificativi di persona o ente, senza transazione né messaggio.
- ALTRO: tutto il resto. **Per scelta di regola, preventivo e pro-forma NON sono fatture** (restano ALTRO anche con imponibile e IVA).
Se la tua definizione è diversa (es. pro-forma = FATTURA) le etichette di `o_preventivo` e `o_prev2` vanno cambiate.

Strati (test): standard 182, breve 77, **conflitto_lessicale 151** (es. preventivo con IVA, DDT con importi, email che inoltra una fattura, scheda fornitore con IBAN), lungo_distrattori 70 (email con fattura citata, fattura a tabella con più date e importi). Circa il 15% dei casi standard/brevi ha rumore OCR.

## Task 2 - instradamento email (direzione / risorse_umane / reparto_tecnico / amministrazione / legale)
Regola: **il ruolo è quello che possiede l'azione richiesta nelle righe NON citate.** Il contesto può evocare altri reparti. Ogni testo contiene richieste di un solo ruolo (l'audit lo controlla).
Strati (test): standard 100, breve 100, **conflitto_contesto 200** (2 frasi di contesto di altri reparti, richiesta di un reparto), **thread 100** (richiesta nuova in alto, messaggio citato di un altro reparto sotto), **lungo_distrattori 100** (3 frasi di contesto di altri reparti).
Dev e test usano richieste e frasi di contesto **disgiunte**.

## Cosa è stato verificato (output in `audit_results.txt`)
- Etichetta corretta per costruzione. In più l'audit, scritto separatamente, ricontrolla la regola sul testo: marcatori di fattura/email/scheda per il router, una sola famiglia di richieste per le email.
- Checksum: CF 157, IBAN 236, P.IVA 236 token, tutti validi. Il rumore OCR non altera mai i token con cifre.
- 0 testi duplicati, split disgiunti, classi bilanciate, rigenerazione identica.
- **Mutation test:** alterando la verità in 6 modi (etichetta cambiata, preventivo ribattezzato fattura, checksum rotto, ID duplicato...) l'audit li ha rilevati tutti.
- L'audit ha trovato un mio difetto (rumore OCR eccessivo su note brevissime): corretto.

## Difficoltà misurata (`baseline_results.txt`, solo test)
| Baseline | Accuratezza | Dove cade |
|---|---|---|
| Router, 4 regex con regola sulle intestazioni | 86,2% | conflitto_lessicale 57,6% |
| Router, solo parole-spia | 70,0% | lungo_distrattori 42,9% |
| Email, parole-spia sull'intero testo | 38,5% (caso: 20%) | thread 13%, lungo 15%, conflitto 22% |

Le baseline sono mie, non il router del progetto: dicono solo che il dataset **non si risolve con parole-spia**, specie nel conflitto lessicale e nelle email con contesto fuorviante.

## Limiti (da riportare nella nota)
1. **Sintetico da template, stesso autore del sistema.** Il testo è più regolare dei documenti reali. Un'accuratezza alta qui non si trasferisce automaticamente. Servono 20-30 documenti reali anonimizzati come campione di controllo.
2. **Le etichette dipendono da due regole dichiarate** (forma del documento; proprietario dell'azione). Se un lettore umano le giudica discutibili, la ground truth è "corretta secondo la regola", non "oggettiva". Fai rivedere un campione a una persona che non ha scritto la regola.
3. **Nessuna ambiguità vera.** Ogni caso ha una sola risposta per regola. Per misurare l'astensione/INDETERMINATE serve un set a parte con insiemi ammissibili.
4. **Pochi template per classe.** Le prove non sono indipendenti: gli intervalli vanno calcolati per campione (bootstrap a cluster per template), non con Wilson sulle prove.
5. **Il solo routing non dice nulla sull'estrazione dei campi.** Per quello serve un altro dataset.
6. **Non prova che il decisore sia migliore del generativo.** Dà il banco di prova; la risposta viene dal confronto appaiato.

## Come usarlo per la tua domanda
Stessi campioni, stesse permutazioni delle opzioni, due bracci: Reflex (think:false, num_predict 1) e Qwen con enum nello schema. Differenza appaiata **per campione**, bootstrap a cluster, sempre con la baseline a regole accanto.
Costo stimato (con ~1,1 s a chiamata, da misurare): con rotazioni cicliche (4 per il router, 5 per le email) sono 4.920 chiamate sul test, circa 1,5 ore per modello. Con tutte le permutazioni (24 e 120) servono ore in più per modello: conviene un sottocampione stratificato.
Il set `neutral.jsonl` serve alla distribuzione delle lettere (chi quadro), non all'accuratezza.
