import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ExecutionMetrics } from '../../core/models/orchestrator.models';
import { IconComponent } from '../common/icon/icon.component';

@Component({
  selector: 'app-metrics-card',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './metrics-card.component.html',
  styleUrls: ['./metrics-card.component.scss']
})
export class MetricsCardComponent {
  readonly metrics = input<ExecutionMetrics | undefined>();
  readonly fallbackLatencyMs = input<number>(0);
}
