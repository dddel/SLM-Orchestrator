import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent, IconName } from '../../../../shared/ui/icon/icon.component';
import { RequestPreset } from '../../../../core/models/orchestrator.models';

@Component({
  selector: 'app-example-chips',
  standalone: true,
  imports: [CommonModule, IconComponent],
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

  getPresetIcon(preset: RequestPreset): IconName {
    if (preset.id.startsWith('fattura') || preset.docType === 'FATTURA') return 'receipt';
    if (preset.id === 'email-preventivo' || preset.docType === 'EMAIL') return 'mail';
    if (preset.id === 'anagrafica-persona' || preset.docType === 'ANAGRAFICA') return 'user';
    return 'file-text';
  }
}
