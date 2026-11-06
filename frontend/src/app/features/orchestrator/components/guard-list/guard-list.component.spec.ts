import { TestBed } from '@angular/core/testing';
import { GuardListComponent } from './guard-list.component';

describe('GuardListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GuardListComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(GuardListComponent);
    fixture.componentRef.setInput('guards', []);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
