# SLM Orchestrator

Piattaforma neuro-simbolica basata su Java e Spring Boot per l'orchestrazione di Small Language Model (SLM) locali (tramite Ollama) o modelli remoti (Gemini, Cloudflare/Jev), con pipeline di estrazione strutturata, validazione simbolica e recupero degli errori.

---

## 1. Requisiti di Sistema

- **Java**: OpenJDK 21 o superiore
- **Maven**: 3.9+ (oppure il wrapper `./mvnw` incluso)
- **Container Runtime** (opzionale): Docker / Podman e Docker Compose per l'esecuzione dei servizi containerizzati
- **Ollama** (opzionale, per inferenza locale): istanza Ollama attiva (default `http://localhost:11434` o `http://ollama:11434`) con i modelli supportati

---

## 2. Configurazione e Variabili d'Ambiente

La configurazione dell'applicazione può essere definita tramite file `.env` o variabili d'ambiente di sistema. Un modello di riferimento è disponibile in `.env.example`.

### Variabili Principali

| Variabile | Descrizione | Valore Predefinito / Esempio |
| :--- | :--- | :--- |
| `ROUTER_TYPE` | Meccanismo di routing dei documenti (`local-ollama`, `gemini`, `jev`) | `local-ollama` |
| `OLLAMA_BASE_URL` | URL base dell'istanza Ollama per inferenza locale | `http://localhost:11434` |
| `GEMINI_API_KEY` | Chiave API per Google AI Studio (necessaria con router o client Gemini) | - |
| `JEV_PROVIDER` | Provider per router Jev (`typesafe` o `cloudflare`) | `typesafe` |
| `JEV_API_KEY` | Chiave API per TypeSafe AI | - |
| `CLOUDFLARE_API_TOKEN` | Token API per Cloudflare Workers AI | - |
| `CLOUDFLARE_ACCOUNT_ID` | Account ID Cloudflare | - |

Le proprietà applicative predefinite e i parametri di runtime sono definiti in `src/main/resources/application.properties`.

---

## 3. Build e Test

### Compilazione e Packaging

Per compilare il progetto e generare il file JAR eseguibile:

```bash
./mvnw clean package -DskipTests
```

### Esecuzione della Suite di Test

Per eseguire i test unitari e di integrazione offline:

```bash
./mvnw test
```

I test che richiedono un'istanza live di Ollama o chiavi API esterne sono contrassegnati con il tag JUnit `@Tag("live")` e sono esclusi per impostazione predefinita dal profilo di test standard. Per eseguirli esplicitamente:

```bash
./mvnw test -DexcludedGroups="" -Dgroups=live
```

---

## 4. Avvio dell'Applicazione

### Avvio Locale con Maven

```bash
./mvnw spring-boot:run
```

L'applicazione sarà disponibile sulla porta `8080`.

### Avvio con Container (Docker / Podman Compose)

Per avviare sia l'orchestratore sia il servizio Ollama in container:

```bash
docker compose up -d
# oppure
podman compose up -d
```

Per l'abilitazione di accelerazione GPU NVIDIA, consultare `compose.gpu.yaml`.

---

## 5. Struttura del Progetto

```text
├── data/                  # Dataset di test e ground truth (JSON)
├── scripts/               # Script di generazione e validazione dataset
├── src/
│   ├── main/
│   │   ├── java/          # Codice sorgente applicativo (Spring Boot)
│   │   └── resources/     # Configurazioni, pipeline YAML, prompt e schemi JSON
│   └── test/
│       ├── java/          # Test unitari, integrativi e harness di valutazione
│       └── resources/     # Risorse di test
├── .mvn/                  # Wrapper Maven
├── compose.yaml           # Definizione multi-container Docker/Podman
├── Dockerfile             # Dockerfile multi-stage per l'applicazione
├── mvnw / mvnw.cmd        # Script wrapper Maven per Linux e Windows
└── pom.xml                # Descrittore di build Maven
```
