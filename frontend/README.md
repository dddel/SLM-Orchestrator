# SLM Orchestrator - Frontend SPA

Interfaccia web moderna, modulare ed estremamente minimale ("easy on the eye") per l'interazione con l'orchestratore neuro-simbolico SLM (Small Language Models).

---

## Caratteristiche Principali

- **Design Minimale & High-Readability**: Palette scura raffinata (Slate / Deep Blue) con accenti elettrici (Emerald, Indigo, Sky), tipografia studiata (Inter & Fira Code), contrasto ottimizzato e tema chiaro alternativo.
- **Architettura a Componenti Modulari**: Standalone components Angular 21, Signals reattivi, Service Layer disaccoppiato e Type Safety rigorosa.
- **Menu Parametri ad Espansione (Accordion)**:
  - **Tipo Documento (`DocumentType`)**: Selezione discreta (`Auto-Detect`, `FATTURA`, `EMAIL`, `ANAGRAFICA`, `ALTRO`) con badge visivi.
  - **Motore Router (`RouterType`)**: Selezione (`Predefinito`, `local-ollama`, `reflex`) con note prestazionali.
  - **Parametri Liberi & Metadati (Stringhe)**: Campi testuali liberi per inserire coppie chiave/valore arbitrarie (es. `correlationId`, tag di debug, metadati di tracciamento).
  - **Endpoint API & Host**: Campo testuale libero per sovrascrivere l'endpoint backend o puntare a cluster remoti.
- **Viewer Risultati Multi-Scheda**:
  - **JSON Validato**: Output strutturato con formattazione automatica, copia negli appunti in 1-click ed esportazione.
  - **Metriche Neuro-Simboliche**: Schede KPI su latenza (ms), throughput token/s, token di prompt/eval e durata per ciascuna fase della pipeline.
  - **Errori di Validazione**: Dettaglio formale delle violazioni (`path`, `code`, `message`) intercettate dalle guardie simboliche.
  - **Output Non Validato**: Revisione dell'output grezzo generato dal modello in caso di stato `INDETERMINATE`.
- **Esempi Predefiniti Integrati**: Casi d'uso realistici pre-caricati (Fattura commerciale con P.IVA/IBAN, Email di preventivo, Scheda anagrafica con Codice Fiscale, Verbale/Altro).
- **Cronologia Locale**: Storico locale delle ultime esecuzioni con latenza e ripristino immediato nel form.

---

## Avvio e Sviluppo

### Prerequisiti
- **Node.js** >= 20.x
- **Angular CLI** 21.x (`ng`)
- Backend Spring Boot attivo su `http://localhost:8080`

### Comandi

```bash
# Entra nella cartella frontend
cd frontend

# Installa le dipendenze (se non già presenti)
npm install

# Avvia il server di sviluppo con proxy attivo verso localhost:8080
npm start
# Oppure: ng serve
```

L'applicazione sarà disponibile all'indirizzo [http://localhost:4200](http://localhost:4200).
Le chiamate a `/api` verranno automaticamente inoltrate a `http://localhost:8080` grazie a `proxy.conf.json`.

### Esecuzione Test Unitari

```bash
npm test -- --watch=false
```

### Build di Produzione

```bash
npm run build
```
I file compilati e ottimizzati saranno posizionati in `frontend/dist/frontend/browser`.
