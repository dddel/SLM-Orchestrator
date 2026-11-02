import {
  ChangeDetectionStrategy,
  Component,
  input,
  model,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccordionItemComponent } from '../common/accordion/accordion-item.component';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import {
  CustomParam,
  DocumentType,
  RouterType
} from '../../core/models/orchestrator.models';

@Component({
  selector: 'app-parameter-drawer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
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
  readonly customParams = model<CustomParam[]>([]);
  readonly baseUrl = model<string>('');

  // Expandable panel states
  readonly isDocTypeOpen = signal<boolean>(false);
  readonly isRouterOpen = signal<boolean>(false);
  readonly isCustomParamsOpen = signal<boolean>(false);
  readonly isConnectionOpen = signal<boolean>(false);

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
    },
    {
      type: 'ALTRO',
      label: 'Documento Altro',
      description: 'Pipeline flessibile per documenti generici non standard',
      badge: 'ALTRO'
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
      label: 'Ollama Semantico (local-ollama)',
      description: 'Classificazione basata su prompt semantico con modello Qwen residente',
      badge: 'Zero Hallucinations'
    },
    {
      type: 'reflex',
      label: 'APUS Reflex (reflex)',
      description: 'Classificatore discreto locale ultra-rapido (GGUF Q4_K_M)',
      badge: 'Bassa Latenza'
    }
  ];

  selectDocType(type: DocumentType | null): void {
    this.selectedDocType.set(type);
  }

  selectRouter(type: RouterType | null): void {
    this.selectedRouterType.set(type);
  }

  addCustomParam(): void {
    const newParam: CustomParam = {
      id: crypto.randomUUID(),
      key: '',
      value: '',
      enabled: true
    };
    this.customParams.update((list) => [...list, newParam]);
    this.isCustomParamsOpen.set(true);
  }

  addPresetParam(key: string, defaultValue: string = ''): void {
    const exists = this.customParams().some((p) => p.key.toLowerCase() === key.toLowerCase());
    if (!exists) {
      this.customParams.update((list) => [
        ...list,
        {
          id: crypto.randomUUID(),
          key,
          value: defaultValue,
          enabled: true
        }
      ]);
      this.isCustomParamsOpen.set(true);
    }
  }

  removeCustomParam(id: string): void {
    this.customParams.update((list) => list.filter((p) => p.id !== id));
  }

  toggleCustomParam(id: string): void {
    this.customParams.update((list) =>
      list.map((p) => (p.id === id ? { ...p, enabled: !p.enabled } : p))
    );
  }

  updateParamKey(id: string, newKey: string): void {
    this.customParams.update((list) =>
      list.map((p) => (p.id === id ? { ...p, key: newKey } : p))
    );
  }

  updateParamValue(id: string, newValue: string): void {
    this.customParams.update((list) =>
      list.map((p) => (p.id === id ? { ...p, value: newValue } : p))
    );
  }
}
