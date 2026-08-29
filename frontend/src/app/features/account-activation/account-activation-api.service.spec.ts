import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AccountActivationApiService } from './account-activation-api.service';
import { InvitationValidation } from './account-activation.models';

describe('AccountActivationApiService', () => {
  let service: AccountActivationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AccountActivationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('validates with GET /api/v1/account-invitations/validate, token as a query parameter', () => {
    let received: InvitationValidation | undefined;
    service.validate('raw-token-123').subscribe((r) => (received = r));

    const req = http.expectOne((r) => r.url === '/api/v1/account-invitations/validate');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('token')).toBe('raw-token-123');
    expect(req.request.body).toBeNull();
    expect(req.request.headers.has('Authorization')).toBe(false);

    req.flush({ valid: true });
    expect(received).toEqual({ valid: true });
  });

  it('activates with POST /api/v1/account-invitations/activate and a body of exactly { token, password }', () => {
    let completed = false;
    service
      .activate({ token: 'raw-token-123', password: 'a-long-enough-password' })
      .subscribe({ next: () => (completed = true) });

    const req = http.expectOne('/api/v1/account-invitations/activate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ token: 'raw-token-123', password: 'a-long-enough-password' });
    expect(Object.keys(req.request.body as object).sort()).toEqual(['password', 'token']);
    expect(req.request.headers.has('Authorization')).toBe(false);

    req.flush(null, { status: 204, statusText: 'No Content' });
    expect(completed).toBe(true);
  });
});
