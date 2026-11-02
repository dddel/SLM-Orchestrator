import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { OrchestratorService } from './core/services/orchestrator.service';
import { ParameterDrawerComponent } from './components/parameter-drawer/parameter-drawer.component';
import { PresetsModalComponent } from './components/presets-modal/presets-modal.component';

describe('App', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App, ParameterDrawerComponent],
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
      payload: 'Fattura 123',
      expected: {
        status: 'OK',
        detectedLabel: 'FATTURA',
        summary: 'Test summary'
      }
    });
    expect(app.payload()).toBe('Fattura 123');
    expect(app.selectedDocType()).toBe('FATTURA');
    expect(app.selectedRouterType()).toBe('local-ollama');
  });

  it('should not allow selecting ALTRO in input document types', () => {
    const fixture = TestBed.createComponent(ParameterDrawerComponent);
    const component = fixture.componentInstance;
    const types = component.documentTypes.map((d) => d.type);
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
      router: 'CLIENT_SPECIFIED',
      result: '{"numeroFattura": "1"}',
      errors: [],
      actionErrors: [],
      latencyMs: 42
    });
  });

  it('should render presets modal with comparison and allow selection', () => {
    const fixture = TestBed.createComponent(PresetsModalComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.presets.length).toBeGreaterThan(0);

    // Default selection is the first preset
    expect(component.selectedPresetId()).toBe(component.presets[0].id);

    // Find the P.IVA checksum error preset
    const pivaErrorPreset = component.presets.find((p) => p.id === 'fattura-piva-errata');
    expect(pivaErrorPreset).toBeDefined();
    expect(pivaErrorPreset?.expected.status).toBe('INDETERMINATE');
    expect(pivaErrorPreset?.expected.errors?.[0].code).toBe('SOURCE_CHECKSUM_INVALID');

    // Switch selection
    component.selectPreset('fattura-piva-errata');
    expect(component.selectedPresetId()).toBe('fattura-piva-errata');
    expect(component.currentPreset()?.id).toBe('fattura-piva-errata');

    // Test apply emission
    let appliedPresetId: string | null = null;
    component.applyPreset.subscribe((preset) => {
      appliedPresetId = preset.id;
    });
    component.onApply();
    expect(appliedPresetId).toBe('fattura-piva-errata');
  });
});
