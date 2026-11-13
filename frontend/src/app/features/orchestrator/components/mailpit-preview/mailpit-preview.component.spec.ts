import { TestBed } from '@angular/core/testing';
import { MailpitPreviewComponent } from './mailpit-preview.component';

describe('MailpitPreviewComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MailpitPreviewComponent]
    }).compileComponents();
  });

  it('renders the Mailpit link after the simulated reply is received', () => {
    const fixture = TestBed.createComponent(MailpitPreviewComponent);
    fixture.componentRef.setInput('state', {
      phase: 'received',
      recipient: 'cliente@example.test',
      subject: 'Elaborazione email completata',
      preview: 'La tua richiesta è stata elaborata con successo.'
    });

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Risposta ricevuta');
    expect(fixture.nativeElement.querySelector('a[href="http://localhost:8025"]')).toBeTruthy();
  });
});
