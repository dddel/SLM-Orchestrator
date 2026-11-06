import { TestBed } from '@angular/core/testing';
import { DemoControlComponent } from './demo-control.component';
import { vi, describe, it, expect, beforeEach } from 'vitest';

describe('DemoControlComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DemoControlComponent]
    }).compileComponents();
  });

  it('dovrebbe istanziare il componente', () => {
    const fixture = TestBed.createComponent(DemoControlComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('dovrebbe emettere resume quando cliccato in stato di pausa', () => {
    const fixture = TestBed.createComponent(DemoControlComponent);
    const component = fixture.componentInstance;
    fixture.componentRef.setInput('isRunning', true);
    fixture.componentRef.setInput('isPaused', true);
    fixture.detectChanges();

    let resumeEmitted = false;
    component.resume.subscribe(() => {
      resumeEmitted = true;
    });

    component.onToggle();
    expect(resumeEmitted).toBe(true);
  });
});
