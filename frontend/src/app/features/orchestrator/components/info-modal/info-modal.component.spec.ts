import { TestBed } from '@angular/core/testing';
import { InfoModalComponent } from './info-modal.component';

describe('InfoModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InfoModalComponent]
    }).compileComponents();
  });

  it('should render authors, API contract, summary and licenses', () => {
    const fixture = TestBed.createComponent(InfoModalComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    // Autori
    expect(el.textContent).toContain('Giuseppe Preta');
    expect(el.textContent).toContain('Stefano Vena');
    expect(el.textContent).toContain('Carmine Dodaro');

    // API contract
    expect(el.textContent).toContain('/api/orchestrator/process');
    expect(el.textContent).toContain('OrchestratorRequest');
    expect(el.textContent).toContain('OrchestratorResponse');

    // Riassunto
    expect(el.textContent).toContain('neuro-simbolica');
    expect(el.textContent).toContain('Qwen');

    // Licenze
    expect(el.textContent).toContain('Apache 2.0');
  });

  it('should emit close on close button click', () => {
    const fixture = TestBed.createComponent(InfoModalComponent);
    fixture.detectChanges();
    let closed = false;
    fixture.componentInstance.close.subscribe(() => {
      closed = true;
    });

    const closeBtn = fixture.nativeElement.querySelector('.close-btn') as HTMLButtonElement;
    closeBtn.click();
    expect(closed).toBe(true);
  });
});
