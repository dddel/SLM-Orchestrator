import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { OrchestratorService } from './core/services/orchestrator.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render brand title', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-title')?.textContent).toContain('SLM Orchestrator');
  });

  it('should apply preset correctly', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    app.onSelectPreset({
      id: 'test-preset',
      title: 'Test Fattura',
      docType: 'FATTURA',
      routerType: 'local-ollama',
      description: 'Test description',
      payload: 'Fattura 123'
    });
    expect(app.payload()).toBe('Fattura 123');
    expect(app.selectedDocType()).toBe('FATTURA');
    expect(app.selectedRouterType()).toBe('local-ollama');
  });

  it('should reject non-local base url for security', () => {
    const service = TestBed.inject(OrchestratorService);
    let thrownError: Error | null = null;
    service.process({ payload: 'test' }, [], 'https://malicious-external-site.com').subscribe({
      error: (err: unknown) => {
        thrownError = err as Error;
      }
    });
    expect(thrownError).toBeTruthy();
    expect((thrownError as Error | null)?.message).toContain('Per motivi di sicurezza');
  });
});
