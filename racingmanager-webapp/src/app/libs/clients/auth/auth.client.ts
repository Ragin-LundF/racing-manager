import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  LoginRequest,
  LoginResponse,
  SessionResponse,
  SetupRequest,
  SetupResponse,
  SetupStatusResponse,
} from './auth.models';

/** Raw HTTP for the auth API. Session state lives in AuthService (core). */
@Injectable({ providedIn: 'root' })
export class AuthClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getSetupStatus(): Observable<SetupStatusResponse> {
    return this.http.get<SetupStatusResponse>(`${this.baseUrl}/api/v1/auth/setup-status`);
  }

  setup(request: SetupRequest): Observable<SetupResponse> {
    return this.http.post<SetupResponse>(`${this.baseUrl}/api/v1/auth/setup`, request);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/api/v1/auth/login`, request);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/logout`, {});
  }

  getSession(): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(`${this.baseUrl}/api/v1/auth/session`);
  }
}
