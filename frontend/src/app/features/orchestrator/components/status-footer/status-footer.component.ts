import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-status-footer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './status-footer.component.html',
  styleUrls: ['./status-footer.component.scss']
})
export class StatusFooterComponent {
  /** Router attivo selezionato */
  readonly activeRouter = input<string | null>('local-ollama');

  readonly routerLabel = computed(() => {
    const r = this.activeRouter();
    if (r === 'reflex') return 'APUS Reflex Classifier';
    if (r === 'local-ollama') return 'Ollama Qwen 3B';
    return r || 'Ollama Qwen 3B';
  });
}
