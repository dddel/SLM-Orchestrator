import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';

@Component({
  selector: 'app-demo-control',
  standalone: true,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './demo-control.component.html',
  styleUrls: ['./demo-control.component.scss']
})
export class DemoControlComponent {
  /** Se la modalità demo è attualmente in esecuzione */
  readonly isRunning = input<boolean>(false);
  /** Se la modalità demo è temporaneamente in pausa */
  readonly isPaused = input<boolean>(false);

  /** Evento per avviare la demo */
  readonly start = output<void>();
  /** Evento per mettere in pausa la demo */
  readonly pause = output<void>();
  /** Evento per riprendere la demo */
  readonly resume = output<void>();
  /** Evento per fermare e resettare la demo */
  readonly stop = output<void>();

  onToggle(): void {
    if (!this.isRunning()) {
      this.start.emit();
    } else if (this.isPaused()) {
      this.resume.emit();
    } else {
      this.pause.emit();
    }
  }

  onStop(e: MouseEvent): void {
    e.stopPropagation();
    this.stop.emit();
  }
}
