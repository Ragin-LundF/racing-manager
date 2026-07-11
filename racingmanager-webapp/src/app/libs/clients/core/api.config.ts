import { InjectionToken } from '@angular/core';

/** Base URL of the racing-manager backend API. Provided in app.config.ts. */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL');

/** localStorage key holding the current session id. */
export const SESSION_STORAGE_KEY = 'racingmanager_session';
