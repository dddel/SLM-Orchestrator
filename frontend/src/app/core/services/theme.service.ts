import { Injectable, signal } from '@angular/core';

export type Theme = 'dark' | 'light';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly STORAGE_KEY = 'slm_orchestrator_theme';
  readonly currentTheme = signal<Theme>('dark');

  constructor() {
    this.initTheme();
  }

  private initTheme(): void {
    const saved = localStorage.getItem(this.STORAGE_KEY) as Theme | null;
    const initialTheme: Theme = saved === 'light' ? 'light' : 'dark';
    this.setTheme(initialTheme);
  }

  toggleTheme(): void {
    const nextTheme: Theme = this.currentTheme() === 'dark' ? 'light' : 'dark';
    this.setTheme(nextTheme);
  }

  setTheme(theme: Theme): void {
    this.currentTheme.set(theme);
    localStorage.setItem(this.STORAGE_KEY, theme);
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
      root.classList.remove('light');
    } else {
      root.classList.add('light');
      root.classList.remove('dark');
    }
  }
}
