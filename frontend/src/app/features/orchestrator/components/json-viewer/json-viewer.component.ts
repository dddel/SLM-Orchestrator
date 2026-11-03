import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';

export interface JsonSyntaxToken {
  type: 'key' | 'string' | 'number' | 'punct' | 'plain';
  text: string;
}

@Component({
  selector: 'app-json-viewer',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './json-viewer.component.html',
  styleUrls: ['./json-viewer.component.scss']
})
export class JsonViewerComponent {
  /** Stringa JSON formattata o grezza */
  readonly jsonContent = input.required<string>();
  /** Titolo del blocco (default: "JSON Estratto") */
  readonly title = input<string>('JSON Estratto');

  readonly isCopied = signal<boolean>(false);

  readonly tokens = computed<JsonSyntaxToken[]>(() => {
    const raw = this.jsonContent() || '';
    if (!raw) return [];

    const pattern =
      /"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b|[{}\[\]:,]|"(?:\\.|[^"\\])*$|\s+|[^\s{}\[\]:,"]+/g;
    const result: JsonSyntaxToken[] = [];
    let match: RegExpExecArray | null;

    while ((match = pattern.exec(raw)) !== null) {
      const text = match[0];
      const after = raw.slice(match.index + text.length);

      if (text.startsWith('"')) {
        const isKey = /^\s*:/.test(after);
        result.push({ type: isKey ? 'key' : 'string', text });
      } else if (/^-?\d/.test(text) || /^(?:true|false|null)$/.test(text)) {
        result.push({ type: 'number', text });
      } else if (/^[{}\[\]:,]$/.test(text)) {
        result.push({ type: 'punct', text });
      } else {
        result.push({ type: 'plain', text });
      }
    }

    return result;
  });

  async copyJson(): Promise<void> {
    const text = this.jsonContent();
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      this.isCopied.set(true);
      setTimeout(() => this.isCopied.set(false), 2000);
    } catch {
      // Fallback
    }
  }

  downloadJson(): void {
    const text = this.jsonContent();
    if (!text) return;
    const blob = new Blob([text], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `orchestrator-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }
}
