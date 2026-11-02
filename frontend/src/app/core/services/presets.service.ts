import { Injectable } from '@angular/core';
import { RequestPreset } from '../models/orchestrator.models';

@Injectable({
  providedIn: 'root'
})
export class PresetsService {
  readonly presets: RequestPreset[] = [
    {
      id: 'fattura-standard',
      title: 'Fattura Elettronica Standard',
      docType: 'FATTURA',
      routerType: 'local-ollama',
      description: 'Documento contabile con Fornitore, P.IVA, IBAN, Totale imponibile e IVA.',
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
Totale da saldare a vista fattura: € 5.612,00`
    },
    {
      id: 'email-preventivo',
      title: 'Email Richiesta Preventivo',
      docType: 'EMAIL',
      routerType: 'local-ollama',
      description: 'Comunicazione via posta elettronica con mittente, urgenza e richiesta di offerta.',
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
Responsabile Sistemi Informativi - TechSolution S.r.l.`
    },
    {
      id: 'anagrafica-persona',
      title: 'Scheda Anagrafica & KYC',
      docType: 'ANAGRAFICA',
      routerType: 'local-ollama',
      description: 'Dati di identificazione persona fisica con Codice Fiscale e residenza.',
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
Email certificata: sofia.ferrari@pec.it`
    },
    {
      id: 'altro-verbale',
      title: 'Testo Generico (Classificato come ALTRO)',
      docType: null,
      routerType: 'reflex',
      description: 'Testo non catalogabile per verificare che il router server risponda autonomamente ALTRO.',
      payload: `VERBALE SPRINT REVIEW & RETROSPECTIVE - SPRINT 42
Data: 02 Novembre 2026 - Ore 10:00
Partecipanti: Lead Architect, Backend Engineer, QA Specialist.

Sintesi discussione:
Abbiamo verificato che le guardie simboliche deterministiche prevengono al 100% l'insorgenza di allucinazioni sintattiche sui campi chiave.
L'escalation automatica scatta in media nel 3.2% dei casi limite.
Decisioni prese:
- Consolidare i prompt di sistema in classpath
- Aggiornare i benchmark Wilson score al 95% di confidenza`
    }
  ];

  getPresets(): RequestPreset[] {
    return this.presets;
  }
}
