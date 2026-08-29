import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AdministrationApiService } from './administration-api.service';

describe('AdministrationApiService', () => {
  let service: AdministrationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdministrationApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdministrationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('listUsers', () => {
    it('GETs /api/v1/users and only sends the filters that are set', () => {
      service
        .listUsers({
          q: 'alice',
          status: 'ACTIVE',
          role: 'TEACHER',
          sort: 'email,asc',
          page: 2,
          size: 50,
        })
        .subscribe();

      const req = http.expectOne((r) => r.url === '/api/v1/users');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('q')).toBe('alice');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      expect(req.request.params.get('role')).toBe('TEACHER');
      expect(req.request.params.get('sort')).toBe('email,asc');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('omits empty / null filter params entirely', () => {
      service
        .listUsers({ q: null, status: null, role: null, sort: 'createdAt,desc', page: 0, size: 20 })
        .subscribe();

      const req = http.expectOne((r) => r.url === '/api/v1/users');
      expect(req.request.params.has('q')).toBe(false);
      expect(req.request.params.has('status')).toBe(false);
      expect(req.request.params.has('role')).toBe(false);
      expect(req.request.params.get('sort')).toBe('createdAt,desc');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
  });

  it('getUser GETs /api/v1/users/{publicId}', () => {
    service.getUser('abc-123').subscribe();
    const req = http.expectOne('/api/v1/users/abc-123');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('never issues a write request (read-only slice)', () => {
    service.listUsers({}).subscribe();
    http.expectOne((r) => r.url === '/api/v1/users' && r.method === 'GET').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    http.expectNone((r) => r.method === 'POST');
    http.expectNone((r) => r.method === 'PATCH');
    http.expectNone((r) => r.method === 'DELETE');
  });
});
