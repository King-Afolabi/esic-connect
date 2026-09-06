import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notifications/notification.service';
import { apiErrorInterceptor } from './api-error.interceptor';

describe('apiErrorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  const auth = {
    handleUnauthorized: vi.fn(),
    refreshSession: vi.fn(),
    accessToken: 'fresh-token',
  };
  const notifications = { error: vi.fn(), info: vi.fn() };

  beforeEach(() => {
    auth.handleUnauthorized.mockReset();
    auth.refreshSession.mockReset();
    auth.refreshSession.mockReturnValue(of(false));
    auth.accessToken = 'fresh-token';
    notifications.error.mockReset();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('tries a silent refresh on a 401; if it fails, signals an expired session and rethrows', () => {
    auth.refreshSession.mockReturnValue(of(false));
    const onError = vi.fn();
    http.get('/api/v1/students').subscribe({ error: onError });
    controller.expectOne('/api/v1/students').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.refreshSession).toHaveBeenCalledOnce();
    expect(auth.handleUnauthorized).toHaveBeenCalledOnce();
    expect(onError).toHaveBeenCalledOnce();
  });

  it('replays the original request with the new token when the silent refresh succeeds', () => {
    auth.refreshSession.mockReturnValue(of(true));
    auth.accessToken = 'brand-new-token';
    const onNext = vi.fn();
    http.get('/api/v1/students').subscribe({ next: onNext });

    controller.expectOne('/api/v1/students').flush(null, { status: 401, statusText: 'Unauthorized' });

    const retried = controller.expectOne('/api/v1/students');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer brand-new-token');
    retried.flush({ ok: true });

    expect(onNext).toHaveBeenCalledOnce();
    expect(auth.handleUnauthorized).not.toHaveBeenCalled();
  });

  it('signals an expired session if the replayed request still returns 401', () => {
    auth.refreshSession.mockReturnValue(of(true));
    const onError = vi.fn();
    http.get('/api/v1/students').subscribe({ error: onError });

    controller.expectOne('/api/v1/students').flush(null, { status: 401, statusText: 'Unauthorized' });
    controller.expectOne('/api/v1/students').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.handleUnauthorized).toHaveBeenCalledOnce();
    expect(onError).toHaveBeenCalledOnce();
  });

  it('does not attempt a refresh on a 401 from an auth route', () => {
    http.post('/api/v1/auth/refresh', {}).subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.refreshSession).not.toHaveBeenCalled();
  });

  it('does not treat a failed login as an expired session', () => {
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.refreshSession).not.toHaveBeenCalled();
    expect(auth.handleUnauthorized).not.toHaveBeenCalled();
  });

  it('shows a generic notification for a 5xx response', () => {
    http.get('/api/v1/students').subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/students').flush(
      { status: 500, code: 'INTERNAL_ERROR', message: 'stack trace', path: '', correlationId: null, details: [] },
      { status: 500, statusText: 'Server Error' },
    );

    expect(notifications.error).toHaveBeenCalledOnce();
    expect(notifications.error.mock.calls[0][0]).not.toContain('stack trace');
  });

  it('stays silent for a 4xx business error (handled by the caller)', () => {
    http.get('/api/v1/students').subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/students').flush(
      { status: 422, code: 'ENR_USER_NOT_ELIGIBLE', message: 'x', path: '', correlationId: null, details: [] },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(notifications.error).not.toHaveBeenCalled();
    expect(auth.handleUnauthorized).not.toHaveBeenCalled();
  });

  it('never clears the session on a 401 from the public activation endpoint', () => {
    const onError = vi.fn();
    http.post('/api/v1/account-invitations/activate', {}).subscribe({ error: onError });
    controller
      .expectOne('/api/v1/account-invitations/activate')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.refreshSession).not.toHaveBeenCalled();
    expect(auth.handleUnauthorized).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledOnce();
  });

  it('does not raise the global snackbar for a 5xx from the public invitation endpoints', () => {
    http
      .get('/api/v1/account-invitations/validate', { params: { token: 'x' } })
      .subscribe({ error: () => undefined });
    controller
      .expectOne((r) => r.url === '/api/v1/account-invitations/validate')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('still rethrows public activation failures so the component can map them', () => {
    const onError = vi.fn();
    http.post('/api/v1/account-invitations/activate', {}).subscribe({ error: onError });
    controller.expectOne('/api/v1/account-invitations/activate').flush(
      {
        status: 400,
        code: 'INVITATION_INVALID',
        message: "Lien d'activation invalide ou expire.",
        path: '',
        correlationId: null,
        details: [],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(onError).toHaveBeenCalledOnce();
  });
});
