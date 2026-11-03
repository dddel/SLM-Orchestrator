import { TestBed } from '@angular/core/testing';
import { PipelineComponent } from './pipeline.component';

describe('PipelineComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(PipelineComponent);
    fixture.componentRef.setInput('stages', []);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
