import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type BadgeVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'accent';

@Component({
  selector: 'app-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="'badge-chip ' + variant() + ' ' + (size() === 'sm' ? 'badge-sm' : '')">
      @if (dot()) {
        <span class="badge-dot"></span>
      }
      <ng-content></ng-content>
    </span>
  `,
  styles: [`
    .badge-chip {
      display: inline-flex;
      align-items: center;
      gap: var(--space-1-5);
      padding: var(--space-1) var(--space-3);
      font-size: var(--type-label-sm-size);
      font-weight: var(--weight-medium);
      font-family: var(--font-sans);
      border-radius: var(--radius-pill);
      letter-spacing: var(--type-label-track);
      white-space: nowrap;
      line-height: var(--type-label-sm-line);
    }

    .badge-sm {
      padding: var(--space-0-5) var(--space-2);
      font-size: var(--type-label-sm-size);
    }

    .badge-dot {
      width: var(--dot-size);
      height: var(--dot-size);
      border-radius: var(--radius-pill);
      background-color: currentColor;
    }

    .badge-chip.success {
      background-color: var(--md-sys-color-success);
      color: var(--md-sys-color-on-success);
    }

    .badge-chip.warning {
      background-color: var(--md-sys-color-warning);
      color: var(--md-sys-color-on-warning);
    }

    .badge-chip.danger {
      background-color: var(--md-sys-color-error);
      color: var(--md-sys-color-on-error);
    }

    .badge-chip.info {
      background-color: var(--md-sys-color-secondary-container);
      color: var(--md-sys-color-on-secondary-container);
    }

    .badge-chip.accent {
      background-color: var(--md-sys-color-primary-container);
      color: var(--md-sys-color-on-primary-container);
    }

    .badge-chip.neutral {
      background-color: var(--md-sys-color-surface-container-highest);
      color: var(--md-sys-color-on-surface-variant);
    }
  `]
})
export class BadgeComponent {
  /** Variante semantica del badge */
  readonly variant = input<BadgeVariant>('neutral');
  /** Dimensione (default: 'md') */
  readonly size = input<'sm' | 'md'>('md');
  /** Se mostrare il pallino colorato a sinistra */
  readonly dot = input<boolean>(false);
}
