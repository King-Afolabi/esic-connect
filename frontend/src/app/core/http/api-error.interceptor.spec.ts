import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../auth/auth.service';
import { NotificationService } from '../notifications/notification.service';
import { apiErrorInterceptor } from './api-error.interceptor';

describe('apiErrorInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  const auth = { handleUnauthorized: vi.fn() };
  const notifications = { error: vi.fn(), info: vi.fn() };

  beforeEach(() => {
    auth.handleUnauthorized.mockReset();
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

  it('signals an expired session on a 401 (non-login) response and rethrows', () => {
    const onError = vi.fn();
    http.get('/api/v1/students').subscribe({ error: onError });
    controller.expectOne('/api/v1/students').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(auth.handleUnauthorized).toHaveBeenCalledOnce();
    expect(onError).toHaveBeenCalledOnce();
  });

  it('does not treat a failed login as an expired session', () => {
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

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
});
