import { TestBed } from '@angular/core/testing';
import { ResultBannerComponent } from './result-banner.component';

describe('ResultBannerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultBannerComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(ResultBannerComponent);
    fixture.componentRef.setInput('status', 'OK');
    expect(fixture.componentInstance).toBeTruthy();
  });
});
