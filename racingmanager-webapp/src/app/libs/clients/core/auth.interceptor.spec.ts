import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { API_BASE_URL, AUTH_STORAGE_KEY } from './api.config';
import { authErrorInterceptor, bearerInterceptor } from './auth.interceptor';

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

describe('bearerInterceptor / authErrorInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([bearerInterceptor, authErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: API_BASE_URL, useValue: 'http://localhost:8080' },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpTesting.verify();
    localStorage.removeItem(AUTH_STORAGE_KEY);
  });

  it('attaches the access token as a Bearer header', () => {
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    http.get('http://localhost:8080/api/v1/events').subscribe();
    const req = httpTesting.expectOne('http://localhost:8080/api/v1/events');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-1');
    req.flush([]);
  });

  it('does not overwrite an explicitly-set Authorization header', () => {
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    http.get('http://localhost:8080/api/v1/spectator/snapshot', { headers: { Authorization: 'Bearer spectator-token' } }).subscribe();
    const req = httpTesting.expectOne('http://localhost:8080/api/v1/spectator/snapshot');
    expect(req.request.headers.get('Authorization')).toBe('Bearer spectator-token');
    req.flush({});
  });

  it('on a 401, refreshes once and retries the request with the new token', () => {
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    let result: unknown;
    http.get('http://localhost:8080/api/v1/events').subscribe((res) => (result = res));

    const first = httpTesting.expectOne('http://localhost:8080/api/v1/events');
    first.flush({ code: 'INVALID_TOKEN', message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    httpTesting.expectOne('http://localhost:8080/api/v1/auth/refresh').flush({ accessToken: 'access-2', expiresIn: 900 });

    const retried = httpTesting.expectOne('http://localhost:8080/api/v1/events');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer access-2');
    retried.flush([{ id: 'e1' }]);

    expect(result).toEqual([{ id: 'e1' }]);
  });

  it('redirects to /login when the refresh itself fails', () => {
    auth.login({ username: 'admin', password: 'password123' }).subscribe();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(loginResponse);

    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    http.get('http://localhost:8080/api/v1/events').subscribe({ error: () => undefined });
    httpTesting.expectOne('http://localhost:8080/api/v1/events').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('never retries a 401 from the login endpoint itself', () => {
    let result: unknown;
    auth.login({ username: 'admin', password: 'wrong' }).subscribe((res: unknown) => (result = res));
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/login').flush(
      { code: 'INVALID_CREDENTIALS', message: 'bad' },
      { status: 401, statusText: 'Unauthorized' },
    );

    // No refresh call was ever made — httpTesting.verify() in afterEach would
    // fail if one had been queued — and the login 401 reached AuthService's
    // own catchError as a normal emission rather than being retried.
    expect(result).toEqual({ code: 'INVALID_CREDENTIALS', message: 'bad' });
  });
});
