import { Injectable, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export type Locale = 'de' | 'en';

/** Resolves the initial language from localStorage, then the browser, else 'en'. */
export function detectLocale(): Locale {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem('locale') : null;
  if (stored === 'de' || stored === 'en') return stored;
  const browser = typeof navigator !== 'undefined' ? navigator.language?.slice(0, 2) : 'en';
  return browser === 'de' ? 'de' : 'en';
}

@Injectable({ providedIn: 'root' })
export class LocaleService {
  private readonly translate = inject(TranslateService);
  readonly currentLocale = signal<Locale>(detectLocale());

  constructor() {
    this.translate.use(this.currentLocale());
  }

  /** Switches language live — no reload, no navigation — and remembers it. */
  setLocale(locale: Locale): void {
    if (locale === this.currentLocale()) return;
    localStorage.setItem('locale', locale);
    this.currentLocale.set(locale);
    this.translate.use(locale);
  }
}
