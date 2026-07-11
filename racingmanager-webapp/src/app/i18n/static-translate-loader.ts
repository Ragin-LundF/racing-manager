import { TranslateLoader, TranslationObject } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import en from './en.json';
import de from './de.json';

/** Serves the bundled nested translation JSON — no HTTP, no assets, no flicker. */
export class StaticTranslateLoader extends TranslateLoader {
  getTranslation(lang: string): Observable<TranslationObject> {
    return of((lang === 'de' ? de : en) as TranslationObject);
  }
}
