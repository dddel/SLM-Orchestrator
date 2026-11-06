import { TestBed } from '@angular/core/testing';
import { AppHeaderComponent } from './app-header.component';

describe('AppHeaderComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppHeaderComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(AppHeaderComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the brand title', () => {
    const fixture = TestBed.createComponent(AppHeaderComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.brand-title')?.textContent).toContain('SLM Orchestrator');
  });
});
