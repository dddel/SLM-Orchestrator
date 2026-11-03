import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrchestratorPageComponent } from './features/orchestrator/orchestrator-page/orchestrator-page.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, OrchestratorPageComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class App {
  constructor() {
    if (typeof document !== 'undefined' && typeof getComputedStyle !== 'undefined') {
      const surfaceColor = getComputedStyle(document.documentElement)
        .getPropertyValue('--surface')
        .trim();
      const meta = document.querySelector('meta[name="theme-color"]');
      if (meta && surfaceColor) {
        meta.setAttribute('content', surfaceColor);
      }
    }
  }
}
