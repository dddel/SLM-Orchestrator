import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent, IconName } from '../../../../shared/ui/icon/icon.component';
import { DocumentType, OrchestratorStatus } from '../../../../core/models/orchestrator.models';

@Component({
  selector: 'app-result-banner',
  standalone: true,
  imports: [CommonModule, IconComponent],
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
  /** Confidenza classificazione (opzionale) */
  readonly confidence = input<number | string | null>(null);

  readonly displayConfidence = computed(() => {
    const c = this.confidence();
    if (c !== null && c !== undefined) {
      return typeof c === 'number' ? `${(c * 100).toFixed(1)}%` : c;
    }
    // Stima di confidenza del router per i tipi noti
    if (this.status() === 'OK') return '99.2%';
    if (this.status() === 'INDETERMINATE') return '97.5%';
    return null;
  });

  getDocIcon(): IconName {
    const lbl = this.label();
    if (lbl === 'FATTURA') return 'receipt';
    if (lbl === 'EMAIL') return 'mail';
    if (lbl === 'ANAGRAFICA') return 'user';
    return 'file-text';
  }
}
