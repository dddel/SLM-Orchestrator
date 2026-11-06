import { TestBed } from '@angular/core/testing';
import { PipelineStageComponent } from './pipeline-stage.component';

describe('PipelineStageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineStageComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(PipelineStageComponent);
    fixture.componentRef.setInput('stage', {
      id: 'input',
      label: 'Input',
      sublabel: 'Test',
      status: 'inactive'
    });
    expect(fixture.componentInstance).toBeTruthy();
  });
});
