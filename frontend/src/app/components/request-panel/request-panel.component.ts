import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  computed,
  inject,
  input,
  model,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ParameterDrawerComponent } from '../parameter-drawer/parameter-drawer.component';
import { IconComponent } from '../common/icon/icon.component';
import { BadgeComponent } from '../common/badge/badge.component';
import { OrchestratorService } from '../../core/services/orchestrator.service';
import { PresetsService } from '../../core/services/presets.service';
import {
  CustomParam,
  DocumentType,
  RequestPreset,
  RouterType
} from '../../core/models/orchestrator.models';

@Component({
  selector: 'app-request-panel',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ParameterDrawerComponent,
    IconComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './request-panel.component.html',
  styleUrls: ['./request-panel.component.scss']
})
export class RequestPanelComponent {
  readonly orchestratorService = inject(OrchestratorService);
  readonly presetsService = inject(PresetsService);

  readonly payload = model<string>('');
  readonly selectedDocType = model<DocumentType | null>(null);
  readonly selectedRouterType = model<RouterType | null>(null);
  readonly customParams = model<CustomParam[]>([]);
  readonly baseUrl = model<string>('');

  readonly lineCount = computed(() => {
    const text = this.payload();
    return text ? text.split('\n').length : 0;
  });

  readonly charCount = computed(() => this.payload().length);

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent): void {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      this.submitRequest();
    }
  }

  submitRequest(): void {
    const text = this.payload().trim();
    if (!text || this.orchestratorService.isLoading()) return;

    this.orchestratorService
      .process(
        {
          type: this.selectedDocType(),
          payload: text,
          routerType: this.selectedRouterType()
        },
        this.customParams(),
        this.baseUrl()
      )
      .subscribe({
        next: () => {},
        error: () => {}
      });
  }

  clearPayload(): void {
    this.payload.set('');
  }

  applyPreset(preset: RequestPreset): void {
    this.payload.set(preset.payload);
    this.selectedDocType.set(preset.docType);
    if (preset.routerType) {
      this.selectedRouterType.set(preset.routerType);
    }
  }
}
