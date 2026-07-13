import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../../../core/auth.service';

/** Attaches the current access token as `Authorization: Bearer <token>` to
    every outgoing request. Replaces the removed `X-Session-Id` interceptor.
    Skips requests that already carry an explicit `Authorization` header —
    the spectator client sets its own short-lived spectator token this way,
    and it must never be overwritten by the operator's session token. */
export const bearerInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();
  if (token && !req.headers.has('Authorization')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req);
};

/** Requests never eligible for the operator-session refresh-and-retry: the
    auth endpoints themselves (would loop), and the spectator snapshot route,
    whose 401 means the *spectator* token expired — irrelevant to, and not
    fixable by, the operator's own refresh token. */
const isAuthEndpoint = (url: string): boolean =>
  url.includes('/api/v1/auth/refresh') ||
  url.includes('/api/v1/auth/login') ||
  url.includes('/api/v1/auth/setup') ||
  url.includes('/api/v1/spectator/snapshot');

/** On a 401 from an authenticated request, attempts one silent refresh and
    retries the request once with the new token; if the refresh also fails,
    clears the session and redirects to `/login`. Never retries a 401 from
    the auth endpoints themselves (that would loop). */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint(req.url)) {
        return throwError(() => error);
      }
      return authService.refreshNow().pipe(
        switchMap((refreshed) => {
          if (!refreshed) {
            router.navigate(['/login']);
            return throwError(() => error);
          }
          const token = authService.getAccessToken();
          const retried = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
          return next(retried);
        }),
      );
    }),
  );
};
