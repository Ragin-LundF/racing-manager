import { HttpInterceptorFn } from '@angular/common/http';
import { SESSION_STORAGE_KEY } from './api.config';

/** Attaches the stored session id as X-Session-Id to every outgoing request.
    Replaces the per-service header getters that were duplicated across clients. */
export const sessionInterceptor: HttpInterceptorFn = (req, next) => {
  const sessionId = localStorage.getItem(SESSION_STORAGE_KEY);
  if (sessionId) {
    req = req.clone({ setHeaders: { 'X-Session-Id': sessionId } });
  }
  return next(req);
};
