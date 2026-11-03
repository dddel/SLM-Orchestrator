import { TestBed } from '@angular/core/testing';
import { DemoControlComponent } from './demo-control.component';

describe('DemoControlComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DemoControlComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(DemoControlComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
