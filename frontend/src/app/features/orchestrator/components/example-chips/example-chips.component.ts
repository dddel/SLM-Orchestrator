import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RequestPreset } from '../../../../core/models/orchestrator.models';

@Component({
  selector: 'app-example-chips',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './example-chips.component.html',
  styleUrls: ['./example-chips.component.scss']
})
export class ExampleChipsComponent {
  /** Lista dei preset disponibili */
  readonly presets = input.required<RequestPreset[]>();
  /** ID del preset attualmente selezionato */
  readonly activePresetId = input<string | null>(null);
  /** Se i controlli sono disabilitati (es. durante inferenza) */
  readonly disabled = input<boolean>(false);

  /** Evento emesso alla selezione di un preset */
  readonly selectPreset = output<RequestPreset>();

  getShortLabel(preset: RequestPreset): string {
    if (preset.id === 'fattura-valida') return 'Fattura Valida';
    if (preset.id === 'fattura-piva-errata') return 'P.IVA Errata (Luhn)';
    if (preset.id === 'email-preventivo') return 'Email Preventivo';
    if (preset.id === 'anagrafica-persona') return 'Anagrafica KYC';
    if (preset.id === 'altro-verbale') return 'Verbale (ALTRO)';
    return preset.title;
  }
}
