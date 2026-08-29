import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';
import { makeJwt } from './jwt.spec';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  const router = { navigate: vi.fn(), navigateByUrl: vi.fn() };

  const futureExp = Math.floor(Date.now() / 1000) + 900;

  beforeEach(() => {
    router.navigate.mockReset();
    router.navigateByUrl.mockReset();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts unauthenticated', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken).toBeNull();
  });

  it('stores an in-memory session on successful login, with roles read from the JWT', async () => {
    const token = makeJwt({ sub: 'public-42', roles: ['PEDAGOGICAL_MANAGER', 'TEACHER'], exp: futureExp });
    const promise = firstValueFrom(service.login('  Manager@Esic.TEST ', 'secret'));

    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'manager@esic.test', password: 'secret' });
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 });

    const session = await promise;
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken).toBe(token);
    expect(session.subject).toBe('public-42');
    expect(service.roles()).toEqual(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    expect(service.currentUserEmail()).toBe('manager@esic.test');
  });

  it('does not establish a session when login fails', async () => {
    const promise = firstValueFrom(service.login('bad@esic.test', 'wrong'));
    http.expectOne('/api/v1/auth/login').flush(
      { status: 401, code: 'AUTH_INVALID_CREDENTIALS', message: 'x', path: '', correlationId: null, details: [] },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(promise).rejects.toBeDefined();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken).toBeNull();
  });

  it('restoreSession completes without establishing a session (no client persistence)', async () => {
    await expect(firstValueFrom(service.restoreSession())).resolves.toBeUndefined();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logout clears the session and returns to the login screen', async () => {
    await authenticate(service, http, futureExp);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout does nothing (no navigation) when already signed out', () => {
    service.logout();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('handleUnauthorized clears the session and redirects with reason=expired', async () => {
    await authenticate(service, http, futureExp);

    service.handleUnauthorized();

    expect(service.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });

  it('hasAnyRole reflects the roles carried by the session', async () => {
    expect(service.hasAnyRole(['ADMIN'])).toBe(false);
    await authenticate(service, http, futureExp, ['SCHOOL_ADMINISTRATION']);

    expect(service.hasAnyRole(['ADMIN', 'SCHOOL_ADMINISTRATION'])).toBe(true);
    expect(service.hasAnyRole(['ADMIN'])).toBe(false);
    expect(service.hasAnyRole([])).toBe(true);
  });
});

async function authenticate(
  service: AuthService,
  http: HttpTestingController,
  exp: number,
  roles: string[] = ['ADMIN'],
): Promise<void> {
  const promise = firstValueFrom(service.login('user@esic.test', 'pw'));
  http
    .expectOne('/api/v1/auth/login')
    .flush({ accessToken: makeJwt({ sub: 's', roles, exp }), tokenType: 'Bearer', expiresInSeconds: 900 });
  await promise;
}
