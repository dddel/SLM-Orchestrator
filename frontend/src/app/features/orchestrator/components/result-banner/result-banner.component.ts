import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { BadgeComponent } from '../../../../shared/ui/badge/badge.component';
import { DocumentType, OrchestratorStatus } from '../../../../core/models/orchestrator.models';

@Component({
  selector: 'app-result-banner',
  standalone: true,
  imports: [CommonModule, IconComponent, BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './result-banner.component.html',
  styleUrls: ['./result-banner.component.scss']
})
export class ResultBannerComponent {
  /** Stato dell'orchestrazione */
  readonly status = input.required<OrchestratorStatus | 'ERROR'>();
  /** Etichetta del documento classificato */
  readonly label = input<DocumentType | null>(null);
  /** Router utilizzato */
  readonly router = input<string>('local-ollama');
  /** Latenza in millisecondi */
  readonly latencyMs = input<number>(0);
}
