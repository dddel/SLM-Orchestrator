import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-status-footer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './status-footer.component.html',
  styleUrls: ['./status-footer.component.scss']
})
export class StatusFooterComponent {}
