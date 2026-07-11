import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import {
  ErrorResponse,
  LoginRequest,
  LoginResponse,
  SessionResponse,
  SetupRequest,
  SetupResponse,
  SetupStatusResponse,
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080';

  readonly session = signal<SessionResponse | null>(null);
  readonly isAuthenticated = signal(false);

  private readonly sessionKey = 'racingmanager_session';

  constructor() {
    this.restoreSession();
  }

  private restoreSession(): void {
    const stored = localStorage.getItem(this.sessionKey);
    if (stored) {
      const sessionId = stored;
      this.http
        .get<SessionResponse>(`${this.baseUrl}/api/v1/auth/session`, {
          headers: { 'X-Session-Id': sessionId },
        })
        .pipe(
          tap((s) => {
            this.session.set(s);
            this.isAuthenticated.set(true);
          }),
          catchError(() => {
            localStorage.removeItem(this.sessionKey);
            return of(null);
          }),
        )
        .subscribe();
    }
  }

  getSetupStatus(): Observable<SetupStatusResponse> {
    return this.http.get<SetupStatusResponse>(
      `${this.baseUrl}/api/v1/auth/setup-status`,
    );
  }

  setup(request: SetupRequest): Observable<SetupResponse | ErrorResponse> {
    return this.http
      .post<SetupResponse>(`${this.baseUrl}/api/v1/auth/setup`, request)
      .pipe(
        catchError((err) =>
          of(err.error as ErrorResponse),
        ),
      );
  }

  login(request: LoginRequest): Observable<LoginResponse | ErrorResponse> {
    return this.http
      .post<LoginResponse>(`${this.baseUrl}/api/v1/auth/login`, request)
      .pipe(
        tap((res) => {
          if ('sessionId' in res) {
            localStorage.setItem(this.sessionKey, res.sessionId);
            this.session.set({
              userId: res.userId,
              username: res.username,
              displayName: res.displayName,
              role: res.role,
            });
            this.isAuthenticated.set(true);
          }
        }),
        catchError((err) =>
          of(err.error as ErrorResponse),
        ),
      );
  }

  logout(): Observable<void> {
    const sessionId = localStorage.getItem(this.sessionKey);
    if (!sessionId) {
      this.clearSession();
      return of(undefined);
    }
    return this.http
      .post<void>(
        `${this.baseUrl}/api/v1/auth/logout`,
        {},
        { headers: { 'X-Session-Id': sessionId } },
      )
      .pipe(
        tap(() => this.clearSession()),
        catchError(() => {
          this.clearSession();
          return of(undefined);
        }),
      );
  }

  private clearSession(): void {
    localStorage.removeItem(this.sessionKey);
    this.session.set(null);
    this.isAuthenticated.set(false);
  }
}
