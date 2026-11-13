import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MailpitDemoState } from '../../models/orchestrator-ui.models';

@Component({
  selector: 'app-mailpit-preview',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mailpit-preview.component.html',
  styleUrls: ['./mailpit-preview.component.scss']
})
export class MailpitPreviewComponent {
  readonly state = input.required<MailpitDemoState>();

  readonly phaseLabels: Record<MailpitDemoState['phase'], string> = {
    hidden: '',
    sending: 'Invio in corso',
    delivered: 'Catturata da Mailpit',
    replying: 'Risposta in preparazione',
    received: 'Risposta ricevuta'
  };
}
