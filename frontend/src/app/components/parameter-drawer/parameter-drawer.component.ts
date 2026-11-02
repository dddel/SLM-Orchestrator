import {
  ChangeDetectionStrategy,
  Component,
  model,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { AccordionItemComponent } from '../common/accordion/accordion-item.component';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import {
  DocumentType,
  RouterType
} from '../../core/models/orchestrator.models';

@Component({
  selector: 'app-parameter-drawer',
  standalone: true,
  imports: [
    CommonModule,
    AccordionItemComponent,
    IconComponent,
    BadgeComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './parameter-drawer.component.html',
  styleUrls: ['./parameter-drawer.component.scss']
})
export class ParameterDrawerComponent {
  readonly selectedDocType = model<DocumentType | null>(null);
  readonly selectedRouterType = model<RouterType | null>(null);

  // Expandable panel states
  readonly isDocTypeOpen = signal<boolean>(false);
  readonly isRouterOpen = signal<boolean>(false);

  // Tipi di documento selezionabili dall'utente per forzare l'instradamento
  // Nota: 'ALTRO' non è selezionabile in input, è esclusivamente la classificazione restituita dal server
  readonly documentTypes: Array<{
    type: DocumentType | null;
    label: string;
    description: string;
    badge: string;
  }> = [
    {
      type: null,
      label: 'Auto-Detect',
      description: 'Instradamento semantico automatico dedotto dal Router',
      badge: 'Raccomandato'
    },
    {
      type: 'FATTURA',
      label: 'Fattura Elettronica',
      description: 'Estrazione tabellare, validazione P.IVA, IBAN e imponibili',
      badge: 'FATTURA'
    },
    {
      type: 'EMAIL',
      label: 'Posta Elettronica',
      description: 'Analisi mittente, urgenza, oggetto e recipient verification',
      badge: 'EMAIL'
    },
    {
      type: 'ANAGRAFICA',
      label: 'Scheda Anagrafica',
      description: 'Estrazione dati persona/azienda, Codice Fiscale e residenza',
      badge: 'ANAGRAFICA'
    }
  ];

  readonly routerOptions: Array<{
    type: RouterType | null;
    label: string;
    description: string;
    badge: string;
  }> = [
    {
      type: null,
      label: 'Predefinito di Sistema',
      description: 'Utilizza la configurazione router attiva sul server Spring Boot',
      badge: 'Default'
    },
    {
      type: 'local-ollama',
      label: 'Ollama Semantico',
      description: 'Classificazione basata su prompt semantico con modello Qwen residente',
      badge: 'local-ollama'
    },
    {
      type: 'reflex',
      label: 'APUS Reflex',
      description: 'Classificatore discreto locale basato su modello APUS GGUF',
      badge: 'reflex'
    }
  ];

  selectDocType(type: DocumentType | null): void {
    this.selectedDocType.set(type);
  }

  selectRouter(type: RouterType | null): void {
    this.selectedRouterType.set(type);
  }
}
