import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { AUTH_STORAGE_KEY, API_BASE_URL } from '../libs/clients/core/api.config';

const loginResponse = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresIn: 900,
  tenantId: 'tenant-1',
  scopes: ['rm:admin'],
  userId: 'user-1',
  username: 'admin',
  displayName: 'Admin',
  role: 'ADMIN',
};

describe('AuthService', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.removeItem(AUTH_STORAGE_KEY);
  });

  function setup(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: 'http://localhost:8080' }],
    });
    httpTesting = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  it('starts unauthenticated with nothing in storage', () => {
    const auth = setup();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.getAccessToken()).toBeNull();
  });

  it('login stores tokens, sets the session signal, and persists to storage', () => {
    const auth = setup();
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.getAccessToken()).toBe('access-1');
    expect(auth.session()?.displayName).toBe('Admin');
    expect(auth.session()?.tenantId).toBe('tenant-1');
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY)!).refreshToken).toBe('refresh-1');
  });

  it('hasScope treats rm:admin as satisfying rm:user', () => {
    const auth = setup();
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    expect(auth.hasScope('rm:admin')).toBe(true);
    expect(auth.hasScope('rm:user')).toBe(true);
    expect(auth.hasScope('rm:supervisor')).toBe(false);
  });

  it('restores a session from storage on construction', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: 'access-1',
        refreshToken: 'refresh-1',
        expiresAt: Date.now() + 10 * 60 * 1000,
        tenantId: 'tenant-1',
        scopes: ['rm:admin'],
        userId: 'user-1',
        username: 'admin',
        displayName: 'Admin',
        role: 'ADMIN',
      }),
    );

    const auth = setup();
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.getAccessToken()).toBe('access-1');
  });

  it('refreshes immediately on restore if the stored token is already expired', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: 'stale-token',
        refreshToken: 'refresh-1',
        expiresAt: Date.now() - 1000,
        tenantId: 'tenant-1',
        scopes: ['rm:admin'],
        userId: 'user-1',
        username: 'admin',
        displayName: 'Admin',
        role: 'ADMIN',
      }),
    );

    const auth = setup();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/refresh').flush({ accessToken: 'fresh-token', expiresIn: 900 });
    expect(auth.getAccessToken()).toBe('fresh-token');
  });

  it('clears the session when refresh fails', () => {
    localStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify({
        accessToken: 'stale-token',
        refreshToken: 'refresh-1',
        expiresAt: Date.now() - 1000,
        tenantId: 'tenant-1',
        scopes: ['rm:admin'],
        userId: 'user-1',
        username: 'admin',
        displayName: 'Admin',
        role: 'ADMIN',
      }),
    );

    const auth = setup();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/refresh').flush({ code: 'INVALID_REFRESH_TOKEN', message: 'invalid' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.getAccessToken()).toBeNull();
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });

  it('logout sends the refresh token and clears local state regardless of the response', () => {
    const auth = setup();
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    auth.logout().subscribe();
    const req = httpTesting.expectOne('http://localhost:8080/api/v1/auth/logout');
    expect(req.request.body).toEqual({ refreshToken: 'refresh-1' });
    req.flush(null);

    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(AUTH_STORAGE_KEY)).toBeNull();
  });
});
