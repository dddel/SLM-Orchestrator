import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type IconName =
  | 'play'
  | 'sparkles'
  | 'cpu'
  | 'sliders'
  | 'layers'
  | 'file-text'
  | 'chevron-down'
  | 'chevron-up'
  | 'check'
  | 'copy'
  | 'trash'
  | 'refresh'
  | 'plus'
  | 'x'
  | 'alert-circle'
  | 'check-circle'
  | 'clock'
  | 'terminal'
  | 'history'
  | 'sun'
  | 'moon'
  | 'external-link'
  | 'database'
  | 'arrow-right'
  | 'zap';

@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      [class]="'icon-svg ' + customClass()"
    >
      @switch (name()) {
        @case ('play') {
          <polygon points="5 3 19 12 5 21 5 3"></polygon>
        }
        @case ('sparkles') {
          <path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z"></path>
          <path d="M5 3v4"></path>
          <path d="M19 17v4"></path>
          <path d="M3 5h4"></path>
          <path d="M17 19h4"></path>
        }
        @case ('cpu') {
          <rect x="4" y="4" width="16" height="16" rx="2"></rect>
          <rect x="9" y="9" width="6" height="6"></rect>
          <path d="M9 1v3"></path>
          <path d="M15 1v3"></path>
          <path d="M9 20v3"></path>
          <path d="M15 20v3"></path>
          <path d="M20 9h3"></path>
          <path d="M20 14h3"></path>
          <path d="M1 9h3"></path>
          <path d="M1 14h3"></path>
        }
        @case ('sliders') {
          <line x1="4" y1="21" x2="4" y2="14"></line>
          <line x1="4" y1="10" x2="4" y2="3"></line>
          <line x1="12" y1="21" x2="12" y2="12"></line>
          <line x1="12" y1="8" x2="12" y2="3"></line>
          <line x1="20" y1="21" x2="20" y2="16"></line>
          <line x1="20" y1="12" x2="20" y2="3"></line>
          <line x1="1" y1="14" x2="7" y2="14"></line>
          <line x1="9" y1="8" x2="15" y2="8"></line>
          <line x1="17" y1="16" x2="23" y2="16"></line>
        }
        @case ('layers') {
          <polygon points="12 2 2 7 12 12 22 7 12 2"></polygon>
          <polyline points="2 17 12 22 22 17"></polyline>
          <polyline points="2 12 12 17 22 12"></polyline>
        }
        @case ('file-text') {
          <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"></path>
          <polyline points="14 2 14 8 20 8"></polyline>
          <line x1="16" y1="13" x2="8" y2="13"></line>
          <line x1="16" y1="17" x2="8" y2="17"></line>
          <line x1="10" y1="9" x2="8" y2="9"></line>
        }
        @case ('chevron-down') {
          <polyline points="6 9 12 15 18 9"></polyline>
        }
        @case ('chevron-up') {
          <polyline points="18 15 12 9 6 15"></polyline>
        }
        @case ('check') {
          <polyline points="20 6 9 17 4 12"></polyline>
        }
        @case ('check-circle') {
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
          <polyline points="22 4 12 14.01 9 11.01"></polyline>
        }
        @case ('copy') {
          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
        }
        @case ('trash') {
          <polyline points="3 6 5 6 21 6"></polyline>
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
        }
        @case ('refresh') {
          <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path>
        }
        @case ('plus') {
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <line x1="5" y1="12" x2="19" y2="12"></line>
        }
        @case ('x') {
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        }
        @case ('alert-circle') {
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="10"></circle>
          <polyline points="12 6 12 12 16 14"></polyline>
        }
        @case ('terminal') {
          <polyline points="4 17 10 11 4 5"></polyline>
          <line x1="12" y1="19" x2="20" y2="19"></line>
        }
        @case ('history') {
          <path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"></path>
          <path d="M3 3v5h5"></path>
          <polyline points="12 7 12 12 15 15"></polyline>
        }
        @case ('sun') {
          <circle cx="12" cy="12" r="5"></circle>
          <line x1="12" y1="1" x2="12" y2="3"></line>
          <line x1="12" y1="21" x2="12" y2="23"></line>
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
          <line x1="1" y1="12" x2="3" y2="12"></line>
          <line x1="21" y1="12" x2="23" y2="12"></line>
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
        }
        @case ('moon') {
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
        }
        @case ('external-link') {
          <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"></path>
          <polyline points="15 3 21 3 21 9"></polyline>
          <line x1="10" y1="14" x2="21" y2="3"></line>
        }
        @case ('database') {
          <ellipse cx="12" cy="5" rx="9" ry="3"></ellipse>
          <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"></path>
          <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"></path>
        }
        @case ('arrow-right') {
          <line x1="5" y1="12" x2="19" y2="12"></line>
          <polyline points="12 5 19 12 12 19"></polyline>
        }
        @case ('zap') {
          <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
        }
      }
    </svg>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }
    .icon-svg {
      display: block;
      vertical-align: middle;
      transition: transform var(--transition-fast);
    }
  `]
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly size = input<number>(18);
  readonly customClass = input<string>('');
}
