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
- Estrazione vincolata a JSON Schema tramite pipeline dichiarative YAML multi-step, con isolamento dello stato di retry tra step consecutivi (`clearRetryState()`).
- Validazione deterministica di formato, checksum e coerenza:
  - Partita IVA con algoritmo di Luhn
  - Codice Fiscale con carattere CIN
  - IBAN con ISO 7064 MOD 97-10
  - date, email, cataloghi e relazioni tra campi
- Guardrail pre-volo e post-inferenza sulla finestra di contesto (`ContextBudgetValidator`): rifiuto preventivo dei payload fuori budget (`HTTP 422`, `CONTEXT_TRUNCATED`) e short-circuit immediato su saturazione del contesto o troncamento dell'output (`done_reason=length` -> `OUTPUT_TRUNCATED`).
- Smart Retry con feedback strutturato e short-circuit dei documenti non validi.
- Esecuzione resiliente dei Tool (`ToolInvoker` e `ToolExecutionPolicy`) su virtual thread con timeout per singola invocazione, retry esponenziale per errori infrastrutturali e tracciamento delle metriche `StepMetric` (`stepType="TOOL"`).
- Separazione netta tra errori infrastrutturali (`HTTP 502` su router non disponibile con fallback disabilitato, `HTTP 503` su modello di estrazione irraggiungibile al primo tentativo) ed esiti di dominio (`HTTP 200` con `OK` o `INDETERMINATE`).
- Stato di dominio `OK` o `INDETERMINATE`, con conservazione dell'output non validato e degli errori di campo.
- Metriche per richiesta e per step: latenza, token, modello e durata della pipeline (`MODEL` e `TOOL`).
- Frontend Angular per l'invio dei documenti, il controllo per-request del fallback euristico del router (con banner di warning dedicato quando attivato), la visualizzazione del JSON e l'ispezione degli errori.

## Architettura

```text
Client / Angular
        |
        v
POST /api/orchestrator/process
        |
        v
InputValidator -> Router -> ContextBudgetValidator -> Pipeline YAML
                    |                                       |
                    +--> Fallback euristico (opzionale)     +--> SLM locale via Ollama
                    +--> HTTP 502 se disabilitato/down      +--> JSON Schema + Guardie Java
                                                            +--> ToolInvoker (timeout + retry)
                                                            +--> Smart Retry / INDETERMINATE
                                                            +--> Post-pipeline Actions (es. Mailpit)
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

Avvia backend, frontend e Ollama. Prima dell'avvio viene chiesto il percorso della directory dati di Ollama:

```bash
make up
```

È possibile premere `Invio` per usare il default `~/.ollama`, oppure indicare una directory dati Ollama già esistente. La directory viene montata direttamente nel container su `/root/.ollama`; l'init scarica solo i modelli non già presenti.

Per evitare il prompt e usare un percorso specifico:

```bash
OLLAMA_DATA_DIR=/percorso/assoluto/.ollama make up
```

Il percorso deve essere la directory dati completa di Ollama, non soltanto la sottocartella `models`.

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
make clean   # arresta i servizi senza cancellare i pesi Ollama
```

Per abilitare il profilo GPU NVIDIA:

```bash
cp compose.gpu.yaml compose.override.yaml
make up
```

I modelli predefiniti sono `qwen2.5:3b-instruct-q8_0` e `hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M`. Se non sono già presenti nella directory selezionata, vengono scaricati automaticamente. Possono essere sostituiti tramite variabili d'ambiente, ad esempio:

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
    "routerType": "local-ollama",
    "allowHeuristicFallback": true
  }'
```

- `type` (opzionale): se omesso o `null`, il router classifica automaticamente il testo. È possibile specificare `FATTURA`, `ANAGRAFICA`, `EMAIL` o `ALTRO`.
- `routerType` (opzionale): seleziona il router `local-ollama` o `reflex`, quando il relativo modello è configurato.
- `allowHeuristicFallback` (opzionale): sovrascrive per la singola richiesta l'impostazione `router.heuristic-fallback.enabled` (`true` abilita il fallback a parole chiave e segnala il warning nel frontend, `false` restituisce `HTTP 502` se il router SLM non risponde).

### Codici di stato HTTP

| HTTP | Esito / Eccezione | Significato |
|---|---|---|
| `200 OK` | `OrchestratorResponse` (`OK` / `INDETERMINATE`) | Elaborazione completata. Con `OK` il JSON certificato è in `result`; con `INDETERMINATE` gli errori sono in `errors` e l'ultimo JSON grezzo (non certificato) è in `unvalidatedResult`. |
| `400 Bad Request` | `MethodArgumentNotBlank` / JSON malformato | Payload mancante, vuoto o richiesta HTTP sintatticamente non valida. |
| `422 Unprocessable Entity` | `ValidationException` | Il payload viola i vincoli di dominio (`InputValidator`) oppure supera il budget di token della finestra di contesto (`ContextBudgetValidator`, codice `CONTEXT_TRUNCATED`). |
| `502 Bad Gateway` | `RoutingException` | Il modello di routing è irraggiungibile o va in timeout e il fallback euristico è disabilitato. |
| `503 Service Unavailable` | `PipelineExecutionException` | Il modello di estrazione è irraggiungibile al primo tentativo (nessun output parziale disponibile) o si verifica un errore infrastrutturale critico della pipeline. |
| `500 Internal Server Error` | `OrchestratorException` / `Exception` | Errore interno imprevisto o di configurazione. |

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

| Variabile / Property | Default | Scopo |
|---|---|---|
| `OLLAMA_BASE_URL` | `http://localhost:11434` | URL del servizio Ollama |
| `OLLAMA_BASE_MODEL` | `qwen2.5:3b-instruct-q8_0` | Modello generativo base |
| `OLLAMA_REFLEX_MODEL` | `hf.co/apus-ailab/APUS-Reflex-4B-GGUF:Q4_K_M` | Modello decisionale |
| `OLLAMA_NUM_CTX` | `2048` | Dimensione finestra di contesto (`num_ctx`) |
| `OLLAMA_NUM_PREDICT` | `400` | Numero massimo di token generati in output (`num_predict`) |
| `ROUTER_TYPE` | `local-ollama` | Router da utilizzare (`local-ollama` o `reflex`) |
| `ROUTER_HEURISTIC_FALLBACK` | `true` | Abilita il fallback euristico a parole chiave del router |
| `ORCHESTRATOR_GUARDS_ENABLED` | `true` | Abilita le guardie deterministiche |
| `ORCHESTRATOR_COHERENCE_ENABLED` | `true` | Abilita i controlli di coerenza |
| `orchestrator.pipeline.max-duration-ms` | `120000` | Budget temporale massimo (wall-clock) di una pipeline |
| `orchestrator.tools.default-timeout-ms` | `5000` | Timeout di default per singola invocazione tool |
| `orchestrator.tools.default-max-retries` | `0` | Numero di retry di default per errori infrastrutturali dei tool |
| `orchestrator.tools.default-retry-backoff-ms` | `200` | Backoff esponenziale iniziale (ms) per i retry dei tool |
| `orchestrator.context.chars-per-token` | `2.5` | Stima conservativa caratteri/token per `ContextBudgetValidator` |
| `EMAIL_ENABLED` | `false` | Abilita l'azione di invio email |
| `EMAIL_DRY_RUN` | `true` | Simula l'invio senza contattare SMTP |
| `EMAIL_FROM` | `no-reply@example.test` | Mittente dei messaggi |
| `EMAIL_ALLOWED_RECIPIENT_DOMAINS` | `example.test` | Domini ammessi dalla allowlist |
| `EMAIL_SMTP_HOST` | `localhost` | Host del server SMTP |
| `EMAIL_SMTP_PORT` | `1025` | Porta del server SMTP |

### Demo email con Mailpit

La pipeline `EMAIL` può eseguire l'azione post-pipeline `send_email`, che invia un
riepilogo testuale dell'output validato. L'invio è disabilitato e in `dry-run` per
default; Mailpit viene avviato solo con il profilo Compose dedicato:

```bash
EMAIL_ENABLED=true EMAIL_DRY_RUN=false \
  docker compose --profile mailpit up -d --build
```

Il servizio `app` riceve queste variabili e usa Mailpit come destinazione SMTP
predefinita nel profilo Compose. La UI di Mailpit è disponibile su
<http://localhost:8025>; il server SMTP ascolta sulla porta `1025`.

Per limitare i destinatari durante i test è possibile configurare
`EMAIL_ALLOWED_RECIPIENT_DOMAINS` (default: `example.test`). Un'azione fallita
non altera lo stato primario della pipeline: viene riportata in `actionErrors`.

Per aggiungere una nuova pipeline occorre definire la categoria documentale, lo schema JSON, il prompt e il file YAML; la procedura completa è descritta nella sezione dedicata della [documentazione tecnica](docs/documentazione_progetto.md).

## Limiti e sicurezza

- L'API è volutamente aperta in ambiente locale: non sono implementati autenticazione, autorizzazione o rate limiting. Non esporla direttamente a Internet.
- L'esecuzione locale riduce l'invio di dati a terzi, ma non risolve automaticamente gli obblighi GDPR relativi a log, accessi e conservazione di `unvalidatedResult`.
- `OK` indica il superamento dei controlli configurati, non la verità dei dati estratti: valori plausibili ma errati possono passare.
- `ContextBudgetValidator` stima pre-volo il budget di token della finestra di contesto (`num_ctx`) respingendo i payload eccedenti con HTTP 422 (`CONTEXT_TRUNCATED`) e verifica post-inferenza l'eventuale saturazione del contesto o il troncamento dell'output (`OUTPUT_TRUNCATED`).
- Il throughput è pensato per un prototipo con un runner locale; concorrenza e gestione della cache richiedono ulteriori verifiche.
- La licenza del modello base Qwen2.5-3B è una Qwen Research License: verificarne la compatibilità prima dell'uso aziendale. Le licenze dei modelli sono distinte da quella del codice del progetto.

Per dettagli su scelte architetturali, guardie, error handling, benchmark e punti aperti, consultare:

- [`docs/documentazione_progetto.md`](docs/documentazione_progetto.md)
- [`docs/codebase_technical_reference.md`](docs/codebase_technical_reference.md)
- [`frontend/README.md`](frontend/README.md)

## Licenza

Il repository non dichiara attualmente una licenza software a livello di progetto. Prima di distribuire o riutilizzare il codice, definire la licenza del repository e verificare separatamente le licenze dei modelli e dei dataset.
