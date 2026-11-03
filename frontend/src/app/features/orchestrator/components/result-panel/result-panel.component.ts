import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ResultBannerComponent } from '../result-banner/result-banner.component';
import { JsonViewerComponent } from '../json-viewer/json-viewer.component';
import { GuardListComponent } from '../guard-list/guard-list.component';
import { GuardPresentationItem } from '../../models/orchestrator-ui.models';
import { OrchestratorResponse } from '../../../../core/models/orchestrator.models';

@Component({
  selector: 'app-result-panel',
  standalone: true,
  imports: [
    CommonModule,
    ResultBannerComponent,
    JsonViewerComponent,
    GuardListComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './result-panel.component.html',
  styleUrls: ['./result-panel.component.scss']
})
export class ResultPanelComponent {
  /** Risposta dall'orchestrazione (reale o simulata da demo) */
  readonly response = input.required<OrchestratorResponse | null>();
  /** Lista delle guardie simboliche */
  readonly guards = input<GuardPresentationItem[]>([]);
  /** Eventuale testo JSON in streaming progressivo durante la demo */
  readonly streamingJson = input<string>('');
  /** Eventuale errore generale */
  readonly errorMessage = input<string | null>(null);

  readonly displayJson = computed(() => {
    if (this.streamingJson()) return this.streamingJson();
    const res = this.response();
    if (!res) return '';
    const content = res.result || res.unvalidatedResult || '';
    try {
      const parsed = JSON.parse(content);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return content;
    }
  });
}
