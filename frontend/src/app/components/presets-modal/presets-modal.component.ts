import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PresetsService } from '../../core/services/presets.service';
import { RequestPreset } from '../../core/models/orchestrator.models';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';

@Component({
  selector: 'app-presets-modal',
  standalone: true,
  imports: [CommonModule, IconComponent, BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './presets-modal.component.html',
  styleUrls: ['./presets-modal.component.scss']
})
export class PresetsModalComponent {
  readonly presetsService = inject(PresetsService);

  readonly close = output<void>();
  readonly applyPreset = output<RequestPreset>();

  readonly presets = this.presetsService.getPresets();
  readonly selectedPresetId = signal<string>(this.presets[0]?.id || '');

  readonly currentPreset = computed(() => {
    const id = this.selectedPresetId();
    return this.presets.find((p) => p.id === id) || this.presets[0];
  });

  selectPreset(id: string): void {
    this.selectedPresetId.set(id);
  }

  onApply(): void {
    const preset = this.currentPreset();
    if (preset) {
      this.applyPreset.emit(preset);
      this.close.emit();
    }
  }
}
