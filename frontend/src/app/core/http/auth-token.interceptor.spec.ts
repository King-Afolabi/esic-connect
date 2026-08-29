import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../auth/auth.service';
import { authTokenInterceptor } from './auth-token.interceptor';

describe('authTokenInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  const auth = { accessToken: null as string | null };

  beforeEach(() => {
    auth.accessToken = null;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('adds a bearer header to API calls when a token is held', () => {
    auth.accessToken = 'token-abc';
    http.get('/api/v1/students').subscribe();

    const req = controller.expectOne('/api/v1/students');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-abc');
    req.flush({});
  });

  it('leaves the request untouched when there is no session', () => {
    http.get('/api/v1/students').subscribe();

    const req = controller.expectOne('/api/v1/students');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('does not attach the token to non-API URLs', () => {
    auth.accessToken = 'token-abc';
    http.get('https://third-party.example/data').subscribe();

    const req = controller.expectOne('https://third-party.example/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
