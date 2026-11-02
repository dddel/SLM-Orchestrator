import {
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HeaderComponent } from './components/header/header.component';
import { RequestPanelComponent } from './components/request-panel/request-panel.component';
import { ResponseViewerComponent } from './components/response-viewer/response-viewer.component';
import { HistoryModalComponent } from './components/history-modal/history-modal.component';
import { PresetsModalComponent } from './components/presets-modal/presets-modal.component';
import {
  DocumentType,
  HistoryEntry,
  RequestPreset,
  RouterType
} from './core/models/orchestrator.models';
import { OrchestratorService } from './core/services/orchestrator.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    HeaderComponent,
    RequestPanelComponent,
    ResponseViewerComponent,
    HistoryModalComponent,
    PresetsModalComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class App {
  readonly orchestratorService = inject(OrchestratorService);

  readonly payload = signal<string>('');
  readonly selectedDocType = signal<DocumentType | null>(null);
  readonly selectedRouterType = signal<RouterType | null>(null);

  readonly isHistoryOpen = signal<boolean>(false);
  readonly isPresetsOpen = signal<boolean>(false);

  @ViewChild(RequestPanelComponent) requestPanel?: RequestPanelComponent;

  onSelectPreset(preset: RequestPreset): void {
    this.payload.set(preset.payload);
    this.selectedDocType.set(preset.docType ?? null);
    if (preset.routerType) {
      this.selectedRouterType.set(preset.routerType);
    }
  }

  onRestoreHistory(entry: HistoryEntry): void {
    this.payload.set(entry.request.payload);
    this.selectedDocType.set(entry.request.type || null);
    this.selectedRouterType.set(entry.request.routerType || null);

    // If history entry had a response, set it as current response
    if (entry.response) {
      this.orchestratorService.currentResponse.set(entry.response);
      this.orchestratorService.currentError.set(null);
    } else if (entry.errorMessage) {
      this.orchestratorService.currentError.set(entry.errorMessage);
      this.orchestratorService.currentResponse.set(null);
    }
  }
}
