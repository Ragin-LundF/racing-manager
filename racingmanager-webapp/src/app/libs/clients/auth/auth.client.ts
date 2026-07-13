import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  LoginRequest,
  LoginResponse,
  RefreshRequest,
  RefreshResponse,
  RegisterRequest,
  RegisterResponse,
  SessionResponse,
  SetupRequest,
  SetupResponse,
  SetupStatusResponse,
} from './auth.models';

/** Raw HTTP for the auth API. Token/session state lives in AuthService (core). */
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

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.baseUrl}/api/v1/register`, request);
  }

  refresh(request: RefreshRequest): Observable<RefreshResponse> {
    return this.http.post<RefreshResponse>(`${this.baseUrl}/api/v1/auth/refresh`, request);
  }

  logout(refreshToken: string | null): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/auth/logout`, refreshToken ? { refreshToken } : {});
  }

  getSession(): Observable<SessionResponse> {
    return this.http.get<SessionResponse>(`${this.baseUrl}/api/v1/auth/session`);
  }
}
