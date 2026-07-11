import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard, redirectIfAuthenticatedGuard } from './auth.guard';
import { provideHttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../libs/clients/core/api.config';

function mockRouteSnapshot(): ActivatedRouteSnapshot {
  return {} as unknown as ActivatedRouteSnapshot;
}

function mockRouterStateSnapshot(): RouterStateSnapshot {
  return {} as unknown as RouterStateSnapshot;
}

describe('authGuard', () => {
  let auth: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), { provide: API_BASE_URL, useValue: 'http://localhost:8080' }],
    }).compileComponents();

    auth = TestBed.inject(AuthService);
  });

  it('should return true when authenticated', () => {
    auth.isAuthenticated.set(true);
    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRouteSnapshot(), mockRouterStateSnapshot()),
    );
    expect(result).toBe(true);
  });

  it('should redirect to login when not authenticated', () => {
    auth.isAuthenticated.set(false);
    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRouteSnapshot(), mockRouterStateSnapshot()),
    );
    expect(result.toString()).toContain('/login');
  });
});

describe('redirectIfAuthenticatedGuard', () => {
  let auth: AuthService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), { provide: API_BASE_URL, useValue: 'http://localhost:8080' }],
    }).compileComponents();

    auth = TestBed.inject(AuthService);
  });

  it('should redirect to racemanager when authenticated', () => {
    auth.isAuthenticated.set(true);
    const result = TestBed.runInInjectionContext(() =>
      redirectIfAuthenticatedGuard(mockRouteSnapshot(), mockRouterStateSnapshot()),
    );
    expect(result.toString()).toContain('/racemanager');
  });

  it('should return true when not authenticated', () => {
    auth.isAuthenticated.set(false);
    const result = TestBed.runInInjectionContext(() =>
      redirectIfAuthenticatedGuard(mockRouteSnapshot(), mockRouterStateSnapshot()),
    );
    expect(result).toBe(true);
  });
});
