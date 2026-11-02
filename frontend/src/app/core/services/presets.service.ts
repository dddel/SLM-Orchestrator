import { Injectable } from '@angular/core';
import { RequestPreset } from '../models/orchestrator.models';

@Injectable({
  providedIn: 'root'
})
export class PresetsService {
  readonly presets: RequestPreset[] = [
    {
      id: 'fattura-valida',
      title: 'Fattura Standard (Valida al 100%)',
      docType: 'FATTURA',
      routerType: 'local-ollama',
      description: 'Documento contabile con Fornitore, P.IVA, IBAN e imponibile conformi alle regole formali.',
      payload: `FATTURA N. 2024/0984 del 15/10/2024
Cedente/Prestatore:
Acme Servizi Digitali S.r.l.
Via Alessandro Manzoni 45, 20121 Milano (MI)
P.IVA: 01234567897 - C.F.: 01234567897
IBAN: IT60X0542811101000000123456

Cessionario/Committente:
Rossi Meccanica S.p.A.
Via Emilia Ponente 102, 40133 Bologna (BO)
P.IVA: 98765432103

Descrizione prestazioni:
1. Consulenza architettura Microservizi e SLM: € 3.200,00
2. Assistenza rilascio in produzione e tuning GGUF: € 1.400,00

Imponibile: € 4.600,00
Aliquota IVA 22%: € 1.012,00
Totale da saldare a vista fattura: € 5.612,00`,
      expected: {
        status: 'OK',
        detectedLabel: 'FATTURA',
        summary: 'Estrazione completa e superamento di tutte le guardie deterministiche.',
        expectedJson: `{
  "numeroFattura": "2024/0984",
  "dataFattura": "15/10/2024",
  "fornitore": {
    "denominazione": "Acme Servizi Digitali S.r.l.",
    "partitaIva": "01234567897"
  },
  "cliente": {
    "denominazione": "Rossi Meccanica S.p.A.",
    "codiceFiscale": "98765432103"
  },
  "importoTotale": 5612,
  "iban": "IT60X0542811101000000123456"
}`,
        errors: [],
        notes: [
          'La Partita IVA fornitore 01234567897 rispetta il checksum di Luhn (cifra di controllo 7).',
          'La Partita IVA cliente 98765432103 rispetta il checksum di Luhn (cifra di controllo 3).',
          'IBAN italiano valido con codice CIN conforme.',
          'Importo totale coerente con la somma Imponibile + IVA 22%.'
        ]
      }
    },
    {
      id: 'fattura-piva-errata',
      title: 'Fattura con P.IVA Errata (Checksum Luhn Invalido)',
      docType: 'FATTURA',
      routerType: 'local-ollama',
      description: 'Dimostra la guardia deterministica: il modello estrae correttamente il testo, ma la guardia rileva il checksum matematico errato e rigetta l\'output.',
      payload: `FATTURA N. 2024/0984 del 15/10/2024
Cedente/Prestatore:
Acme Servizi Digitali S.r.l.
Via Alessandro Manzoni 45, 20121 Milano (MI)
P.IVA: 01234567897 - C.F.: 01234567897
IBAN: IT60X0542811101000000123456

Cessionario/Committente:
Rossi Meccanica S.p.A.
Via Emilia Ponente 102, 40133 Bologna (BO)
P.IVA: 98765432109

Descrizione prestazioni:
1. Consulenza architettura Microservizi e SLM: € 3.200,00
2. Assistenza rilascio in produzione e tuning GGUF: € 1.400,00

Imponibile: € 4.600,00
Aliquota IVA 22%: € 1.012,00
Totale da saldare a vista fattura: € 5.612,00`,
      expected: {
        status: 'INDETERMINATE',
        detectedLabel: 'FATTURA',
        summary: 'Rifiuto formale da parte della guardia: P.IVA cliente invalida presente nel testo sorgente.',
        expectedJson: `{
  "numeroFattura": "2024/0984",
  "dataFattura": "15/10/2024",
  "fornitore": {
    "denominazione": "Acme Servizi Digitali S.r.l.",
    "partitaIva": "01234567897"
  },
  "cliente": {
    "denominazione": "Rossi Meccanica S.p.A.",
    "codiceFiscale": "98765432109"
  },
  "importoTotale": 5612,
  "iban": "IT60X0542811101000000123456"
}`,
        errors: [
          {
            path: 'cliente.codiceFiscale',
            code: 'SOURCE_CHECKSUM_INVALID',
            reason: "Il codice di controllo della partita IVA '98765432109' è errato (il checksum Luhn atteso sarebbe 3, ma termina con 9). Poiché il dato errato è presente letteralmente nel testo di partenza, l'orchestratore evita inutili retry ed emette lo stato INDETERMINATE."
          }
        ],
        notes: [
          "Non si tratta di un'allucinazione dell'SLM: il modello ha estratto fedelmente quanto scritto.",
          "La componente simbolica (algoritmo deterministico di Luhn) impedisce l'archiviazione di dati formalmente errati nel sistema."
        ]
      }
    },
    {
      id: 'email-preventivo',
      title: 'Email Richiesta Preventivo Urgente',
      docType: 'EMAIL',
      routerType: 'local-ollama',
      description: 'Comunicazione non strutturata via posta elettronica con mittente, richiesta e urgenza.',
      payload: `Da: marco.bianchi@techsolution.it
A: supporto@slm-orchestrator.it
Data: 28 Ottobre 2024 14:32
Oggetto: Richiesta urgente preventivo integrazione orchestratore neuro-simbolico

Gentile Team,
vi scrivo per richiedere una quotazione urgente per implementare la vostra pipeline di orchestrazione con modelli Qwen 2.5 3B e routing reflex su nostra infrastruttura on-premises.

Abbiamo un volume stimato di 15.000 documenti/mese tra fatture passive e corrispondenza tecnica.
Gradiremmo ricevere dettagli sulla latenza media per token e supporto alle guardie su P.IVA e Codici Fiscali.

Resto a disposizione per una call conoscitiva entro giovedì.

Cordiali saluti,
Ing. Marco Bianchi
Responsabile Sistemi Informativi - TechSolution S.r.l.`,
      expected: {
        status: 'OK',
        detectedLabel: 'EMAIL',
        summary: 'Estrazione mittente RFC-compliant, oggetto, urgenza e recipient verification.',
        expectedJson: `{
  "mittente": "marco.bianchi@techsolution.it",
  "oggetto": "Richiesta urgente preventivo integrazione orchestratore neuro-simbolico",
  "dataEmail": "2024-10-28",
  "urgenza": "URGENTE",
  "sintesiRichiesta": "Richiesta preventivo per implementazione pipeline on-premises Qwen 2.5 3B."
}`,
        errors: [],
        notes: [
          "Indirizzo email mittente validato formalmente secondo lo standard RFC 5322.",
          "Classificazione del livello di urgenza e risoluzione dell'azienda mittente."
        ]
      }
    },
    {
      id: 'anagrafica-persona',
      title: 'Scheda Anagrafica & KYC (Codice Fiscale Valido)',
      docType: 'ANAGRAFICA',
      routerType: 'local-ollama',
      description: 'Dati persona fisica con Codice Fiscale a 16 caratteri verificato con algoritmo alfanumerico ufficiale.',
      payload: `MODULO ACQUISIZIONE ANAGRAFICA CLIENTE
ID Pratica: KY-88291

Dati Identificativi:
Nome: Sofia
Cognome: Ferrari
Nata il: 14/05/1990 a Firenze (FI)
Codice Fiscale: FRRSFO90E54D612Y
Stato Civile: Celibe/Nubile

Residenza:
Indirizzo: Via de' Tornabuoni, 18
CAP: 50123
Comune: Firenze
Provincia: FI
Recapito telefonico: +39 347 1122334
Email certificata: sofia.ferrari@pec.it`,
      expected: {
        status: 'OK',
        detectedLabel: 'ANAGRAFICA',
        summary: 'Verifica della matrice anagrafica e validità del Codice Fiscale (D.M. 23/12/1976).',
        expectedJson: `{
  "nome": "Sofia",
  "cognome": "Ferrari",
  "dataNascita": "14/05/1990",
  "luogoNascita": "Firenze",
  "codiceFiscale": "FRRSFO90E54D612Y",
  "indirizzoResidenza": {
    "via": "Via de' Tornabuoni, 18",
    "cap": "50123",
    "comune": "Firenze",
    "provincia": "FI"
  },
  "pec": "sofia.ferrari@pec.it"
}`,
        errors: [],
        notes: [
          "Codice Fiscale a 16 caratteri con carattere di controllo 'Y' verificato matematicamente.",
          'CAP numerico di 5 cifre compatibile con la provincia di Firenze.'
        ]
      }
    },
    {
      id: 'altro-verbale',
      title: 'Verbale Riunione (Classificazione Autonoma ALTRO)',
      docType: null,
      routerType: 'reflex',
      description: 'Documento generico non strutturato: ideale per testare il router APUS Reflex che lo classifica autonomamente come ALTRO.',
      payload: `VERBALE SPRINT REVIEW & RETROSPECTIVE - SPRINT 42
Data: 02 Novembre 2026 - Ore 10:00
Partecipanti: Lead Architect, Backend Engineer, QA Specialist.

Sintesi discussione:
Abbiamo verificato che le guardie simboliche deterministiche prevengono al 100% l'insorgenza di anomalie sintattiche sui campi chiave.
L'escalation automatica scatta in media nel 3.2% dei casi limite.
Decisioni prese:
- Consolidare i prompt di sistema in classpath
- Aggiornare i benchmark Wilson score al 95% di confidenza`,
      expected: {
        status: 'OK',
        detectedLabel: 'ALTRO',
        summary: 'Il Router semantico o reflex riconosce che il documento non è FATTURA, EMAIL o ANAGRAFICA.',
        expectedJson: `{
  "categoriaRilevata": "Verbale riunione tecnica",
  "argomentoPrincipale": "Sprint Review e metriche di confidenza",
  "motivoEsclusione": "Non contiene estremi di pagamento, intestazioni di posta o schede anagrafiche.",
  "entitaRilevate": [
    "Lead Architect",
    "Backend Engineer",
    "QA Specialist"
  ]
}`,
        errors: [],
        notes: [
          "L'utente non seleziona 'ALTRO': è il Router a decidere l'etichetta in base al contenuto semantico.",
          'Viene eseguita la pipeline generica per estrarre categoria, argomento principale ed entità.'
        ]
      }
    }
  ];

  getPresets(): RequestPreset[] {
    return this.presets;
  }
}
