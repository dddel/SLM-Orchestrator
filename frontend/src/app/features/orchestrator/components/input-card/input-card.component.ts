import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  input,
  output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { TooltipComponent } from '../../../../shared/ui/tooltip/tooltip.component';
import { ParamSelectComponent } from '../param-select/param-select.component';
import { ParamOption } from '../../models/orchestrator-ui.models';
import { DocumentType, RouterType } from '../../../../core/models/orchestrator.models';

@Component({
  selector: 'app-input-card',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent, TooltipComponent, ParamSelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './input-card.component.html',
  styleUrls: ['./input-card.component.scss']
})
export class InputCardComponent {
  /** Testo del documento */
  readonly payload = input<string>('');
  /** Tipo documento selezionato */
  readonly selectedDocType = input<DocumentType | null>(null);
  /** Motore router selezionato */
  readonly selectedRouterType = input<RouterType | null>(null);
  /** Stato di caricamento (inferenza in corso) */
  readonly isLoading = input<boolean>(false);
  /** Se disabilitato */
  readonly disabled = input<boolean>(false);

  /** Modifica del payload */
  readonly payloadChange = output<string>();
  /** Modifica del tipo documento */
  readonly docTypeChange = output<DocumentType | null>();
  /** Modifica del motore router */
  readonly routerTypeChange = output<RouterType | null>();
  /** Invio del documento per elaborazione */
  readonly submitRequest = output<void>();
  /** Pulizia del testo */
  readonly clearPayload = output<void>();
  /** Notifica di interazione utente (es. digitazione o click per fermare demo) */
  readonly userInteracted = output<void>();

  readonly lineCount = computed(() => {
    const text = this.payload();
    return text ? text.split('\n').length : 0;
  });

  readonly charCount = computed(() => this.payload().length);

  readonly docTypeOptions: ParamOption[] = [
    { value: null, label: 'Auto-Detect', badge: 'Consigliato' },
    { value: 'FATTURA', label: 'Fattura Elettronica' },
    { value: 'EMAIL', label: 'Posta Elettronica' },
    { value: 'ANAGRAFICA', label: 'Scheda Anagrafica' }
  ];

  readonly routerOptions: ParamOption[] = [
    { value: null, label: 'Default di Sistema', badge: 'Auto' },
    { value: 'local-ollama', label: 'Ollama Qwen 3B' },
    { value: 'reflex', label: 'APUS Reflex Classifier' }
  ];

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.onSubmit();
    }
  }

  onInputText(val: string): void {
    this.userInteracted.emit();
    this.payloadChange.emit(val);
  }

  onFocus(): void {
    this.userInteracted.emit();
  }

  onSelectDocType(val: string | null): void {
    this.userInteracted.emit();
    this.docTypeChange.emit(val as DocumentType | null);
  }

  onSelectRouter(val: string | null): void {
    this.userInteracted.emit();
    this.routerTypeChange.emit(val as RouterType | null);
  }

  onSubmit(): void {
    if (!this.payload().trim() || this.isLoading() || this.disabled()) return;
    this.userInteracted.emit();
    this.submitRequest.emit();
  }

  onClear(): void {
    this.userInteracted.emit();
    this.clearPayload.emit();
  }
}
