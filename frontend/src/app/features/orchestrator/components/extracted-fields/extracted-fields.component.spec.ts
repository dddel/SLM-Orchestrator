import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExtractedFieldsComponent } from './extracted-fields.component';

describe('ExtractedFieldsComponent', () => {
  let component: ExtractedFieldsComponent;
  let fixture: ComponentFixture<ExtractedFieldsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExtractedFieldsComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ExtractedFieldsComponent);
    component = fixture.componentInstance;
  });

  it('dovrebbe istanziarsi correttamente', () => {
    fixture.componentRef.setInput('jsonContent', '{"numeroFattura": "2024/01"}');
    fixture.detectChanges();
    expect(component).toBeTruthy();
    expect(component.fields().length).toBe(1);
    expect(component.fields()[0].label).toBe('Numero Fattura');
    expect(component.fields()[0].value).toBe('2024/01');
  });
});
