import { Injectable, signal } from '@angular/core';

export type Locale = 'de' | 'en';

@Injectable({ providedIn: 'root' })
export class LocaleService {
  readonly currentLocale = signal<Locale>(this.detectLocale());

  private detectLocale(): Locale {
    const stored = localStorage.getItem('locale');
    if (stored === 'de' || stored === 'en') return stored;
    const browser = navigator.language?.slice(0, 2);
    return browser === 'de' ? 'de' : 'en';
  }

  setLocale(locale: Locale): void {
    localStorage.setItem('locale', locale);
    this.currentLocale.set(locale);
  }
}
