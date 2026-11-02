import {
  ChangeDetectionStrategy,
  Component,
  inject,
  output
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrchestratorService } from '../../core/services/orchestrator.service';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import { HistoryEntry } from '../../core/models/orchestrator.models';

@Component({
  selector: 'app-history-modal',
  standalone: true,
  imports: [CommonModule, IconComponent, BadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './history-modal.component.html',
  styleUrls: ['./history-modal.component.scss']
})
export class HistoryModalComponent {
  readonly orchestratorService = inject(OrchestratorService);

  readonly close = output<void>();
  readonly restoreEntry = output<HistoryEntry>();

  onSelect(entry: HistoryEntry): void {
    this.restoreEntry.emit(entry);
    this.close.emit();
  }
}
