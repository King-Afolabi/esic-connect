import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';
import { makeJwt } from './jwt.testing';

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
    expect(req.request.body).toEqual({
      email: 'manager@esic.test',
      password: 'secret',
      captchaToken: null,
    });
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 });

    const outcome = await promise;
    expect(outcome.kind).toBe('session');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken).toBe(token);
    expect(outcome.kind === 'session' && outcome.session.subject).toBe('public-42');
    expect(service.roles()).toEqual(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    expect(service.currentUserEmail()).toBe('manager@esic.test');
  });

  it('rend le défi de second facteur sans ouvrir de session', async () => {
    const promise = firstValueFrom(service.login('admin@esic.test', 'secret'));

    http.expectOne('/api/v1/auth/login').flush({
      mfa: { challengeId: 'defi-1', purpose: 'ENROLL', expiresInSeconds: 300 },
    });

    const outcome = await promise;
    expect(outcome.kind).toBe('challenge');
    // Le mot de passe est bon, mais aucun jeton n'est posé : la connexion
    // n'est pas terminée (RG-007, AC-021).
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken).toBeNull();
  });

  it("transmet l'identifiant d'appareil à la connexion", async () => {
    const promise = firstValueFrom(service.login('user@esic.test', 'pw'));

    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.headers.get('X-Device-Id')).toMatch(/^[0-9a-f]{64}$/);
    request.flush({
      accessToken: makeJwt({ sub: 's', roles: [], exp: futureExp }),
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    });
    await promise;
  });

  it('ouvre la session une fois le second facteur vérifié', async () => {
    const token = makeJwt({ sub: 'public-7', roles: ['ADMIN'], exp: futureExp });
    const promise = firstValueFrom(service.verifyMfa('defi-1', '123456', 'admin@esic.test'));

    const request = http.expectOne('/api/v1/auth/mfa/verify');
    expect(request.request.body).toEqual({ challengeId: 'defi-1', code: '123456' });
    request.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 });

    await promise;
    expect(service.isAuthenticated()).toBe(true);
    expect(service.roles()).toEqual(['ADMIN']);
  });

  it("ouvre la session à la confirmation d'un enrôlement imposé", async () => {
    const token = makeJwt({ sub: 'public-8', roles: ['ADMIN'], exp: futureExp });
    const promise = firstValueFrom(service.confirmMfaEnrollment('123456', 'defi-1', 'a@esic.test'));

    http.expectOne('/api/v1/auth/mfa/enroll/confirm').flush({
      recoveryCodes: ['ABCDE-12345'],
      session: { accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 },
    });

    const result = await promise;
    expect(result.recoveryCodes).toHaveLength(1);
    expect(service.isAuthenticated()).toBe(true);
  });

  it("n'ouvre aucune session quand l'enrôlement vient d'une session déjà établie", async () => {
    const promise = firstValueFrom(service.confirmMfaEnrollment('123456', undefined, ''));

    const request = http.expectOne('/api/v1/auth/mfa/enroll/confirm');
    expect(request.request.body).toEqual({ code: '123456' });
    request.flush({ recoveryCodes: ['ABCDE-12345'] });

    expect((await promise).session).toBeNull();
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

  it('restoreSession rebuilds the session from the refresh cookie then the identity route', async () => {
    const token = makeJwt({ sub: 'public-9', roles: ['STUDENT'], exp: futureExp });
    const promise = firstValueFrom(service.restoreSession());

    const refresh = http.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.method).toBe('POST');
    expect(refresh.request.withCredentials).toBe(true);
    refresh.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 });

    const me = http.expectOne('/api/v1/auth/me');
    expect(me.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    me.flush({ subject: 'public-9', email: 'etudiant@esic.test', roles: ['STUDENT'] });

    await promise;
    expect(service.isAuthenticated()).toBe(true);
    expect(service.currentUserEmail()).toBe('etudiant@esic.test');
    expect(service.roles()).toEqual(['STUDENT']);
  });

  it('restoreSession completes without a session when there is no valid refresh cookie', async () => {
    const promise = firstValueFrom(service.restoreSession());
    http.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(promise).resolves.toBeUndefined();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('refreshSession installs a new token in memory and keeps the current email', async () => {
    await authenticate(service, http, futureExp, ['TEACHER']);
    const newToken = makeJwt({ sub: 's', roles: ['TEACHER'], exp: futureExp });

    const promise = firstValueFrom(service.refreshSession());
    const request = http.expectOne('/api/v1/auth/refresh');
    expect(request.request.withCredentials).toBe(true);
    request.flush({ accessToken: newToken, tokenType: 'Bearer', expiresInSeconds: 900 });

    expect(await promise).toBe(true);
    expect(service.accessToken).toBe(newToken);
    expect(service.currentUserEmail()).toBe('user@esic.test');
  });

  it('refreshSession folds concurrent calls into a single request', async () => {
    const first = firstValueFrom(service.refreshSession());
    const second = firstValueFrom(service.refreshSession());

    http.expectOne('/api/v1/auth/refresh').flush({
      accessToken: makeJwt({ sub: 's', roles: [], exp: futureExp }),
      tokenType: 'Bearer',
      expiresInSeconds: 900,
    });

    expect(await first).toBe(true);
    expect(await second).toBe(true);
  });

  it('refreshSession resolves false and leaves the session untouched on failure', async () => {
    await authenticate(service, http, futureExp);

    const promise = firstValueFrom(service.refreshSession());
    http.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(await promise).toBe(false);
    // La décision de renvoyer vers la connexion revient à l'intercepteur.
    expect(service.isAuthenticated()).toBe(true);
  });

  it('logout tells the server to revoke the token, clears the session and returns to login', async () => {
    await authenticate(service, http, futureExp);

    service.logout();

    // Sans cet appel, « se déconnecter » n'effacerait que l'écran : le
    // jeton resterait utilisable jusqu'à son expiration (EF-AUTH-014).
    const request = http.expectOne('/api/v1/auth/logout');
    expect(request.request.method).toBe('POST');
    request.flush(null);

    expect(service.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout still signs the user out locally when the server call fails', async () => {
    await authenticate(service, http, futureExp);

    service.logout();
    http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('network error'));

    // Refuser de déconnecter parce que le serveur ne répond pas serait
    // le pire des deux mondes : la session locale part quoi qu'il arrive.
    expect(service.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout does nothing (no request, no navigation) when already signed out', () => {
    service.logout();

    http.expectNone('/api/v1/auth/logout');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('requestPasswordReset posts the normalised address and resolves on success', async () => {
    const done = firstValueFrom(service.requestPasswordReset('  Alice@ESIC-Connect.test '));

    const request = http.expectOne('/api/v1/auth/forgot-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'alice@esic-connect.test' });
    request.flush({ message: 'Si un compte existe pour cette adresse…' });

    await expect(done).resolves.toBeUndefined();
  });

  it('resetPassword posts the token and the new password', async () => {
    const done = firstValueFrom(service.resetPassword('un-jeton', 'cheval batterie agrafe'));

    const request = http.expectOne('/api/v1/auth/reset-password');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      token: 'un-jeton',
      newPassword: 'cheval batterie agrafe',
    });
    request.flush(null);

    await expect(done).resolves.toBeNull();
  });

  it('never stores the reset token anywhere on the client', async () => {
    void firstValueFrom(service.resetPassword('jeton-sensible', 'cheval batterie agrafe'));
    http.expectOne('/api/v1/auth/reset-password').flush(null);

    // Même garantie que pour le JWT : rien de sensible ne persiste
    // côté navigateur (RG-093).
    expect(JSON.stringify(localStorage)).not.toContain('jeton-sensible');
    expect(JSON.stringify(sessionStorage)).not.toContain('jeton-sensible');
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
