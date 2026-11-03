import { TestBed } from '@angular/core/testing';
import { JsonViewerComponent } from './json-viewer.component';

describe('JsonViewerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JsonViewerComponent]
    }).compileComponents();
  });

  it('should create the component', () => {
    const fixture = TestBed.createComponent(JsonViewerComponent);
    fixture.componentRef.setInput('jsonContent', '{}');
    expect(fixture.componentInstance).toBeTruthy();
  });
});
