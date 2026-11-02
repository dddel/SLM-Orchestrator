import {
  ChangeDetectionStrategy,
  Component,
  input,
  model
} from '@angular/core';
import { IconComponent, IconName } from '../icon/icon.component';

@Component({
  selector: 'app-accordion-item',
  standalone: true,
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="accordion-item" [class.is-open]="isOpen()">
      <button
        type="button"
        class="accordion-header"
        (click)="toggle()"
        [attr.aria-expanded]="isOpen()"
      >
        <div class="header-left">
          @if (icon()) {
            <span class="header-icon-box">
              <app-icon [name]="icon()!" [size]="16"></app-icon>
            </span>
          }
          <div class="header-text">
            <span class="header-title">{{ title() }}</span>
            @if (subtitle()) {
              <span class="header-subtitle">{{ subtitle() }}</span>
            }
          </div>
        </div>

        <div class="header-right">
          @if (activeValue()) {
            <span class="active-badge">{{ activeValue() }}</span>
          }
          <span class="chevron" [class.rotated]="isOpen()">
            <app-icon name="chevron-down" [size]="16"></app-icon>
          </span>
        </div>
      </button>

      @if (isOpen()) {
        <div class="accordion-body">
          <ng-content></ng-content>
        </div>
      }
    </div>
  `,
  styles: [`
    .accordion-item {
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: var(--radius-md);
      transition: all var(--transition-smooth);
      overflow: hidden;

      &.is-open {
        border-color: var(--border-medium);
        background: var(--bg-surface);
        box-shadow: var(--shadow-sm);
      }
    }

    .accordion-header {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 14px;
      text-align: left;
      user-select: none;
      transition: background-color var(--transition-fast);

      &:hover {
        background-color: var(--bg-surface-elevated);
      }
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .header-icon-box {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: var(--radius-sm);
      background: var(--bg-surface-elevated);
      color: var(--text-muted);
      border: 1px solid var(--border-subtle);
    }

    .header-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .header-title {
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--text-main);
    }

    .header-subtitle {
      font-size: 0.72rem;
      color: var(--text-muted);
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .active-badge {
      font-size: 0.72rem;
      padding: 2px 7px;
      background: var(--bg-surface-elevated);
      color: var(--accent-primary);
      border: 1px solid var(--border-medium);
      border-radius: var(--radius-pill);
      font-weight: 500;
    }

    .chevron {
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--text-dim);
      transition: transform var(--transition-smooth);

      &.rotated {
        transform: rotate(180deg);
        color: var(--text-main);
      }
    }

    .accordion-body {
      padding: 12px 14px 14px;
      border-top: 1px solid var(--border-subtle);
      background: var(--bg-surface);
      animation: fadeIn 150ms ease-out;
    }

    @keyframes fadeIn {
      from {
        opacity: 0;
        transform: translateY(-4px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }
  `]
})
export class AccordionItemComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>('');
  readonly icon = input<IconName>();
  readonly activeValue = input<string>('');
  readonly isOpen = model<boolean>(false);

  toggle(): void {
    this.isOpen.update((v) => !v);
  }
}
