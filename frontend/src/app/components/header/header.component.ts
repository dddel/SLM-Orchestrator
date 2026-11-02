import {
  ChangeDetectionStrategy,
  Component,
  inject,
  output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import { ThemeService } from '../../core/services/theme.service';
import { OrchestratorService } from '../../core/services/orchestrator.service';

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
  readonly orchestratorService = inject(OrchestratorService);

  readonly openHistory = output<void>();
  readonly openPresetsModal = output<void>();
}
