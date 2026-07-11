import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { AuthClient } from '../libs/clients/auth/auth.client';
import { SESSION_STORAGE_KEY } from '../libs/clients/core/api.config';
import {
  ErrorResponse,
  LoginRequest,
  LoginResponse,
  SessionResponse,
  SetupRequest,
  SetupResponse,
  SetupStatusResponse,
} from '../libs/clients/auth/auth.models';

/** Stateful session store. Delegates all HTTP to AuthClient and holds the
    signed-in session; the session id itself is attached to requests by the
    session interceptor. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authClient = inject(AuthClient);

  readonly session = signal<SessionResponse | null>(null);
  readonly isAuthenticated = signal(false);

  private readonly sessionKey = SESSION_STORAGE_KEY;

  constructor() {
    this.restoreSession();
  }

  private restoreSession(): void {
    const stored = localStorage.getItem(this.sessionKey);
    if (stored) {
      this.authClient
        .getSession()
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
    return this.authClient.getSetupStatus();
  }

  setup(request: SetupRequest): Observable<SetupResponse | ErrorResponse> {
    return this.authClient
      .setup(request)
      .pipe(catchError((err) => of(err.error as ErrorResponse)));
  }

  login(request: LoginRequest): Observable<LoginResponse | ErrorResponse> {
    return this.authClient.login(request).pipe(
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
      catchError((err) => of(err.error as ErrorResponse)),
    );
  }

  logout(): Observable<void> {
    const sessionId = localStorage.getItem(this.sessionKey);
    if (!sessionId) {
      this.clearSession();
      return of(undefined);
    }
    return this.authClient.logout().pipe(
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
