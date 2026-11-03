import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { OrchestratorPageComponent } from './features/orchestrator/orchestrator-page/orchestrator-page.component';
import { InputCardComponent } from './features/orchestrator/components/input-card/input-card.component';
import { ExampleChipsComponent } from './features/orchestrator/components/example-chips/example-chips.component';
import { OrchestratorService } from './core/services/orchestrator.service';

describe('SLM Orchestrator Redesign', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, OrchestratorPageComponent, InputCardComponent, ExampleChipsComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render brand title in header', async () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.brand-title')?.textContent).toContain('SLM Orchestrator');
  });

  it('should apply preset when selected from example chips', () => {
    const fixture = TestBed.createComponent(OrchestratorPageComponent);
    const page = fixture.componentInstance;
    fixture.detectChanges();

    const samplePreset = {
      id: 'test-preset',
      title: 'Fattura di Test',
      docType: 'FATTURA' as const,
      routerType: 'local-ollama' as const,
      description: 'Test description',
      payload: 'Fattura 123',
      expected: {
        status: 'OK' as const,
        detectedLabel: 'FATTURA' as const,
        summary: 'Test summary'
      }
    };

    page.onSelectPreset(samplePreset);
    expect(page.userPayload()).toBe('Fattura 123');
    expect(page.userDocType()).toBe('FATTURA');
    expect(page.userRouterType()).toBe('local-ollama');
    expect(page.effectivePayload()).toBe('Fattura 123');
  });

  it('should not allow selecting ALTRO in input document types', () => {
    const fixture = TestBed.createComponent(InputCardComponent);
    const component = fixture.componentInstance;
    const types = component.docTypeOptions.map((d) => d.value);
    expect(types).not.toContain('ALTRO');
    expect(types).toContain(null); // Auto-Detect
    expect(types).toContain('FATTURA');
    expect(types).toContain('EMAIL');
    expect(types).toContain('ANAGRAFICA');
  });

  it('should send post request to /api/orchestrator/process', () => {
    const service = TestBed.inject(OrchestratorService);
    service.process({ payload: 'test payload', type: 'FATTURA' }).subscribe((res) => {
      expect(res.status).toBe('OK');
      expect(res.label).toBe('FATTURA');
    });

    const req = httpTesting.expectOne('/api/orchestrator/process');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ payload: 'test payload', type: 'FATTURA' });

    req.flush({
      status: 'OK',
      label: 'FATTURA',
      router: 'local-ollama',
      result: '{"numeroFattura": "1"}',
      errors: [],
      actionErrors: [],
      latencyMs: 42
    });
  });

  it('should render example chips selector and include bad P.IVA demo example', () => {
    const fixture = TestBed.createComponent(OrchestratorPageComponent);
    const page = fixture.componentInstance;
    fixture.detectChanges();

    expect(page.presets.length).toBeGreaterThan(0);
    const pivaErrorPreset = page.presets.find((p) => p.id === 'fattura-piva-errata');
    expect(pivaErrorPreset).toBeDefined();
    expect(pivaErrorPreset?.expected.status).toBe('INDETERMINATE');
  });

  it('should enforce single dark theme with no theme toggle and hide Ctrl + Invio at rest', () => {
    const fixture = TestBed.createComponent(OrchestratorPageComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('app-theme-toggle')).toBeNull();
    expect(el.textContent).not.toContain('Ctrl + Invio');
    expect(el.textContent).not.toContain('Ctrl+Invio');

    const submitBtn = el.querySelector('.submit-btn') as HTMLButtonElement;
    expect(submitBtn).toBeTruthy();
    expect(submitBtn.getAttribute('aria-keyshortcuts')).toBe('Control+Enter Meta+Enter');
  });
});
