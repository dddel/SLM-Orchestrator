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
  /** Etichetta del parametro (es. "Tipo Documento" o "Router") */
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
  readonly highlightedIndex = signal<number>(-1);

  constructor(private elementRef: ElementRef) {}

  toggleDropdown(): void {
    if (this.disabled()) return;
    const next = !this.isOpen();
    this.isOpen.set(next);
    if (next) {
      const idx = this.options().findIndex((o) => o.value === this.selectedValue());
      this.highlightedIndex.set(idx >= 0 ? idx : 0);
    }
  }

  selectOption(val: string | null): void {
    this.valueChange.emit(val);
    this.isOpen.set(false);
  }

  getSelectedLabel(): string {
    const found = this.options().find((o) => o.value === this.selectedValue());
    return found ? found.label : 'Riconoscimento auto';
  }

  @HostListener('keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (this.disabled()) return;

    if (this.isOpen()) {
      if (event.key === 'Escape') {
        event.preventDefault();
        this.isOpen.set(false);
      } else if (event.key === 'ArrowDown') {
        event.preventDefault();
        const opts = this.options();
        this.highlightedIndex.update((curr) => (curr + 1) % opts.length);
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        const opts = this.options();
        this.highlightedIndex.update((curr) => (curr <= 0 ? opts.length - 1 : curr - 1));
      } else if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        const opts = this.options();
        const idx = this.highlightedIndex();
        if (idx >= 0 && idx < opts.length) {
          this.selectOption(opts[idx].value);
        }
      }
    } else {
      if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        this.toggleDropdown();
      }
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isOpen.set(false);
    }
  }
}
