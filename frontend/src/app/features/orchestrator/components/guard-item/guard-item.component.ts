import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { GuardPresentationItem } from '../../models/orchestrator-ui.models';

@Component({
  selector: 'app-guard-item',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guard-item.component.html',
  styleUrls: ['./guard-item.component.scss']
})
export class GuardItemComponent {
  /** Oggetto guardia con esito, titolo e dettaglio */
  readonly guard = input.required<GuardPresentationItem>();
}
