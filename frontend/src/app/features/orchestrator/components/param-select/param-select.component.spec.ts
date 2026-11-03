import { TestBed } from '@angular/core/testing';
import { ParamSelectComponent } from './param-select.component';

describe('ParamSelectComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParamSelectComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(ParamSelectComponent);
    fixture.componentRef.setInput('label', 'Test');
    fixture.componentRef.setInput('options', []);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
