import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { GuardItemComponent } from '../guard-item/guard-item.component';
import { GuardPresentationItem } from '../../models/orchestrator-ui.models';

@Component({
  selector: 'app-guard-list',
  standalone: true,
  imports: [CommonModule, IconComponent, GuardItemComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guard-list.component.html',
  styleUrls: ['./guard-list.component.scss']
})
export class GuardListComponent {
  /** Elenco dei controlli simbolici eseguiti */
  readonly guards = input.required<GuardPresentationItem[]>();
}
