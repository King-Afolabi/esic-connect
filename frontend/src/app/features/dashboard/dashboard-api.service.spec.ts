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

  it('GETs /api/v1/me/dashboard with no parameters', () => {
    service.getDashboard().subscribe();
    const req = http.expectOne('/api/v1/me/dashboard');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush({ role: 'STUDENT', generatedAt: '', student: null, teacher: null, manager: null, administration: null, notes: [] });
  });
});
