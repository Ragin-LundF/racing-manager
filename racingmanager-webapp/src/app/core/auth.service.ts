import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap } from 'rxjs';
import { AuthClient } from '../libs/clients/auth/auth.client';
import { AUTH_STORAGE_KEY } from '../libs/clients/core/api.config';
import {
  ErrorResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  SetupRequest,
  SetupResponse,
  SetupStatusResponse,
} from '../libs/clients/auth/auth.models';

/** Everything needed to attach a request and to silently refresh, persisted
    as one JSON blob so a page reload restores a signed-in session. */
interface StoredAuth {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  tenantId: string;
  scopes: string[];
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface SessionInfo {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  tenantId: string;
  scopes: string[];
}

/** Refresh a little before the access token actually expires so an in-flight
    request never races the exact expiry instant. */
const REFRESH_SKEW_MS = 30_000;

/** Token-based session store (design §B/§G). Holds the access+refresh token
    pair and schedules silent refresh; the bearer interceptor reads
    `getAccessToken()` and the auth-error interceptor calls `refreshNow()` on
    a 401. Tokens are never logged. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly authClient = inject(AuthClient);

  readonly session = signal<SessionInfo | null>(null);
  readonly isAuthenticated = signal(false);

  private auth: StoredAuth | null = null;
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;
  private refreshInFlight: Observable<boolean> | null = null;

  constructor() {
    this.restore();
  }

  private restore(): void {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return;
    try {
      const stored = JSON.parse(raw) as StoredAuth;
      this.applyAuth(stored);
      if (Date.now() >= stored.expiresAt - REFRESH_SKEW_MS) {
        this.refreshNow().subscribe();
      } else {
        this.scheduleRefresh(stored.expiresAt);
      }
    } catch {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  }

  getAccessToken(): string | null {
    return this.auth?.accessToken ?? null;
  }

  hasScope(scope: string): boolean {
    const scopes = this.auth?.scopes ?? [];
    return scopes.includes(scope) || (scope === 'rm:user' && scopes.includes('rm:admin'));
  }

  getSetupStatus(): Observable<SetupStatusResponse> {
    return this.authClient.getSetupStatus();
  }

  setup(request: SetupRequest): Observable<SetupResponse | ErrorResponse> {
    return this.authClient.setup(request).pipe(catchError((err) => of(err.error as ErrorResponse)));
  }

  login(request: LoginRequest): Observable<LoginResponse | ErrorResponse> {
    return this.authClient.login(request).pipe(
      tap((res) => {
        if ('accessToken' in res) {
          this.applyAuth({
            accessToken: res.accessToken,
            refreshToken: res.refreshToken,
            expiresAt: Date.now() + res.expiresIn * 1000,
            tenantId: res.tenantId,
            scopes: res.scopes,
            userId: res.userId,
            username: res.username,
            displayName: res.displayName,
            role: res.role,
          });
          this.persist();
          this.scheduleRefresh(this.auth!.expiresAt);
        }
      }),
      catchError((err) => of(err.error as ErrorResponse)),
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse | ErrorResponse> {
    return this.authClient.register(request).pipe(
      tap((res) => {
        if ('accessToken' in res) {
          this.applyAuth({
            accessToken: res.accessToken,
            refreshToken: res.refreshToken,
            expiresAt: Date.now() + res.expiresIn * 1000,
            tenantId: res.tenantId,
            scopes: res.scopes,
            userId: res.userId,
            username: res.username,
            displayName: res.displayName,
            role: res.role,
          });
          this.persist();
          this.scheduleRefresh(this.auth!.expiresAt);
        }
      }),
      catchError((err) => of(err.error as ErrorResponse)),
    );
  }

  /** Attempts a single silent refresh; deduplicates concurrent callers (e.g.
      several requests failing with 401 at once) into one HTTP call. Clears
      the session on failure — the caller (auth-error interceptor, or
      `restore()`) is responsible for redirecting to `/login`. */
  refreshNow(): Observable<boolean> {
    if (this.refreshInFlight) return this.refreshInFlight;
    const current = this.auth;
    if (!current) return of(false);

    this.refreshInFlight = this.authClient.refresh({ refreshToken: current.refreshToken }).pipe(
      map((res) => {
        this.applyAuth({ ...current, accessToken: res.accessToken, expiresAt: Date.now() + res.expiresIn * 1000 });
        this.persist();
        this.scheduleRefresh(this.auth!.expiresAt);
        return true;
      }),
      catchError(() => {
        this.clearAuth();
        return of(false);
      }),
      finalize(() => {
        this.refreshInFlight = null;
      }),
      shareReplay(1),
    );
    return this.refreshInFlight;
  }

  logout(): Observable<void> {
    const refreshToken = this.auth?.refreshToken ?? null;
    if (!refreshToken) {
      this.clearAuth();
      return of(undefined);
    }
    return this.authClient.logout(refreshToken).pipe(
      tap(() => this.clearAuth()),
      catchError(() => {
        this.clearAuth();
        return of(undefined);
      }),
    );
  }

  clearAuth(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    localStorage.removeItem(AUTH_STORAGE_KEY);
    this.auth = null;
    this.session.set(null);
    this.isAuthenticated.set(false);
  }

  private applyAuth(stored: StoredAuth): void {
    this.auth = stored;
    this.session.set({
      userId: stored.userId,
      username: stored.username,
      displayName: stored.displayName,
      role: stored.role,
      tenantId: stored.tenantId,
      scopes: stored.scopes,
    });
    this.isAuthenticated.set(true);
  }

  private persist(): void {
    if (this.auth) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(this.auth));
    }
  }

  private scheduleRefresh(expiresAt: number): void {
    if (this.refreshTimer) clearTimeout(this.refreshTimer);
    const delay = Math.max(0, expiresAt - REFRESH_SKEW_MS - Date.now());
    this.refreshTimer = setTimeout(() => this.refreshNow().subscribe(), delay);
  }
}
