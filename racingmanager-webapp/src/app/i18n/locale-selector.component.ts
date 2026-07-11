import { Component, inject } from '@angular/core';
import { Locale, LocaleService } from './locale.service';

@Component({
  selector: 'app-locale-selector',
  standalone: true,
  template: `
    <select [value]="localeService.currentLocale()" (change)="onChange($event)">
      <option value="de">Deutsch</option>
      <option value="en">English</option>
    </select>
  `,
})
export class LocaleSelectorComponent {
  protected readonly localeService = inject(LocaleService);

  protected onChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as Locale;
    this.localeService.setLocale(value);
  }
}
