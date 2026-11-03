import { TestBed } from '@angular/core/testing';
import { ResultPanelComponent } from './result-panel.component';

describe('ResultPanelComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultPanelComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(ResultPanelComponent);
    fixture.componentRef.setInput('response', null);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
