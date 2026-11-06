import { TestBed } from '@angular/core/testing';
import { StatusFooterComponent } from './status-footer.component';
import { describe, it, expect, beforeEach } from 'vitest';

describe('StatusFooterComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusFooterComponent]
    }).compileComponents();
  });

  it('dovrebbe istanziare il componente', () => {
    const fixture = TestBed.createComponent(StatusFooterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('dovrebbe mostrare il router attivo nel testo', () => {
    const fixture = TestBed.createComponent(StatusFooterComponent);
    fixture.componentRef.setInput('activeRouter', 'reflex');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('APUS Reflex Classifier');
  });
});
