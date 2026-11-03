import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      [class]="'btn ' + variant() + ' ' + size() + (loading() ? ' loading' : '')"
      [attr.aria-label]="ariaLabel() || null"
      (click)="clicked.emit($event)"
    >
      <ng-content></ng-content>
    </button>
  `,
  styles: [`
    .btn {
      position: relative;
      overflow: hidden;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--space-2);
      font-family: var(--font-sans);
      font-weight: var(--weight-medium);
      border-radius: var(--radius-pill);
      cursor: pointer;
      white-space: nowrap;
      user-select: none;
      line-height: 1;
      transition: transform var(--duration-fast) var(--ease-standard),
                  opacity var(--duration-fast) var(--ease-standard);
    }

    .btn::after {
      content: '';
      position: absolute;
      inset: 0;
      border-radius: inherit;
      background-color: var(--md-sys-color-on-surface);
      opacity: 0;
      pointer-events: none;
      transition: opacity var(--duration-fast) var(--ease-standard);
    }

    .btn:hover:not(:disabled)::after {
      opacity: 0.08;
    }

    .btn:focus-visible:not(:disabled)::after,
    .btn:active:not(:disabled)::after {
      opacity: 0.10;
    }

    .btn:active:not(:disabled) {
      transform: scale(0.985);
    }

    .btn:disabled {
      opacity: 0.38;
      cursor: not-allowed;
      pointer-events: none;
    }

    /* Sizes */
    .btn.sm {
      height: var(--chip-height);
      padding: 0 var(--space-3);
      font-size: var(--type-label-sm-size);
    }

    .btn.md {
      height: var(--btn-secondary-height);
      padding: 0 var(--space-4);
      font-size: var(--type-label-size);
    }

    .btn.lg {
      height: var(--btn-primary-height);
      padding: 0 var(--space-6);
      font-size: var(--type-label-lg-size);
    }

    /* Variants (M3 Filled, Tonal, Text, Error) */
    .btn.primary {
      background-color: var(--md-sys-color-primary);
      color: var(--md-sys-color-on-primary);
    }

    .btn.primary::after {
      background-color: var(--md-sys-color-on-primary);
    }

    .btn.secondary {
      background-color: var(--md-sys-color-secondary-container);
      color: var(--md-sys-color-on-secondary-container);
    }

    .btn.ghost {
      background-color: transparent;
      color: var(--md-sys-color-on-surface-variant);
    }

    .btn.danger {
      background-color: var(--md-sys-color-error-container);
      color: var(--md-sys-color-on-error-container);
    }
  `]
})
export class ButtonComponent {
  /** Tipo semantico del pulsante */
  readonly variant = input<ButtonVariant>('secondary');
  /** Dimensione del pulsante */
  readonly size = input<ButtonSize>('md');
  /** HTML button type */
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  /** Se disabilitato */
  readonly disabled = input<boolean>(false);
  /** Se in stato di caricamento */
  readonly loading = input<boolean>(false);
  /** Etichetta aria per accessibilità */
  readonly ariaLabel = input<string>('');

  /** Evento di click inoltrato */
  readonly clicked = output<MouseEvent>();
}
