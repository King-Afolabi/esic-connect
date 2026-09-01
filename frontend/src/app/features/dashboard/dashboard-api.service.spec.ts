import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DashboardApiService } from './dashboard-api.service';

describe('DashboardApiService', () => {
  let service: DashboardApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DashboardApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const emptyPayload = {
    role: 'STUDENT',
    generatedAt: '',
    student: null,
    teacher: null,
    manager: null,
    administration: null,
    notes: [],
  };

  it('GETs /api/v1/me/dashboard with no parameters', () => {
    service.getDashboard().subscribe();
    const req = http.expectOne('/api/v1/me/dashboard');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush(emptyPayload);
  });

  it('passes a role context as ?context= when one is given', () => {
    service.getDashboard('TEACHER').subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/me/dashboard');
    expect(req.request.params.get('context')).toBe('TEACHER');
    req.flush(emptyPayload);
  });

  it('omits ?context= for a null / undefined context', () => {
    service.getDashboard(null).subscribe();
    const req = http.expectOne('/api/v1/me/dashboard');
    expect(req.request.params.has('context')).toBe(false);
    req.flush(emptyPayload);
  });
});
