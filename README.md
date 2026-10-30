# SLM Orchestrator

Piattaforma neuro-simbolica basata su Java e Spring Boot per l'orchestrazione di Small Language Model (SLM) locali (tramite Ollama), con pipeline di estrazione strutturata, validazione simbolica, tool calling e recupero degli errori.

---

## 1. Requisiti di Sistema

- **Java**: OpenJDK 21 o superiore
- **Maven**: 3.9+ (oppure il wrapper `./mvnw` incluso)
- **Container Runtime** (opzionale): Docker / Podman e Docker Compose per l'esecuzione dei servizi containerizzati
- **Ollama** (opzionale, per inferenza locale): istanza Ollama attiva (default `http://localhost:11434` o `http://ollama:11434`) con i modelli supportati

---

## 2. Configurazione e Variabili d'Ambiente

La configurazione dell'applicazione può essere personalizzata tramite variabili d'ambiente di sistema oppure modificando i valori predefiniti in `src/main/resources/application.properties`. Non sono necessari secret o chiavi API esterne in quanto l'architettura opera al 100% in locale (offline).

### Variabili Principali

| Variabile | Descrizione | Valore Predefinito / Esempio |
| :--- | :--- | :--- |
| `ROUTER_TYPE` | Meccanismo di routing dei documenti (`local-ollama`) | `local-ollama` |
| `OLLAMA_BASE_URL` | URL base dell'istanza Ollama per inferenza locale | `http://localhost:11434` |
| `OLLAMA_BASE_MODEL` | Modello SLM di default da interrogare | `qwen2.5:3b-instruct-q8_0` |
| `OLLAMA_TIMEOUT_SECONDS` | Timeout in secondi per le chiamate HTTP a Ollama | `30` |

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

I test che richiedono un'istanza live di Ollama sono contrassegnati con il tag JUnit `@Tag("live")` e sono esclusi per impostazione predefinita dal profilo di test standard. Per eseguirli esplicitamente:

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

Per la guida dettagliata sull'architettura dei container, il ciclo di vita dei modelli locali e l'abilitazione dell'accelerazione GPU NVIDIA, consultare la guida dedicata: [**`DOCKER.md`**](file:///home/del/Projects/slm-orchestrator/DOCKER.md).

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
