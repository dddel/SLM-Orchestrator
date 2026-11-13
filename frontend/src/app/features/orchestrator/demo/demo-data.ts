import { GuardPresentationItem } from '../models/orchestrator-ui.models';
import { DocumentType, RouterType, OrchestratorStatus } from '../../../core/models/orchestrator.models';

export interface DemoItem {
  id: string;
  chipLabel: string;
  expectedStatus: OrchestratorStatus;
  detectedLabel: DocumentType;
  router: RouterType;
  inputPayload: string;
  docType: DocumentType | null;
  expectedJson: string;
  guards: GuardPresentationItem[];
  latencyMs: number;
}

export const DEMO_ITEMS: DemoItem[] = [
  {
    id: 'fattura-valida',
    chipLabel: 'Fattura Valida',
    expectedStatus: 'OK',
    detectedLabel: 'FATTURA',
    router: 'local-ollama',
    docType: 'FATTURA',
    inputPayload: `FATTURA N. 2024/0984 del 15/10/2024
Cedente/Prestatore: Acme Servizi Digitali S.r.l.
P.IVA: 01234567897 - C.F.: 01234567897
IBAN: IT60X0542811101000000123456

Cessionario/Committente: Rossi Meccanica S.p.A.
P.IVA: 98765432103

Descrizione: Consulenza architettura Microservizi e SLM: € 3.200,00
Imponibile: € 4.600,00 - IVA 22%: € 1.012,00
Totale da saldare: € 5.612,00`,
    expectedJson: JSON.stringify(
      {
        numeroFattura: '2024/0984',
        dataFattura: '15/10/2024',
        fornitore: {
          denominazione: 'Acme Servizi Digitali S.r.l.',
          partitaIva: '01234567897'
        },
        cliente: {
          denominazione: 'Rossi Meccanica S.p.A.',
          codiceFiscale: '98765432103'
        },
        importoTotale: 5612,
        iban: 'IT60X0542811101000000123456'
      },
      null,
      2
    ),
    latencyMs: 382,
    guards: [
      {
        id: 'g1',
        title: 'P.IVA Fornitore (01234567897)',
        detail: 'Checksum Luhn verificato matematicamente (controllo 7)',
        status: 'passed'
      },
      {
        id: 'g2',
        title: 'P.IVA Cliente (98765432103)',
        detail: 'Checksum Luhn verificato matematicamente (controllo 3)',
        status: 'passed'
      },
      {
        id: 'g3',
        title: 'IBAN Nazionale',
        detail: 'Validazione formale CIN & lunghezza 27 caratteri',
        status: 'passed'
      },
      {
        id: 'g4',
        title: 'Schema JSON Fattura',
        detail: 'Campi obbligatori e coerenza Imponibile + IVA',
        status: 'passed'
      }
    ]
  },
  {
    id: 'fattura-piva-errata',
    chipLabel: 'P.IVA Errata (Luhn)',
    expectedStatus: 'INDETERMINATE',
    detectedLabel: 'FATTURA',
    router: 'local-ollama',
    docType: 'FATTURA',
    inputPayload: `FATTURA N. 2024/0984 del 15/10/2024
Cedente/Prestatore: Acme Servizi Digitali S.r.l.
P.IVA: 01234567897
IBAN: IT60X0542811101000000123456

Cessionario/Committente: Rossi Meccanica S.p.A.
P.IVA: 98765432109

Totale da saldare: € 5.612,00`,
    expectedJson: JSON.stringify(
      {
        numeroFattura: '2024/0984',
        fornitore: {
          denominazione: 'Acme Servizi Digitali S.r.l.',
          partitaIva: '01234567897'
        },
        cliente: {
          denominazione: 'Rossi Meccanica S.p.A.',
          codiceFiscale: '98765432109'
        },
        importoTotale: 5612,
        iban: 'IT60X0542811101000000123456'
      },
      null,
      2
    ),
    latencyMs: 310,
    guards: [
      {
        id: 'g1',
        title: 'P.IVA Fornitore (01234567897)',
        detail: 'Checksum Luhn verificato matematicamente (controllo 7)',
        status: 'passed'
      },
      {
        id: 'g2',
        title: 'P.IVA Cliente (98765432109)',
        detail: 'Checksum Luhn invalido: termina con 9, atteso 3',
        status: 'warning'
      },
      {
        id: 'g3',
        title: 'Guardia Simbolica Deterministica',
        detail: 'Blocco automatico: dato errato nel testo sorgente',
        status: 'warning'
      },
      {
        id: 'g4',
        title: 'Esito Decisionale',
        detail: 'INDETERMINATE emesso senza allucinazioni o retry',
        status: 'warning'
      }
    ]
  },
  {
    id: 'email-preventivo',
    chipLabel: 'Email Richiesta',
    expectedStatus: 'OK',
    detectedLabel: 'EMAIL',
    router: 'local-ollama',
    docType: 'EMAIL',
    inputPayload: `Da: marco.bianchi@techsolution.it
A: supporto@slm-orchestrator.it
Oggetto: Richiesta urgente preventivo integrazione SLM

Gentile Team, vi scrivo per richiedere una quotazione urgente per implementare la vostra pipeline di orchestrazione con modelli Qwen 2.5 3B su nostra infrastruttura on-premises.

Volume stimato: 15.000 doc/mese.
Ing. Marco Bianchi - TechSolution S.r.l.`,
    expectedJson: JSON.stringify(
      {
        mittente: 'Marco Bianchi',
        email: 'marco.bianchi@techsolution.it',
        oggetto: 'Richiesta urgente preventivo integrazione SLM',
        urgenza: 'URGENTE',
        sintesiRichiesta: 'Richiesta preventivo per pipeline on-premises Qwen 2.5 3B.'
      },
      null,
      2
    ),
    latencyMs: 295,
    guards: [
      {
        id: 'g1',
        title: 'Sintassi Email Mittente',
        detail: 'Conforme alle specifiche RFC 5322',
        status: 'passed'
      },
      {
        id: 'g2',
        title: 'Classificazione Urgenza',
        detail: 'Rilevato marcatore di priorità URGENTE',
        status: 'passed'
      },
      {
        id: 'g3',
        title: 'Schema JSON Email',
        detail: 'Struttura campi mittente e sintesi validata',
        status: 'passed'
      }
    ]
  },
  {
    id: 'anagrafica-persona',
    chipLabel: 'Anagrafica & KYC',
    expectedStatus: 'OK',
    detectedLabel: 'ANAGRAFICA',
    router: 'local-ollama',
    docType: 'ANAGRAFICA',
    inputPayload: `MODULO ACQUISIZIONE ANAGRAFICA CLIENTE
Nome: Sofia Ferrari
Nata il: 14/05/1990 a Firenze (FI)
Codice Fiscale: FRRSFO90E54D612Y
Indirizzo: Via de' Tornabuoni 18, 50123 Firenze (FI)
Email: sofia.ferrari@pec.it`,
    expectedJson: JSON.stringify(
      {
        nome: 'Sofia',
        cognome: 'Ferrari',
        dataNascita: '14/05/1990',
        luogoNascita: 'Firenze',
        codiceFiscale: 'FRRSFO90E54D612Y',
        cap: '50123',
        pec: 'sofia.ferrari@pec.it'
      },
      null,
      2
    ),
    latencyMs: 340,
    guards: [
      {
        id: 'g1',
        title: 'Codice Fiscale Alfanumerico',
        detail: 'Algoritmo D.M. 23/12/1976: carattere di controllo Y valido',
        status: 'passed'
      },
      {
        id: 'g2',
        title: 'Coerenza Territoriale',
        detail: 'CAP 50123 coerente con la provincia di Firenze (FI)',
        status: 'passed'
      },
      {
        id: 'g3',
        title: 'Indirizzo PEC',
        detail: 'Formato certificato conforme',
        status: 'passed'
      }
    ]
  },
  {
    id: 'altro-verbale',
    chipLabel: 'Verbale Riunione',
    expectedStatus: 'OK',
    detectedLabel: 'ALTRO',
    router: 'reflex',
    docType: null,
    inputPayload: `VERBALE SPRINT REVIEW & RETROSPECTIVE - SPRINT 42
Data: 02 Novembre 2026 - Ore 10:00
Partecipanti: Lead Architect, Backend Engineer, QA Specialist.

Sintesi: Le guardie simboliche deterministiche prevengono al 100% le anomalie formali.
Decisioni: consolidare prompt di sistema in classpath.`,
    expectedJson: JSON.stringify(
      {
        categoriaRilevata: 'Verbale riunione tecnica',
        argomentoPrincipale: 'Sprint Review e metriche',
        motivoEsclusione: 'Non contiene estremi di fattura, email o anagrafica',
        entitaRilevate: ['Lead Architect', 'Backend Engineer', 'QA Specialist']
      },
      null,
      2
    ),
    latencyMs: 245,
    guards: [
      {
        id: 'g1',
        title: 'APUS Reflex Classifier',
        detail: 'Esclusione semantica rapida da categorie note',
        status: 'passed'
      },
      {
        id: 'g2',
        title: 'Smistamento Dinamico ALTRO',
        detail: 'Pipeline generica attivata con successo',
        status: 'passed'
      }
    ]
  }
];
