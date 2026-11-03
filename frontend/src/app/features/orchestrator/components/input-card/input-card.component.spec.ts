import { TestBed } from '@angular/core/testing';
import { InputCardComponent } from './input-card.component';

describe('InputCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InputCardComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(InputCardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
