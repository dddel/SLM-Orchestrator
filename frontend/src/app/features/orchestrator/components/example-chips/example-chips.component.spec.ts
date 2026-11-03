import { TestBed } from '@angular/core/testing';
import { ExampleChipsComponent } from './example-chips.component';

describe('ExampleChipsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExampleChipsComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(ExampleChipsComponent);
    fixture.componentRef.setInput('presets', []);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
