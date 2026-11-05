import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';

export interface ExtractedFieldItem {
  key: string;
  label: string;
  value: string;
  isWarning?: boolean;
}

@Component({
  selector: 'app-extracted-fields',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './extracted-fields.component.html',
  styleUrls: ['./extracted-fields.component.scss']
})
export class ExtractedFieldsComponent {
  /** Stringa JSON da cui estrarre i campi */
  readonly jsonContent = input.required<string>();
  /** Se vi sono errori nelle guardie per evidenziare campi anomali */
  readonly hasViolations = input<boolean>(false);

  readonly fields = computed<ExtractedFieldItem[]>(() => {
    const raw = this.jsonContent();
    if (!raw) return [];

    try {
      const parsed = JSON.parse(raw);
      if (typeof parsed !== 'object' || parsed === null) return [];

      const items: ExtractedFieldItem[] = [];
      this.flattenObject(parsed, '', items);
      return items;
    } catch {
      return [];
    }
  });

  private flattenObject(obj: any, prefix: string, acc: ExtractedFieldItem[]): void {
    for (const rawKey of Object.keys(obj)) {
      const val = obj[rawKey];
      const fullKey = prefix ? `${prefix}.${rawKey}` : rawKey;
      const humanLabel = this.formatKeyToLabel(fullKey);

      if (val !== null && typeof val === 'object' && !Array.isArray(val)) {
        this.flattenObject(val, fullKey, acc);
      } else if (Array.isArray(val)) {
        acc.push({
          key: fullKey,
          label: humanLabel,
          value: val.join(', ')
        });
      } else {
        const strVal = val !== undefined && val !== null ? String(val) : '—';
        const isSuspicious =
          this.hasViolations() &&
          (fullKey.includes('codiceFiscale') || fullKey.includes('partitaIva') || fullKey.includes('piva')) &&
          strVal.endsWith('9');

        acc.push({
          key: fullKey,
          label: humanLabel,
          value: strVal,
          isWarning: isSuspicious
        });
      }
    }
  }

  private formatKeyToLabel(key: string): string {
    const dictionary: Record<string, string> = {
      numeroFattura: 'Numero Fattura',
      dataFattura: 'Data Fattura',
      'fornitore.denominazione': 'Fornitore — Ragione Sociale',
      'fornitore.partitaIva': 'Fornitore — Partita IVA',
      'cliente.denominazione': 'Cliente — Ragione Sociale',
      'cliente.codiceFiscale': 'Cliente — C.F. / P.IVA',
      'cliente.partitaIva': 'Cliente — Partita IVA',
      importoTotale: 'Importo Totale (€)',
      iban: 'Codice IBAN',
      mittente: 'Mittente Email',
      oggetto: 'Oggetto Email',
      dataEmail: 'Data Ricezione',
      urgenza: 'Priorità Urgenza',
      sintesiRichiesta: 'Sintesi Richiesta',
      nome: 'Nome',
      cognome: 'Cognome',
      dataNascita: 'Data di Nascita',
      luogoNascita: 'Luogo di Nascita',
      codiceFiscale: 'Codice Fiscale',
      'indirizzoResidenza.via': 'Residenza — Indirizzo',
      'indirizzoResidenza.cap': 'Residenza — C.A.P.',
      'indirizzoResidenza.comune': 'Residenza — Comune',
      'indirizzoResidenza.provincia': 'Residenza — Provincia',
      pec: 'Posta Certificata PEC',
      categoriaRilevata: 'Categoria Rilevata',
      argomentoPrincipale: 'Argomento Principale',
      motivoEsclusione: 'Motivo Routing',
      entitaRilevate: 'Entità Rilevate'
    };

    if (dictionary[key]) return dictionary[key];

    // Fallback: da camelCase a parole spaziate
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, (str) => str.toUpperCase())
      .replace(/\./g, ' — ');
  }
}
