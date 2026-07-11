import { Provider } from '@angular/core';
import { provideTranslateService, provideTranslateLoader } from '@ngx-translate/core';
import { StaticTranslateLoader } from '../i18n/static-translate-loader';

/** Provides a fully-loaded English TranslateService for component specs. */
export function provideTestTranslate(): Provider[] {
  return provideTranslateService({
    loader: provideTranslateLoader(StaticTranslateLoader),
    fallbackLang: 'en',
    lang: 'en',
  });
}
