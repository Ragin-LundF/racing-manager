import { Component, inject } from '@angular/core';
import { LocaleService } from './locale.service';

@Component({
  selector: 'app-locale-selector',
  standalone: true,
  template: `
    <div class="locale-switch" role="group" aria-label="Language">
      <button
        type="button"
        class="flag"
        [class.active]="localeService.currentLocale() === 'de'"
        (click)="localeService.setLocale('de')"
        title="Deutsch"
        aria-label="Deutsch"
      >
        <span class="flag-emoji">🇩🇪</span><span class="flag-code">DE</span>
      </button>
      <button
        type="button"
        class="flag"
        [class.active]="localeService.currentLocale() === 'en'"
        (click)="localeService.setLocale('en')"
        title="English"
        aria-label="English"
      >
        <span class="flag-emoji">🇬🇧</span><span class="flag-code">EN</span>
      </button>
    </div>
  `,
  styleUrl: './locale-selector.component.scss',
})
export class LocaleSelectorComponent {
  protected readonly localeService = inject(LocaleService);
}
