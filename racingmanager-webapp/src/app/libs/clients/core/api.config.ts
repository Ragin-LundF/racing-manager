import { InjectionToken } from '@angular/core';

/** Base URL of the racing-manager backend API. app.config.ts overrides this
    with the environment value; the factory default keeps tests injector-clean. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => 'http://localhost:8080',
});

/** localStorage key holding the current access/refresh token pair (JSON, see
    `StoredAuth` in auth.service.ts). */
export const AUTH_STORAGE_KEY = 'racingmanager_auth';
