import { Injectable, OnDestroy, signal } from '@angular/core';
import { DEMO_ITEMS, DemoItem } from './demo-data';
import {
  DemoStep,
  GuardPresentationItem,
  PipelineStageState
} from '../models/orchestrator-ui.models';
import { DocumentType, OrchestratorResponse, RouterType } from '../../../core/models/orchestrator.models';

const INITIAL_PIPELINE_STAGES: PipelineStageState[] = [
  { id: 'input', label: 'Input', sublabel: 'Ricezione payload', status: 'inactive' },
  { id: 'router', label: 'Router', sublabel: 'Classificazione', status: 'inactive' },
  { id: 'slm', label: 'SLM', sublabel: 'Estrazione Qwen 3B', status: 'inactive' },
  { id: 'guards', label: 'Guardie', sublabel: 'Validazione simbolica', status: 'inactive' }
];

@Injectable({
  providedIn: 'root'
})
export class DemoPlayerService implements OnDestroy {
  readonly isRunning = signal<boolean>(false);
  readonly isPaused = signal<boolean>(false);
  readonly currentStep = signal<DemoStep>('idle');
  readonly currentDemoItem = signal<DemoItem | null>(null);
  readonly activeChipId = signal<string | null>(null);

  readonly simulatedPayload = signal<string>('');
  readonly simulatedDocType = signal<DocumentType | null>(null);
  readonly simulatedRouterType = signal<RouterType | null>(null);
  readonly isSubmitting = signal<boolean>(false);

  readonly pipelineStages = signal<PipelineStageState[]>(INITIAL_PIPELINE_STAGES);
  readonly simulatedResponse = signal<OrchestratorResponse | null>(null);
  readonly visibleGuards = signal<GuardPresentationItem[]>([]);
  readonly streamingJson = signal<string>('');

  private currentItemIndex = 0;
  private timerRef: any = null;
  private isDestroyed = false;

  // Meccanismo di ripresa deterministico
  private pendingResumeAction: (() => void) | null = null;
  private scheduledDelay = 0;
  private timerStartTimestamp = 0;
  private remainingDelay = 0;

  ngOnDestroy(): void {
    this.isDestroyed = true;
    this.clearTimer();
  }

  private clearTimer(): void {
    if (this.timerRef) {
      clearTimeout(this.timerRef);
      this.timerRef = null;
    }
  }

  private scheduleAction(action: () => void, delayMs: number): void {
    this.clearTimer();
    this.scheduledDelay = delayMs;
    this.remainingDelay = delayMs;
    this.timerStartTimestamp = Date.now();
    this.pendingResumeAction = action;

    this.timerRef = setTimeout(() => {
      this.timerRef = null;
      this.pendingResumeAction = null;
      this.remainingDelay = 0;
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;
      action();
    }, delayMs);
  }

  private prefersReducedMotion(): boolean {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  start(): void {
    if (this.isRunning()) {
      if (this.isPaused()) {
        this.resume();
      }
      return;
    }
    this.isRunning.set(true);
    this.isPaused.set(false);
    this.currentItemIndex = 0;
    this.playItem(this.currentItemIndex);
  }

  pause(): void {
    if (!this.isRunning() || this.isPaused()) return;
    this.isPaused.set(true);

    if (this.timerRef) {
      clearTimeout(this.timerRef);
      this.timerRef = null;
      const elapsed = Date.now() - this.timerStartTimestamp;
      this.remainingDelay = Math.max(0, this.scheduledDelay - elapsed);
    }
  }

  resume(): void {
    if (!this.isRunning() || !this.isPaused()) return;
    this.isPaused.set(false);

    if (this.pendingResumeAction) {
      const action = this.pendingResumeAction;
      const delay = Math.max(15, this.remainingDelay);
      this.scheduleAction(action, delay);
    } else {
      const item = this.currentDemoItem();
      if (item) {
        this.resumeFromCurrentStep(item);
      } else {
        this.playItem(this.currentItemIndex);
      }
    }
  }

  stop(): void {
    this.clearTimer();
    this.pendingResumeAction = null;
    this.remainingDelay = 0;
    this.isRunning.set(false);
    this.isPaused.set(false);
    this.currentStep.set('idle');
    this.currentDemoItem.set(null);
    this.activeChipId.set(null);
    this.simulatedPayload.set('');
    this.simulatedDocType.set(null);
    this.simulatedRouterType.set(null);
    this.isSubmitting.set(false);
    this.pipelineStages.set(INITIAL_PIPELINE_STAGES);
    this.simulatedResponse.set(null);
    this.visibleGuards.set([]);
    this.streamingJson.set('');
  }

  onUserInteracted(): void {
    if (this.isRunning()) {
      this.stop();
    }
  }

  private playItem(index: number): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    const item = DEMO_ITEMS[index % DEMO_ITEMS.length];
    this.currentDemoItem.set(item);
    this.activeChipId.set(item.id);
    this.simulatedDocType.set(item.docType);
    this.simulatedRouterType.set(item.router);
    this.simulatedResponse.set(null);
    this.visibleGuards.set([]);
    this.streamingJson.set('');
    this.pipelineStages.set(INITIAL_PIPELINE_STAGES);

    // Passo a: Selezione del chip
    this.currentStep.set('select_chip');
    this.simulatedPayload.set('');

    const reduced = this.prefersReducedMotion();
    if (reduced) {
      this.simulatedPayload.set(item.inputPayload);
      this.showCompleteResult(item);
      this.scheduleNext(item, 3500);
      return;
    }

    this.scheduleAction(() => {
      this.startTyping(item);
    }, 400);
  }

  // Passo b: Effetto macchina da scrivere
  private startTyping(item: DemoItem, startCharIdx = 0): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('typing');
    const text = item.inputPayload;
    let charIdx = startCharIdx;
    const chunkSize = 15;
    const intervalMs = 20;

    const typeChunk = () => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      charIdx = Math.min(charIdx + chunkSize, text.length);
      this.simulatedPayload.set(text.slice(0, charIdx));

      if (charIdx < text.length) {
        this.scheduleAction(typeChunk, intervalMs);
      } else {
        this.scheduleAction(() => {
          this.triggerSimulatedSubmit(item);
        }, 300);
      }
    };

    typeChunk();
  }

  // Passo c: Simulazione submit
  private triggerSimulatedSubmit(item: DemoItem): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('submit');
    this.isSubmitting.set(true);

    this.scheduleAction(() => {
      this.isSubmitting.set(false);
      this.runPipelineStages(item);
    }, 350);
  }

  // Passo d: Pipeline (Input -> Router -> SLM -> Guardie)
  private runPipelineStages(item: DemoItem): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('pipeline');

    // Stadio 1: Input pronto, Router in corso
    this.pipelineStages.set([
      { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
      { id: 'router', label: 'Router', sublabel: 'Classificazione in corso...', status: 'running' },
      { id: 'slm', label: 'SLM', sublabel: 'In attesa', status: 'inactive' },
      { id: 'guards', label: 'Guardie', sublabel: 'In attesa', status: 'inactive' }
    ]);

    // Stadio 2: Router completato -> SLM running
    this.scheduleAction(() => {
      this.pipelineStages.set([
        { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
        { id: 'router', label: 'Router', sublabel: item.router, status: 'completed', durationText: '45ms' },
        { id: 'slm', label: 'SLM', sublabel: 'Inferenza Qwen 2.5 3B...', status: 'running' },
        { id: 'guards', label: 'Guardie', sublabel: 'In attesa', status: 'inactive' }
      ]);

      // Stadio 3: SLM completato -> Guardie running
      this.scheduleAction(() => {
        const slmMs = Math.max(300, item.latencyMs - 50);
        this.pipelineStages.set([
          { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
          { id: 'router', label: 'Router', sublabel: item.router, status: 'completed', durationText: '45ms' },
          { id: 'slm', label: 'SLM', sublabel: 'JSON estratto', status: 'completed', durationText: `${slmMs}ms` },
          { id: 'guards', label: 'Guardie', sublabel: 'Validazione regole formali...', status: 'running' }
        ]);

        // Stadio 4: Guardie completate
        this.scheduleAction(() => {
          const isWarning = item.expectedStatus === 'INDETERMINATE';
          this.pipelineStages.set([
            { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
            { id: 'router', label: 'Router', sublabel: item.router, status: 'completed', durationText: '45ms' },
            { id: 'slm', label: 'SLM', sublabel: 'JSON estratto', status: 'completed', durationText: `${slmMs}ms` },
            {
              id: 'guards',
              label: 'Guardie',
              sublabel: isWarning ? 'Anomalia rilevata' : 'Tutti i controlli superati',
              status: isWarning ? 'warning' : 'completed',
              durationText: '3ms'
            }
          ]);

          this.streamJsonAndGuards(item);
        }, 700);
      }, 700);
    }, 650);
  }

  // Passo e: stream JSON riga per riga
  private streamJsonAndGuards(item: DemoItem, startLineIdx = 0): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('stream_json');

    const response: OrchestratorResponse = {
      status: item.expectedStatus,
      label: item.detectedLabel,
      router: item.router,
      result: item.expectedJson,
      unvalidatedResult: item.expectedStatus === 'INDETERMINATE' ? item.expectedJson : undefined,
      errors:
        item.expectedStatus === 'INDETERMINATE'
          ? [
              {
                path: 'cliente.codiceFiscale',
                code: 'SOURCE_CHECKSUM_INVALID',
                message: "Checksum Luhn invalido per la P.IVA '98765432109' (atteso 3, trovato 9)."
              }
            ]
          : [],
      actionErrors: [],
      latencyMs: item.latencyMs
    };
    this.simulatedResponse.set(response);

    const jsonLines = item.expectedJson.split('\n');
    let lineIdx = startLineIdx;
    if (lineIdx === 0) {
      this.streamingJson.set('');
    }

    const streamLine = () => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      lineIdx++;
      this.streamingJson.set(jsonLines.slice(0, lineIdx).join('\n'));

      if (lineIdx < jsonLines.length) {
        this.scheduleAction(streamLine, 35);
      } else {
        this.turnOnGuards(item);
      }
    };

    streamLine();
  }

  // Passo f: accensione progressiva guardie
  private turnOnGuards(item: DemoItem, startGuardIdx = 0): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('guards');
    let guardIdx = startGuardIdx;
    if (guardIdx === 0) {
      this.visibleGuards.set([]);
    }

    const addNextGuard = () => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      if (guardIdx < item.guards.length) {
        this.visibleGuards.set(item.guards.slice(0, guardIdx + 1));
        guardIdx++;
        this.scheduleAction(addNextGuard, 250);
      } else {
        this.currentStep.set('holding');
        this.scheduleNext(item, 3500);
      }
    };

    addNextGuard();
  }

  private showCompleteResult(item: DemoItem): void {
    const isWarning = item.expectedStatus === 'INDETERMINATE';
    const slmMs = Math.max(300, item.latencyMs - 50);
    this.pipelineStages.set([
      { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
      { id: 'router', label: 'Router', sublabel: item.router, status: 'completed', durationText: '45ms' },
      { id: 'slm', label: 'SLM', sublabel: 'JSON estratto', status: 'completed', durationText: `${slmMs}ms` },
      {
        id: 'guards',
        label: 'Guardie',
        sublabel: isWarning ? 'Anomalia rilevata' : 'Tutti i controlli superati',
        status: isWarning ? 'warning' : 'completed',
        durationText: '3ms'
      }
    ]);
    this.streamingJson.set(item.expectedJson);
    this.visibleGuards.set(item.guards);
    this.simulatedResponse.set({
      status: item.expectedStatus,
      label: item.detectedLabel,
      router: item.router,
      result: item.expectedJson,
      errors: [],
      actionErrors: [],
      latencyMs: item.latencyMs
    });
  }

  private scheduleNext(item: DemoItem, delayMs: number): void {
    this.scheduleAction(() => {
      this.currentItemIndex = (this.currentItemIndex + 1) % DEMO_ITEMS.length;
      this.playItem(this.currentItemIndex);
    }, delayMs);
  }

  private resumeFromCurrentStep(item: DemoItem): void {
    const step = this.currentStep();
    switch (step) {
      case 'typing':
        this.startTyping(item, this.simulatedPayload().length);
        break;
      case 'submit':
        this.triggerSimulatedSubmit(item);
        break;
      case 'pipeline':
        this.runPipelineStages(item);
        break;
      case 'stream_json':
        this.streamJsonAndGuards(item, this.streamingJson().split('\n').length);
        break;
      case 'guards':
        this.turnOnGuards(item, this.visibleGuards().length);
        break;
      case 'holding':
        this.scheduleNext(item, 2000);
        break;
      default:
        this.playItem(this.currentItemIndex);
        break;
    }
  }
}
