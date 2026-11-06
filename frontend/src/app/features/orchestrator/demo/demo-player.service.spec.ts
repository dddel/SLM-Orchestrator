import { TestBed } from '@angular/core/testing';
import { DemoPlayerService } from './demo-player.service';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';

describe('DemoPlayerService', () => {
  let service: DemoPlayerService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [DemoPlayerService]
    });
    service = TestBed.inject(DemoPlayerService);
  });

  afterEach(() => {
    service.stop();
    vi.restoreAllMocks();
  });

  it('dovrebbe avviarsi correttamente', () => {
    expect(service.isRunning()).toBe(false);
    expect(service.isPaused()).toBe(false);

    service.start();
    expect(service.isRunning()).toBe(true);
    expect(service.isPaused()).toBe(false);
    expect(service.currentStep()).toBe('select_chip');
  });

  it('dovrebbe mettere in pausa e riprendere continuando lo step corrente', () => {
    service.start();
    expect(service.isRunning()).toBe(true);
    expect(service.isPaused()).toBe(false);

    // Avanza di 400ms per entrare nel typing
    vi.advanceTimersByTime(450);
    expect(service.currentStep()).toBe('typing');

    // Metti in pausa durante il typing
    service.pause();
    expect(service.isPaused()).toBe(true);
    const payloadAtPause = service.simulatedPayload();

    // Con il timer che avanza mentre è in pausa, il payload NON deve cambiare
    vi.advanceTimersByTime(200);
    expect(service.simulatedPayload()).toBe(payloadAtPause);
    expect(service.isPaused()).toBe(true);

    // Chiama resume: la demo deve continuare e completare la digitazione
    service.resume();
    expect(service.isPaused()).toBe(false);
    expect(service.isRunning()).toBe(true);

    // Fai avanzare il timer: la digitazione deve proseguire e aumentare
    vi.advanceTimersByTime(600);
    expect(service.simulatedPayload().length).toBeGreaterThan(payloadAtPause.length);
  });

  it('dovrebbe permettere pause e resume ripetuti senza duplicare timer', () => {
    service.start();
    vi.advanceTimersByTime(450);

    service.pause();
    expect(service.isPaused()).toBe(true);

    service.resume();
    expect(service.isPaused()).toBe(false);

    service.pause();
    expect(service.isPaused()).toBe(true);

    service.resume();
    expect(service.isPaused()).toBe(false);

    service.stop();
    expect(service.isRunning()).toBe(false);
    expect(service.isPaused()).toBe(false);
  });
});
