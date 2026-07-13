import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.parseUrl('/login');
};

export const redirectIfAuthenticatedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return router.parseUrl('/racemanager');
  }

  return true;
};

/** Gates `rm:admin`-only pages (audit, diagnostics). Assumes `authGuard`
    already ran on the parent route — an unauthenticated user has no scopes
    and is redirected to `/racemanager` here rather than `/login`, since the
    parent guard is what owns the "not logged in" redirect. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.hasScope('rm:admin')) {
    return true;
  }

  return router.parseUrl('/racemanager');
};

/** Gates the platform supervisor console. A supervisor has no tenant
    membership, so redirecting a non-supervisor here to `/racemanager` (which
    a supervisor can't use either) would be a dead end — send them to
    `/login` instead, same as [authGuard]. */
export const supervisorGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.hasScope('rm:supervisor')) {
    return true;
  }

  return router.parseUrl('/login');
};
