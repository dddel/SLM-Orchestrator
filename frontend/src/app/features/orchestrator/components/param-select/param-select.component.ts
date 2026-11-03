import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  input,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent, IconName } from '../../../../shared/ui/icon/icon.component';
import { ParamOption } from '../../models/orchestrator-ui.models';

@Component({
  selector: 'app-param-select',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './param-select.component.html',
  styleUrls: ['./param-select.component.scss']
})
export class ParamSelectComponent {
  /** Etichetta del parametro (es. "Tipo Documento" o "Motore Router") */
  readonly label = input.required<string>();
  /** Icona da visualizzare a sinistra */
  readonly icon = input<IconName>('file-text');
  /** Opzioni selezionabili */
  readonly options = input.required<ParamOption[]>();
  /** Valore attualmente selezionato */
  readonly selectedValue = input<string | null>(null);
  /** Se il controllo è disabilitato */
  readonly disabled = input<boolean>(false);

  /** Evento emesso alla selezione di una nuova opzione */
  readonly valueChange = output<string | null>();

  readonly isOpen = signal<boolean>(false);

  constructor(private elementRef: ElementRef) {}

  toggleDropdown(): void {
    if (this.disabled()) return;
    this.isOpen.update((v) => !v);
  }

  selectOption(val: string | null): void {
    this.valueChange.emit(val);
    this.isOpen.set(false);
  }

  getSelectedLabel(): string {
    const found = this.options().find((o) => o.value === this.selectedValue());
    return found ? found.label : 'Predefinito';
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isOpen.set(false);
    }
  }
}
