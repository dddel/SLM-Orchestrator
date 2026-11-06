import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { OrchestratorPageComponent } from './orchestrator-page.component';

describe('OrchestratorPageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrchestratorPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(OrchestratorPageComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
