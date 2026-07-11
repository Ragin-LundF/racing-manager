import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { LocaleService } from '../i18n/locale.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const locale = inject(LocaleService);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.parseUrl(`/${locale.currentLocale()}/login`);
};

export const redirectIfAuthenticatedGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const locale = inject(LocaleService);

  if (auth.isAuthenticated()) {
    return router.parseUrl(`/${locale.currentLocale()}/racemanager`);
  }

  return true;
};
