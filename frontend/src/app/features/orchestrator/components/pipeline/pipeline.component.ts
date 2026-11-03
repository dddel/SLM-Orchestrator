import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PipelineStageComponent } from '../pipeline-stage/pipeline-stage.component';
import { PipelineStageState } from '../../models/orchestrator-ui.models';

@Component({
  selector: 'app-pipeline',
  standalone: true,
  imports: [CommonModule, PipelineStageComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline.component.html',
  styleUrls: ['./pipeline.component.scss']
})
export class PipelineComponent {
  /** Array dei 4 stadi della pipeline neuro-simbolica */
  readonly stages = input.required<PipelineStageState[]>();
}
