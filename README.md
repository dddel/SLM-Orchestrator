# SLM Orchestrator

Servizio **neuro-simbolico** per l'estrazione strutturata di informazioni da documenti testuali non strutturati, basato su Small Language Models (SLM) eseguiti localmente e su validazioni deterministiche.

Il sistema combina la flessibilità semantica dei modelli linguistici con controlli formali verificabili: classifica il documento, estrae i dati in JSON secondo uno schema e valida i campi con guardie Java, retry controllati e gestione esplicita degli esiti non determinabili.

> **Stato:** prototipo/ progetto di ricerca. Prima di un'esposizione in produzione sono necessari autenticazione, autorizzazione, rate limiting, gestione della privacy e una valutazione su documenti reali.

## Funzionalità

- Esecuzione locale dei modelli tramite [Ollama](https://ollama.com/), senza inviare i documenti a servizi LLM esterni.
- Routing automatico o esplicito verso quattro categorie:
  - `FATTURA`
  - `ANAGRAFICA`
  - `EMAIL`
  - `ALTRO`
- Estrazione vincolata a JSON Schema tramite pipeline dichiarative YAML.
- Validazione deterministica di formato, checksum e coerenza:
  - Partita IVA con algoritmo di Luhn
  - Codice Fiscale con carattere CIN
  - IBAN con ISO 7064 MOD 97-10
  - date, email, cataloghi e relazioni tra campi
- Smart Retry con feedback strutturato e short-circuit dei documenti non validi.
- Stato di dominio `OK` o `INDETERMINATE`, con conservazione dell'output non validato e degli errori di campo.
- Metriche per richiesta e per step: latenza, token, modello e durata della pipeline.
- Frontend Angular per l'invio dei documenti, la visualizzazione del JSON e l'ispezione degli errori.

## Architettura

```text
Client / Angular
        |
        v
POST /api/orchestrator/process
        |
        v
InputValidator -> Router -> Pipeline YAML
                              |
                              +--> SLM locale via Ollama
                              +--> JSON Schema
                              +--> Guardie deterministiche
                              +--> Retry / INDETERMINATE
```

Il backend è sviluppato con Java 21 e Spring Boot 4. Le pipeline, gli schemi e i prompt sono risorse configurabili:

```text
src/main/java/                 codice backend
src/main/resources/pipelines/  pipeline YAML
src/main/resources/schemas/    JSON Schema di estrazione
src/main/resources/prompts/    system prompt
src/test/                      test e dataset di valutazione
frontend/                      applicazione Angular
docs/                          documentazione tecnica e risultati
```

## Avvio rapido con Docker Compose

### Prerequisiti

- Docker con Docker Compose, oppure Podman Compose
- Risorse sufficienti per eseguire localmente i modelli scelti
- GPU opzionale; per NVIDIA è disponibile `compose.gpu.yaml`

Avvia backend, frontend, Ollama e il download automatico dei modelli predefiniti:

```bash
make up
```

Indirizzi principali:

- Frontend: <http://localhost:4200>
- API backend: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Ollama: <http://localhost:11434>

Comandi utili:

```bash
make logs
make ps
make restart
make down
make clean   # arresta i servizi e rimuove anche i volumi
```

Per abilitare il profilo GPU NVIDIA:

```bash
cp compose.gpu.yaml compose.override.yaml
make up
```

I modelli predefiniti sono `qwen2.5:3b-instruct-q8_0` e `hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M`. Possono essere sostituiti tramite variabili d'ambiente, ad esempio:

```bash
OLLAMA_BASE_MODEL=qwen2.5:3b-instruct-q8_0 \
OLLAMA_REFLEX_MODEL=hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M \
make up
```

## Sviluppo locale

### Backend

Prerequisiti: Java 21 e un servizio Ollama raggiungibile.

```bash
./mvnw spring-boot:run
```

Per scaricare manualmente il modello base:

```bash
ollama pull qwen2.5:3b-instruct-q8_0
```

### Frontend

Prerequisiti: Node.js 20 o superiore e Angular CLI 21.

```bash
cd frontend
npm install
npm start
```

Il proxy Angular inoltra le richieste `/api` al backend su `http://localhost:8080`.

## API

L'endpoint principale è `POST /api/orchestrator/process`.

```bash
curl -X POST http://localhost:8080/api/orchestrator/process \
  -H 'Content-Type: application/json' \
  -d '{
    "payload": "Fattura n. 12 del 10/01/2026. Totale 1.220,00 euro. IBAN: IT...",
    "type": null,
    "routerType": "local-ollama"
  }'
```

`type` è opzionale: se omesso o `null`, il router classifica automaticamente il testo. È possibile specificare `FATTURA`, `ANAGRAFICA`, `EMAIL` o `ALTRO`. `routerType` può selezionare il router locale o `reflex`, quando il relativo modello è configurato.

Una risposta con stato `OK` contiene il JSON validato in `result`. Una risposta `INDETERMINATE` contiene gli errori in `errors` e, quando disponibile, l'ultimo JSON prodotto dal modello in `unvalidatedResult`; tale output non deve essere trattato come dato certificato.

## Test e build

Test backend, con i gruppi live esclusi dalla configurazione Maven:

```bash
./mvnw test
```

Test e build frontend:

```bash
cd frontend
npm test -- --watch=false
npm run build
```

## Benchmark e riproducibilità

I risultati sperimentali, i dataset congelati e gli script di riproduzione sono descritti in [`docs/documentazione_progetto.md`](docs/documentazione_progetto.md). La documentazione riporta, tra gli altri, il confronto tra modelli 3B e 7B, l'ablazione delle guardie, lo Smart Retry e la valutazione dei router.

I risultati non sono una garanzia di accuratezza generale: i dataset sono ridotti e sintetici, e la ground truth è stata redatta con assistenza di un modello linguistico. Le metriche misurano quindi l'accordo con quei dataset e non la correttezza su documenti aziendali reali.

## Configurazione

Le proprietà principali sono in `src/main/resources/application.properties` e possono essere sovrascritte con variabili d'ambiente:

| Variabile | Default | Scopo |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | URL del servizio Ollama |
| `OLLAMA_BASE_MODEL` | `qwen2.5:3b-instruct-q8_0` | Modello generativo base |
| `OLLAMA_REFLEX_MODEL` | `hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M` | Modello decisionale |
| `ROUTER_TYPE` | `local-ollama` | Router da utilizzare |
| `ORCHESTRATOR_GUARDS_ENABLED` | `true` | Abilita le guardie deterministiche |
| `ORCHESTRATOR_COHERENCE_ENABLED` | `true` | Abilita i controlli di coerenza |

Per aggiungere una nuova pipeline occorre definire la categoria documentale, lo schema JSON, il prompt e il file YAML; la procedura completa è descritta nella sezione dedicata della [documentazione tecnica](docs/documentazione_progetto.md).

## Limiti e sicurezza

- L'API è volutamente aperta in ambiente locale: non sono implementati autenticazione, autorizzazione o rate limiting. Non esporla direttamente a Internet.
- L'esecuzione locale riduce l'invio di dati a terzi, ma non risolve automaticamente gli obblighi GDPR relativi a log, accessi e conservazione di `unvalidatedResult`.
- `OK` indica il superamento dei controlli configurati, non la verità dei dati estratti: valori plausibili ma errati possono passare.
- Il limite di contesto del modello può essere inferiore al limite massimo del payload; input molto lunghi possono essere troncati dal runtime.
- Il throughput è pensato per un prototipo con un runner locale; concorrenza e gestione della cache richiedono ulteriori verifiche.
- La licenza del modello base Qwen2.5-3B è una Qwen Research License: verificarne la compatibilità prima dell'uso aziendale. Le licenze dei modelli sono distinte da quella del codice del progetto.

Per dettagli su scelte architetturali, guardie, error handling, benchmark e punti aperti, consultare:

- [`docs/documentazione_progetto.md`](docs/documentazione_progetto.md)
- [`docs/codebase_technical_reference.md`](docs/codebase_technical_reference.md)
- [`frontend/README.md`](frontend/README.md)

## Licenza

Il repository non dichiara attualmente una licenza software a livello di progetto. Prima di distribuire o riutilizzare il codice, definire la licenza del repository e verificare separatamente le licenze dei modelli e dei dataset.
