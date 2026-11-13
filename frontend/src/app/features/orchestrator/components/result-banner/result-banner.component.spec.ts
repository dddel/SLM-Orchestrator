import { TestBed } from '@angular/core/testing';
import { ResultBannerComponent } from './result-banner.component';

describe('ResultBannerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultBannerComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(ResultBannerComponent);
    fixture.componentRef.setInput('status', 'OK');
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display heuristic fallback warning when router indicates fallback', () => {
    const fixture = TestBed.createComponent(ResultBannerComponent);
    fixture.componentRef.setInput('status', 'OK');
    fixture.componentRef.setInput('label', 'FATTURA');
    fixture.componentRef.setInput('router', 'Fallback Euristico');
    fixture.detectChanges();

    expect(fixture.componentInstance.isHeuristicFallback()).toBe(true);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Fallback Euristico Attivo');
  });
});
