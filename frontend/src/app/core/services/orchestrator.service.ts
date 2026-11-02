import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, catchError, map, throwError } from 'rxjs';
import {
  CustomParam,
  HistoryEntry,
  OrchestratorRequest,
  OrchestratorResponse
} from '../models/orchestrator.models';

@Injectable({
  providedIn: 'root'
})
export class OrchestratorService {
  private readonly http = inject(HttpClient);
  private readonly HISTORY_KEY = 'slm_orchestrator_history_v1';

  readonly isLoading = signal<boolean>(false);
  readonly currentResponse = signal<OrchestratorResponse | null>(null);
  readonly currentError = signal<string | null>(null);
  readonly history = signal<HistoryEntry[]>([]);

  constructor() {
    this.loadHistory();
  }

  process(
    request: OrchestratorRequest,
    customParams: CustomParam[] = [],
    baseUrl: string = ''
  ): Observable<OrchestratorResponse> {
    this.isLoading.set(true);
    this.currentError.set(null);

    const startTime = performance.now();

    // Construct final payload object merging clean core request + enabled custom parameters
    const body: Record<string, unknown> = {
      payload: request.payload
    };

    if (request.type) {
      body['type'] = request.type;
    }
    if (request.routerType) {
      body['routerType'] = request.routerType;
    }

    // Attach any enabled free custom parameters
    for (const param of customParams) {
      if (param.enabled && param.key.trim()) {
        body[param.key.trim()] = param.value;
      }
    }

    const cleanBaseUrl = baseUrl ? baseUrl.replace(/\/+$/, '') : '';
    const endpoint = `${cleanBaseUrl}/api/orchestrator/process`;

    return this.http.post<OrchestratorResponse>(endpoint, body).pipe(
      map((response) => {
        const clientLatency = Math.round(performance.now() - startTime);
        this.isLoading.set(false);
        this.currentResponse.set(response);

        this.addHistoryEntry({
          id: crypto.randomUUID(),
          timestamp: new Date().toISOString(),
          request,
          response,
          latencyMs: response.latencyMs || clientLatency,
          status: response.status
        });

        return response;
      }),
      catchError((error: HttpErrorResponse) => {
        const clientLatency = Math.round(performance.now() - startTime);
        this.isLoading.set(false);

        let errorMsg = 'Si è verificato un errore durante la richiesta.';
        if (error.error && typeof error.error === 'object' && error.error.message) {
          errorMsg = error.error.message;
        } else if (typeof error.error === 'string' && error.error.trim()) {
          errorMsg = error.error;
        } else if (error.message) {
          errorMsg = error.message;
        }

        this.currentError.set(errorMsg);

        this.addHistoryEntry({
          id: crypto.randomUUID(),
          timestamp: new Date().toISOString(),
          request,
          errorMessage: errorMsg,
          latencyMs: clientLatency,
          status: 'ERROR'
        });

        return throwError(() => new Error(errorMsg));
      })
    );
  }

  clearResponse(): void {
    this.currentResponse.set(null);
    this.currentError.set(null);
  }

  private loadHistory(): void {
    try {
      const data = localStorage.getItem(this.HISTORY_KEY);
      if (data) {
        this.history.set(JSON.parse(data));
      }
    } catch {
      this.history.set([]);
    }
  }

  private addHistoryEntry(entry: HistoryEntry): void {
    const list = [entry, ...this.history()].slice(0, 30);
    this.history.set(list);
    try {
      localStorage.setItem(this.HISTORY_KEY, JSON.stringify(list));
    } catch {
      // ignore storage quota errors
    }
  }

  deleteHistoryEntry(id: string): void {
    const list = this.history().filter((item) => item.id !== id);
    this.history.set(list);
    try {
      localStorage.setItem(this.HISTORY_KEY, JSON.stringify(list));
    } catch {
      // ignore
    }
  }

  clearHistory(): void {
    this.history.set([]);
    try {
      localStorage.removeItem(this.HISTORY_KEY);
    } catch {
      // ignore
    }
  }
}
