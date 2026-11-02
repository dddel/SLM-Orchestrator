import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrchestratorService } from '../../core/services/orchestrator.service';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import { MetricsCardComponent } from '../metrics-card/metrics-card.component';

export type ResponseTab = 'result' | 'metrics' | 'errors' | 'unvalidated';

@Component({
  selector: 'app-response-viewer',
  standalone: true,
  imports: [CommonModule, IconComponent, BadgeComponent, MetricsCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './response-viewer.component.html',
  styleUrls: ['./response-viewer.component.scss']
})
export class ResponseViewerComponent {
  readonly orchestratorService = inject(OrchestratorService);

  readonly activeTab = signal<ResponseTab>('result');
  readonly isCopied = signal<boolean>(false);

  readonly response = this.orchestratorService.currentResponse;
  readonly error = this.orchestratorService.currentError;
  readonly isLoading = this.orchestratorService.isLoading;

  readonly formattedJsonResult = computed(() => {
    const res = this.response();
    if (!res || !res.result) return '';
    try {
      const parsed = JSON.parse(res.result);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return res.result;
    }
  });

  readonly formattedUnvalidated = computed(() => {
    const res = this.response();
    if (!res || !res.unvalidatedResult) return '';
    try {
      const parsed = JSON.parse(res.unvalidatedResult);
      return JSON.stringify(parsed, null, 2);
    } catch {
      return res.unvalidatedResult;
    }
  });

  setTab(tab: ResponseTab): void {
    this.activeTab.set(tab);
  }

  async copyResult(): Promise<void> {
    const text = this.activeTab() === 'unvalidated' ? this.formattedUnvalidated() : this.formattedJsonResult();
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      this.isCopied.set(true);
      setTimeout(() => this.isCopied.set(false), 2000);
    } catch {
      // Fallback
    }
  }

  downloadResult(): void {
    const text = this.formattedJsonResult();
    if (!text) return;
    const blob = new Blob([text], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `orchestrator-result-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
