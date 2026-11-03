import { TestBed } from '@angular/core/testing';
import { StatusFooterComponent } from './status-footer.component';

describe('StatusFooterComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusFooterComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(StatusFooterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
