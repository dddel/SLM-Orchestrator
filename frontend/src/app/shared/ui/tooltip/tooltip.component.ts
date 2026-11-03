import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  OnDestroy,
  input,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-tooltip',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-content></ng-content>
    @if (isRendered()) {
      <span
        class="m3-tooltip"
        [class.is-visible]="isVisible()"
        role="tooltip"
      >
        @if (asKbd()) {
          <kbd class="tooltip-kbd">{{ text() }}</kbd>
        } @else {
          <span>{{ text() }}</span>
        }
      </span>
    }
  `,
  styles: [`
    :host {
      position: relative;
      display: inline-flex;
      align-items: center;
    }

    .m3-tooltip {
      position: absolute;
      bottom: calc(100% + var(--space-2));
      left: 50%;
      transform: translateX(-50%);
      z-index: 90;
      pointer-events: none;
      white-space: nowrap;
      padding: var(--space-1) var(--space-2);
      border-radius: var(--radius-tooltip);
      background-color: var(--md-sys-color-inverse-surface);
      color: var(--md-sys-color-inverse-on-surface);
      font-family: var(--font-sans);
      font-size: var(--type-label-sm-size);
      line-height: var(--type-label-sm-line);
      font-weight: var(--weight-medium);
      letter-spacing: var(--type-label-track);
      opacity: 0;
      transition: opacity var(--duration-fast) var(--ease-standard);
    }

    .m3-tooltip.is-visible {
      opacity: 1;
    }

    .tooltip-kbd {
      font-family: var(--font-mono);
      font-size: var(--type-label-sm-size);
      line-height: var(--type-label-sm-line);
      font-weight: var(--weight-medium);
      color: inherit;
      background: transparent;
      border: none;
      padding: 0;
    }

    @media (hover: none) and (pointer: coarse) {
      .m3-tooltip {
        display: none;
      }
    }
  `]
})
export class TooltipComponent implements OnDestroy {
  /** Testo del tooltip */
  readonly text = input.required<string>();
  /** Se rendere il testo dentro un elemento <kbd> discreto */
  readonly asKbd = input<boolean>(false);
  /** Ritardo di comparsa in ms (default M3: 500ms) */
  readonly delayMs = input<number>(500);

  readonly isRendered = signal<boolean>(false);
  readonly isVisible = signal<boolean>(false);

  private showTimer: ReturnType<typeof setTimeout> | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;
  private lastPointerWasTouch = false;

  @HostListener('pointerdown', ['$event'])
  onPointerDown(event: PointerEvent): void {
    if (event.pointerType === 'touch') {
      this.lastPointerWasTouch = true;
      this.hide();
    } else {
      this.lastPointerWasTouch = false;
    }
  }

  @HostListener('pointerenter', ['$event'])
  onPointerEnter(event: PointerEvent): void {
    if (event.pointerType === 'touch') {
      this.lastPointerWasTouch = true;
      return;
    }
    this.lastPointerWasTouch = false;
    this.scheduleShow();
  }

  @HostListener('pointerleave', ['$event'])
  onPointerLeave(event: PointerEvent): void {
    if (event.pointerType === 'touch') return;
    this.hide();
  }

  @HostListener('focusin')
  onFocusIn(): void {
    if (this.lastPointerWasTouch) return;
    this.scheduleShow();
  }

  @HostListener('focusout')
  onFocusOut(): void {
    this.hide();
  }

  @HostListener('keydown.escape')
  onEscape(): void {
    this.hide();
  }

  private scheduleShow(): void {
    this.clearTimers();
    this.showTimer = setTimeout(() => {
      this.isRendered.set(true);
      if (typeof requestAnimationFrame !== 'undefined') {
        requestAnimationFrame(() => {
          this.isVisible.set(true);
        });
      } else {
        this.isVisible.set(true);
      }
    }, this.delayMs());
  }

  private hide(): void {
    if (this.showTimer) {
      clearTimeout(this.showTimer);
      this.showTimer = null;
    }
    this.isVisible.set(false);
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
    }
    this.hideTimer = setTimeout(() => {
      this.isRendered.set(false);
    }, 150);
  }

  private clearTimers(): void {
    if (this.showTimer) {
      clearTimeout(this.showTimer);
      this.showTimer = null;
    }
    if (this.hideTimer) {
      clearTimeout(this.hideTimer);
      this.hideTimer = null;
    }
  }

  ngOnDestroy(): void {
    this.clearTimers();
  }
}
