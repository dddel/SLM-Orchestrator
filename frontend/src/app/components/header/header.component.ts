import {
  ChangeDetectionStrategy,
  Component,
  inject,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import { ThemeService } from '../../core/services/theme.service';
import { PresetsService } from '../../core/services/presets.service';
import { OrchestratorService } from '../../core/services/orchestrator.service';
import { RequestPreset } from '../../core/models/orchestrator.models';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, IconComponent, BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {
  readonly themeService = inject(ThemeService);
  readonly presetsService = inject(PresetsService);
  readonly orchestratorService = inject(OrchestratorService);

  readonly openHistory = output<void>();
  readonly selectPreset = output<RequestPreset>();

  readonly isPresetsMenuOpen = signal<boolean>(false);

  togglePresets(): void {
    this.isPresetsMenuOpen.update((v) => !v);
  }

  onSelectPreset(preset: RequestPreset): void {
    this.selectPreset.emit(preset);
    this.isPresetsMenuOpen.set(false);
  }
}
