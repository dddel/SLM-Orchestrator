import { TestBed } from '@angular/core/testing';
import { GuardItemComponent } from './guard-item.component';

describe('GuardItemComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GuardItemComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(GuardItemComponent);
    fixture.componentRef.setInput('guard', {
      id: 'g1',
      title: 'Test Guard',
      detail: 'Detail',
      status: 'passed'
    });
    expect(fixture.componentInstance).toBeTruthy();
  });
});
