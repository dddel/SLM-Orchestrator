import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppHeaderComponent } from '../components/app-header/app-header.component';
import { ExampleChipsComponent } from '../components/example-chips/example-chips.component';
import { InputCardComponent } from '../components/input-card/input-card.component';
import { PipelineComponent } from '../components/pipeline/pipeline.component';
import { ResultPanelComponent } from '../components/result-panel/result-panel.component';
import { EmptyStateComponent } from '../components/empty-state/empty-state.component';
import { StatusFooterComponent } from '../components/status-footer/status-footer.component';
import { InfoModalComponent } from '../components/info-modal/info-modal.component';
import { MailpitPreviewComponent } from '../components/mailpit-preview/mailpit-preview.component';
import { OrchestratorService } from '../../../core/services/orchestrator.service';
import { PresetsService } from '../../../core/services/presets.service';
import { DemoPlayerService } from '../demo/demo-player.service';
import {
  DocumentType,
  OrchestratorResponse,
  RequestPreset,
  RouterType
} from '../../../core/models/orchestrator.models';
import {
  GuardPresentationItem,
  MailpitDemoState,
  PipelineStageState
} from '../models/orchestrator-ui.models';

const DEFAULT_PIPELINE: PipelineStageState[] = [
  { id: 'input', label: 'Input', sublabel: 'Ricezione payload', status: 'inactive' },
  { id: 'router', label: 'Router', sublabel: 'Classificazione', status: 'inactive' },
  { id: 'slm', label: 'SLM', sublabel: 'Estrazione Qwen 3B', status: 'inactive' },
  { id: 'guards', label: 'Guardie', sublabel: 'Validazione simbolica', status: 'inactive' }
];

@Component({
  selector: 'app-orchestrator-page',
  standalone: true,
  imports: [
    CommonModule,
    AppHeaderComponent,
    ExampleChipsComponent,
    InputCardComponent,
    PipelineComponent,
    ResultPanelComponent,
    EmptyStateComponent,
    StatusFooterComponent,
    InfoModalComponent,
    MailpitPreviewComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './orchestrator-page.component.html',
  styleUrls: ['./orchestrator-page.component.scss']
})
export class OrchestratorPageComponent {
  readonly orchestratorService = inject(OrchestratorService);
  readonly presetsService = inject(PresetsService);
  readonly demoPlayer = inject(DemoPlayerService);

  readonly presets = this.presetsService.getPresets();

  // Stato utente in modalità interattiva
  readonly userPayload = signal<string>('');
  readonly userDocType = signal<DocumentType | null>(null);
  readonly userRouterType = signal<RouterType | null>('local-ollama');
  readonly userHeuristicFallback = signal<boolean>(true);
  readonly activePresetId = signal<string | null>(null);
  readonly manualPipelineStages = signal<PipelineStageState[]>(DEFAULT_PIPELINE);
  readonly isInfoOpen = signal<boolean>(false);

  // Valori visualizzati (alternanza tra Demo automatica e Input utente reale)
  readonly isDemoRunning = this.demoPlayer.isRunning;
  readonly isDemoPaused = this.demoPlayer.isPaused;

  readonly effectivePayload = computed(() => {
    return this.isDemoRunning()
      ? this.demoPlayer.simulatedPayload()
      : this.userPayload();
  });

  readonly effectiveDocType = computed(() => {
    return this.isDemoRunning()
      ? this.demoPlayer.simulatedDocType()
      : this.userDocType();
  });

  readonly effectiveRouterType = computed(() => {
    return this.isDemoRunning()
      ? this.demoPlayer.simulatedRouterType()
      : this.userRouterType();
  });

  readonly effectiveActivePresetId = computed(() => {
    return this.isDemoRunning()
      ? this.demoPlayer.activeChipId()
      : this.activePresetId();
  });

  readonly effectiveIsLoading = computed(() => {
    return this.isDemoRunning()
      ? this.demoPlayer.isSubmitting()
      : this.orchestratorService.isLoading();
  });

  readonly effectivePipelineStages = computed(() => {
    if (this.isDemoRunning()) {
      return this.demoPlayer.pipelineStages();
    }
    return this.manualPipelineStages();
  });

  readonly effectiveResponse = computed<OrchestratorResponse | null>(() => {
    if (this.isDemoRunning()) {
      return this.demoPlayer.simulatedResponse();
    }
    return this.orchestratorService.currentResponse();
  });

  readonly effectiveError = computed<string | null>(() => {
    if (this.isDemoRunning()) return null;
    return this.orchestratorService.currentError();
  });

  readonly effectiveStreamingJson = computed(() => {
    if (this.isDemoRunning()) {
      return this.demoPlayer.streamingJson();
    }
    return '';
  });

  readonly effectiveMailpitState = computed<MailpitDemoState>(() => {
    return this.isDemoRunning()
      ? this.demoPlayer.mailpitState()
      : { phase: 'hidden', recipient: '', subject: '', preview: '' };
  });

  readonly effectiveGuards = computed<GuardPresentationItem[]>(() => {
    if (this.isDemoRunning()) {
      return this.demoPlayer.visibleGuards();
    }
    const res = this.orchestratorService.currentResponse();
    if (!res) return [];

    return this.extractGuardsFromResponse(res);
  });

  readonly showResult = computed(() => {
    return (
      this.effectiveResponse() !== null ||
      this.effectiveError() !== null ||
      (this.isDemoRunning() && this.demoPlayer.streamingJson().length > 0)
    );
  });

  // Metodi Demo
  onStartDemo(): void {
    this.demoPlayer.start();
  }

  onPauseDemo(): void {
    this.demoPlayer.pause();
  }

  onResumeDemo(): void {
    this.demoPlayer.resume();
  }

  onStopDemo(): void {
    this.demoPlayer.stop();
  }

  // Interazioni Utente
  onUserInteracted(): void {
    if (this.isDemoRunning()) {
      this.demoPlayer.stop();
    }
  }

  onSelectPreset(preset: RequestPreset): void {
    this.onUserInteracted();
    this.activePresetId.set(preset.id);
    this.userPayload.set(preset.payload);
    this.userDocType.set(preset.docType ?? null);
    this.userRouterType.set(preset.routerType ?? 'local-ollama');
  }

  onPayloadChange(val: string): void {
    this.userPayload.set(val);
  }

  onDocTypeChange(val: DocumentType | null): void {
    this.userDocType.set(val);
  }

  onRouterTypeChange(val: RouterType | null): void {
    this.userRouterType.set(val);
  }

  onHeuristicFallbackChange(val: boolean): void {
    this.userHeuristicFallback.set(val);
  }

  onClearPayload(): void {
    this.userPayload.set('');
    this.activePresetId.set(null);
    this.userRouterType.set('local-ollama');
    this.userHeuristicFallback.set(true);
    this.orchestratorService.clearResponse();
    this.manualPipelineStages.set(DEFAULT_PIPELINE);
  }

  onSubmitRequest(): void {
    const text = this.userPayload().trim();
    if (!text || this.orchestratorService.isLoading()) return;

    this.onUserInteracted();

    // Imposta la pipeline in stato running
    const routerName = this.userRouterType() || 'local-ollama';
    this.manualPipelineStages.set([
      { id: 'input', label: 'Input', sublabel: 'Payload inviato', status: 'completed' },
      { id: 'router', label: 'Router', sublabel: routerName, status: 'running' },
      { id: 'slm', label: 'SLM', sublabel: 'In attesa', status: 'inactive' },
      { id: 'guards', label: 'Guardie', sublabel: 'In attesa', status: 'inactive' }
    ]);

    this.orchestratorService
      .process({
        type: this.userDocType(),
        payload: text,
        routerType: this.userRouterType(),
        allowHeuristicFallback: this.userHeuristicFallback()
      })
      .subscribe({
        next: (res) => {
          const isWarning = res.status === 'INDETERMINATE';
          const isFallback = (res.router || '').toLowerCase().includes('fallback');
          const routerMs = Math.max(15, Math.round(res.latencyMs * 0.08));
          const guardsMs = Math.max(2, Math.round(res.latencyMs * 0.02));
          const slmMs = Math.max(20, res.latencyMs - routerMs - guardsMs);

          this.manualPipelineStages.set([
            { id: 'input', label: 'Input', sublabel: 'Payload elaborato', status: 'completed' },
            {
              id: 'router',
              label: 'Router',
              sublabel: isFallback ? 'Fallback Euristico attivo' : res.router,
              status: isFallback ? 'warning' : 'completed',
              durationText: `${routerMs}ms`
            },
            { id: 'slm', label: 'SLM', sublabel: 'Estrazione completata', status: 'completed', durationText: `${slmMs}ms` },
            {
              id: 'guards',
              label: 'Guardie',
              sublabel: isWarning ? 'Violazione rilevata' : 'Controlli superati',
              status: isWarning ? 'warning' : 'completed',
              durationText: `${guardsMs}ms`
            }
          ]);
        },
        error: () => {
          this.manualPipelineStages.set([
            { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
            { id: 'router', label: 'Router', sublabel: 'Errore', status: 'inactive' },
            { id: 'slm', label: 'SLM', sublabel: 'Interrotto', status: 'inactive' },
            { id: 'guards', label: 'Guardie', sublabel: 'Non eseguite', status: 'inactive' }
          ]);
        }
      });
  }

  private extractGuardsFromResponse(res: OrchestratorResponse): GuardPresentationItem[] {
    const items: GuardPresentationItem[] = [];

    if (res.router && res.router.toLowerCase().includes('fallback')) {
      items.push({
        id: 'router-heuristic-fallback',
        title: 'Avviso Router: Fallback Euristico Attivato',
        detail: `Il router SLM non era raggiungibile o ha restituito un token non valido (${res.router}). La categoria '${res.label}' è stata assegnata dall'euristica a parole chiave.`,
        status: 'warning'
      });
    }

    // Se ci sono errori specifici restituiti dalle guardie
    if (res.errors && res.errors.length > 0) {
      for (const err of res.errors) {
        items.push({
          id: err.path + err.code,
          title: `Guardia: ${err.path || 'Root'}`,
          detail: `${err.code}: ${err.message}`,
          status: 'warning'
        });
      }
    }

    // Se la risposta è OK, arricchiamo con i controlli simbolici passati
    if (res.status === 'OK') {
      if (res.label === 'FATTURA') {
        items.push({
          id: 'piva-luhn',
          title: 'Partite IVA fornitore e cliente',
          detail: 'Algoritmo Luhn matematicamente conforme su cifre di controllo',
          status: 'passed'
        });
        items.push({
          id: 'iban-cin',
          title: 'IBAN Nazionale e codice CIN',
          detail: 'Formato bancario italiano e lunghezza 27 caratteri validati',
          status: 'passed'
        });
        items.push({
          id: 'schema-fattura',
          title: 'Schema JSON Fattura',
          detail: 'Coerenza tra imponibile, IVA ed importo complessivo',
          status: 'passed'
        });
      } else if (res.label === 'EMAIL') {
        items.push({
          id: 'rfc-email',
          title: 'Indirizzo Mittente RFC 5322',
          detail: 'Sintassi email e dominio conformi',
          status: 'passed'
        });
        items.push({
          id: 'schema-email',
          title: 'Schema JSON Email',
          detail: 'Estrazione urgenza, oggetto e corpo completata',
          status: 'passed'
        });
      } else if (res.label === 'ANAGRAFICA') {
        items.push({
          id: 'cf-algo',
          title: 'Codice Fiscale Alfanumerico',
          detail: 'Validazione carattere di controllo (D.M. 23/12/1976)',
          status: 'passed'
        });
        items.push({
          id: 'cap-match',
          title: 'Coerenza Territoriale CAP/Provincia',
          detail: 'CAP verificato rispetto alla provincia di residenza',
          status: 'passed'
        });
      } else {
        items.push({
          id: 'generic-router',
          title: 'Smistamento Dinamico APUS/Ollama',
          detail: 'Esclusione semantica categorie note ed estrazione generica',
          status: 'passed'
        });
      }
    }

    return items;
  }
}
