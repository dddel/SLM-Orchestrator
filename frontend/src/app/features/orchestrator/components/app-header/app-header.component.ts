import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { DemoControlComponent } from '../demo-control/demo-control.component';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [
    CommonModule,
    IconComponent,
    DemoControlComponent
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-header.component.html',
  styleUrls: ['./app-header.component.scss']
})
export class AppHeaderComponent {
  /** Se la demo è attiva */
  readonly isDemoRunning = input<boolean>(false);
  /** Se la demo è in pausa */
  readonly isDemoPaused = input<boolean>(false);

  /** Evento per avviare la demo */
  readonly startDemo = output<void>();
  /** Evento per mettere in pausa la demo */
  readonly pauseDemo = output<void>();
  /** Evento per fermare la demo */
  readonly stopDemo = output<void>();
  /** Evento per aprire il modale informazioni */
  readonly openInfo = output<void>();
}
