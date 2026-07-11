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
    if (locale === this.currentLocale()) return;
    localStorage.setItem('locale', locale);
    // Angular i18n is compile-time (one bundle per locale), so switching can't
    // be done in-app — navigate to the locale's URL prefix and hard-reload so
    // the right bundle/translations load.
    const segments = window.location.pathname.split('/');
    if (segments[1] === 'en' || segments[1] === 'de') {
      segments[1] = locale;
    } else {
      segments.splice(1, 0, locale);
    }
    window.location.assign((segments.join('/') || `/${locale}`) + window.location.search);
  }
}
