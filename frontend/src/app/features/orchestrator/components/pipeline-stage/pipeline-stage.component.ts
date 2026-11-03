import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent, IconName } from '../../../../shared/ui/icon/icon.component';
import { PipelineStageState } from '../../models/orchestrator-ui.models';

@Component({
  selector: 'app-pipeline-stage',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-stage.component.html',
  styleUrls: ['./pipeline-stage.component.scss']
})
export class PipelineStageComponent {
  /** Stato dello stadio della pipeline */
  readonly stage = input.required<PipelineStageState>();
  /** Se questo è l'ultimo stadio (quindi senza linea connettiva destra) */
  readonly isLast = input<boolean>(false);

  getStageIcon(): IconName {
    const s = this.stage();
    if (s.status === 'completed') return 'check';
    if (s.status === 'warning') return 'alert-circle';
    if (s.status === 'running') return 'refresh';

    // Inattivo: icona contestuale
    if (s.id === 'input') return 'file-text';
    if (s.id === 'router') return 'cpu';
    if (s.id === 'slm') return 'sparkles';
    return 'check-circle';
  }
}
