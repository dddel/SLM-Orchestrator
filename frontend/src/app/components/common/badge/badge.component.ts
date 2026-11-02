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
      gap: 6px;
      padding: 3px 9px;
      font-size: 0.75rem;
      font-weight: 500;
      border-radius: var(--radius-pill);
      letter-spacing: 0.02em;
      white-space: nowrap;
      transition: all var(--transition-fast);
      border: 1px solid transparent;
    }

    .badge-sm {
      padding: 1px 6px;
      font-size: 0.7rem;
    }

    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background-color: currentColor;
    }

    .badge-chip.success {
      background-color: var(--success-bg);
      color: var(--success);
      border-color: var(--success-border);
    }

    .badge-chip.warning {
      background-color: var(--warning-bg);
      color: var(--warning);
      border-color: var(--warning-border);
    }

    .badge-chip.danger {
      background-color: var(--danger-bg);
      color: var(--danger);
      border-color: var(--danger-border);
    }

    .badge-chip.info {
      background-color: var(--info-bg);
      color: var(--info);
      border-color: var(--info-border);
    }

    .badge-chip.accent {
      background-color: var(--accent-primary-glow);
      color: var(--accent-primary);
      border-color: var(--border-focus);
    }

    .badge-chip.neutral {
      background-color: var(--bg-surface-elevated);
      color: var(--text-muted);
      border-color: var(--border-subtle);
    }
  `]
})
export class BadgeComponent {
  readonly variant = input<BadgeVariant>('neutral');
  readonly size = input<'sm' | 'md'>('md');
  readonly dot = input<boolean>(false);
}
