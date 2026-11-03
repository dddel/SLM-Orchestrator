import { TestBed } from '@angular/core/testing';
import { TooltipComponent } from './tooltip.component';

describe('TooltipComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TooltipComponent]
    }).compileComponents();
  });

  it('should create and not render tooltip text at rest', () => {
    const fixture = TestBed.createComponent(TooltipComponent);
    fixture.componentRef.setInput('text', 'Ctrl + Invio');
    fixture.componentRef.setInput('asKbd', true);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="tooltip"]')).toBeNull();
  });
});
