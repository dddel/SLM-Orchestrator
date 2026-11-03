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

  private prefersReducedMotion(): boolean {
    if (typeof window === 'undefined' || !window.matchMedia) return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  start(): void {
    if (this.isRunning()) return;
    this.isRunning.set(true);
    this.isPaused.set(false);
    this.currentItemIndex = 0;
    this.playItem(this.currentItemIndex);
  }

  pause(): void {
    if (!this.isRunning() || this.isPaused()) return;
    this.isPaused.set(true);
    this.clearTimer();
  }

  resume(): void {
    if (!this.isRunning() || !this.isPaused()) return;
    this.isPaused.set(false);
    // Riprendi dal passo successivo
    this.advanceNextDemoStep();
  }

  stop(): void {
    this.clearTimer();
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
      // Avanzamento istantaneo
      this.simulatedPayload.set(item.inputPayload);
      this.showCompleteResult(item);
      this.scheduleNext(item, 3500);
      return;
    }

    this.timerRef = setTimeout(() => {
      this.startTyping(item);
    }, 400);
  }

  // Passo b: Effetto macchina da scrivere (600-900 caratteri/sec)
  private startTyping(item: DemoItem): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('typing');
    const text = item.inputPayload;
    let charIdx = 0;
    // Chunk di ~15 caratteri ogni 20ms = ~750 caratteri/sec
    const chunkSize = 15;
    const intervalMs = 20;

    const typeChunk = () => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      charIdx = Math.min(charIdx + chunkSize, text.length);
      this.simulatedPayload.set(text.slice(0, charIdx));

      if (charIdx < text.length) {
        this.timerRef = setTimeout(typeChunk, intervalMs);
      } else {
        // Testo completato, passo c: Simulazione pressione Elabora
        this.timerRef = setTimeout(() => {
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

    this.timerRef = setTimeout(() => {
      this.isSubmitting.set(false);
      this.runPipelineStages(item);
    }, 350);
  }

  // Passo d: Pipeline che avanza stadio per stadio (Input -> Router -> SLM -> Guardie)
  private runPipelineStages(item: DemoItem): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('pipeline');

    // Stadio 1: Input completato
    this.pipelineStages.set([
      { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
      { id: 'router', label: 'Router', sublabel: 'Classificazione in corso...', status: 'running' },
      { id: 'slm', label: 'SLM', sublabel: 'In attesa', status: 'inactive' },
      { id: 'guards', label: 'Guardie', sublabel: 'In attesa', status: 'inactive' }
    ]);

    // Stadio 2: Router completato -> SLM running (~650ms)
    this.timerRef = setTimeout(() => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      this.pipelineStages.set([
        { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
        { id: 'router', label: 'Router', sublabel: item.router, status: 'completed' },
        { id: 'slm', label: 'SLM', sublabel: 'Inferenza Qwen 2.5 3B...', status: 'running' },
        { id: 'guards', label: 'Guardie', sublabel: 'In attesa', status: 'inactive' }
      ]);

      // Stadio 3: SLM completato -> Guardie running (~700ms)
      this.timerRef = setTimeout(() => {
        if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

        this.pipelineStages.set([
          { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
          { id: 'router', label: 'Router', sublabel: item.router, status: 'completed' },
          { id: 'slm', label: 'SLM', sublabel: 'JSON estratto', status: 'completed' },
          { id: 'guards', label: 'Guardie', sublabel: 'Validazione regole formali...', status: 'running' }
        ]);

        // Stadio 4: Guardie completate (OK -> completed, INDETERMINATE -> warning)
        this.timerRef = setTimeout(() => {
          if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

          const isWarning = item.expectedStatus === 'INDETERMINATE';
          this.pipelineStages.set([
            { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
            { id: 'router', label: 'Router', sublabel: item.router, status: 'completed' },
            { id: 'slm', label: 'SLM', sublabel: 'JSON estratto', status: 'completed' },
            {
              id: 'guards',
              label: 'Guardie',
              sublabel: isWarning ? 'Anomalia rilevata' : 'Tutti i controlli superati',
              status: isWarning ? 'warning' : 'completed'
            }
          ]);

          // Passo e ed f: stream JSON e accensione guardie
          this.streamJsonAndGuards(item);
        }, 700);
      }, 700);
    }, 650);
  }

  // Passo e: JSON che appare riga per riga + f: guardie che si accendono una alla volta
  private streamJsonAndGuards(item: DemoItem): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('stream_json');

    // Imposta la risposta simulata
    const response: OrchestratorResponse = {
      status: item.expectedStatus,
      label: item.detectedLabel,
      router: item.router,
      result: item.expectedStatus === 'OK' ? item.expectedJson : item.expectedJson,
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
    let lineIdx = 0;
    this.streamingJson.set('');

    const streamLine = () => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      lineIdx++;
      this.streamingJson.set(jsonLines.slice(0, lineIdx).join('\n'));

      if (lineIdx < jsonLines.length) {
        this.timerRef = setTimeout(streamLine, 35);
      } else {
        // JSON completato, accendiamo le guardie una alla volta
        this.turnOnGuards(item);
      }
    };

    streamLine();
  }

  private turnOnGuards(item: DemoItem): void {
    if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

    this.currentStep.set('guards');
    let guardIdx = 0;
    this.visibleGuards.set([]);

    const addNextGuard = () => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;

      if (guardIdx < item.guards.length) {
        this.visibleGuards.set(item.guards.slice(0, guardIdx + 1));
        guardIdx++;
        this.timerRef = setTimeout(addNextGuard, 250);
      } else {
        // Passo g: pausa di 3.5 secondi sul risultato completo prima del prossimo esempio
        this.currentStep.set('holding');
        this.scheduleNext(item, 3500);
      }
    };

    addNextGuard();
  }

  private showCompleteResult(item: DemoItem): void {
    const isWarning = item.expectedStatus === 'INDETERMINATE';
    this.pipelineStages.set([
      { id: 'input', label: 'Input', sublabel: 'Payload pronto', status: 'completed' },
      { id: 'router', label: 'Router', sublabel: item.router, status: 'completed' },
      { id: 'slm', label: 'SLM', sublabel: 'JSON estratto', status: 'completed' },
      {
        id: 'guards',
        label: 'Guardie',
        sublabel: isWarning ? 'Anomalia rilevata' : 'Tutti i controlli superati',
        status: isWarning ? 'warning' : 'completed'
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
    this.timerRef = setTimeout(() => {
      if (!this.isRunning() || this.isPaused() || this.isDestroyed) return;
      this.currentItemIndex = (this.currentItemIndex + 1) % DEMO_ITEMS.length;
      this.playItem(this.currentItemIndex);
    }, delayMs);
  }

  private advanceNextDemoStep(): void {
    this.currentItemIndex = (this.currentItemIndex + 1) % DEMO_ITEMS.length;
    this.playItem(this.currentItemIndex);
  }
}
