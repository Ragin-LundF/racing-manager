import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideTranslateService, provideTranslateLoader } from '@ngx-translate/core';

import { routes } from './app.routes';
import { API_BASE_URL } from './libs/clients/core/api.config';
import { sessionInterceptor } from './libs/clients/core/session.interceptor';
import { environment } from '../environments/environment';
import { StaticTranslateLoader } from './i18n/static-translate-loader';
import { detectLocale } from './i18n/locale.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([sessionInterceptor])),
    provideRouter(routes),
    { provide: API_BASE_URL, useValue: environment.apiBaseUrl },
    provideTranslateService({
      loader: provideTranslateLoader(StaticTranslateLoader),
      fallbackLang: 'en',
      lang: detectLocale(),
    }),
  ],
};
