import { InjectionToken } from '@angular/core';

/** Base URL of the racing-manager backend API. app.config.ts overrides this
    with the environment value; the factory default keeps tests injector-clean. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080',
});

/** localStorage key holding the current session id. */
export const SESSION_STORAGE_KEY = 'racingmanager_session';
