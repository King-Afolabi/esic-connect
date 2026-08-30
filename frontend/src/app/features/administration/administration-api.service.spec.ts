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

  describe('lifecycle mutations', () => {
    it('suspendUser POSTs /suspend with the exact { reason } body and completes on 204', () => {
      let completed = false;
      service.suspendUser('abc 123', { reason: 'inactif' }).subscribe({ complete: () => (completed = true) });

      const req = http.expectOne('/api/v1/users/abc%20123/suspend');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'inactif' });
      expect(Object.keys(req.request.body as object)).toEqual(['reason']);
      req.flush(null, { status: 204, statusText: 'No Content' });
      expect(completed).toBe(true);
    });

    it('restoreUser POSTs /restore with the { reason } body', () => {
      service.restoreUser('u-1', { reason: 'retour' }).subscribe();
      const req = http.expectOne('/api/v1/users/u-1/restore');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'retour' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('archiveUser POSTs /archive with the { reason } body', () => {
      service.archiveUser('u-1', { reason: 'départ' }).subscribe();
      const req = http.expectOne('/api/v1/users/u-1/archive');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'départ' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('role mutations', () => {
    it('assignRole POSTs /roles with the exact { role, reason } body', () => {
      service.assignRole('u-1', { role: 'TEACHER', reason: 'affectation' }).subscribe();
      const req = http.expectOne('/api/v1/users/u-1/roles');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ role: 'TEACHER', reason: 'affectation' });
      expect(Object.keys(req.request.body as object).sort()).toEqual(['reason', 'role']);
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('revokeRole POSTs /roles/{roleCode}/revoke, encoding the code, with the { reason } body', () => {
      service.revokeRole('u/1', 'SCHOOL_ADMINISTRATION', { reason: 'fin de mission' }).subscribe();
      const req = http.expectOne('/api/v1/users/u%2F1/roles/SCHOOL_ADMINISTRATION/revoke');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'fin de mission' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('assignRole sends a reason longer than 500 chars verbatim (no truncation)', () => {
      const reason = 'r'.repeat(650);
      service.assignRole('u-1', { role: 'TEACHER', reason }).subscribe();
      const req = http.expectOne('/api/v1/users/u-1/roles');
      expect((req.request.body as { reason: string }).reason).toBe(reason);
      expect((req.request.body as { reason: string }).reason.length).toBe(650);
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('propagates a mutation error to the caller instead of swallowing it', () => {
      let status = 0;
      service.assignRole('u-1', { role: 'STUDENT', reason: 'x' }).subscribe({
        error: (err: { status: number }) => (status = err.status),
      });
      http
        .expectOne('/api/v1/users/u-1/roles')
        .flush(
          { status: 409, code: 'USER_ROLE_ALREADY_ASSIGNED', message: 'x', path: '', correlationId: null, details: [] },
          { status: 409, statusText: 'Conflict' },
        );
      expect(status).toBe(409);
    });
  });

  it('issues no write request on a pure read (list + detail)', () => {
    service.listUsers({}).subscribe();
    http.expectOne((r) => r.url === '/api/v1/users' && r.method === 'GET').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    service.getUser('u-1').subscribe();
    http.expectOne((r) => r.url === '/api/v1/users/u-1' && r.method === 'GET').flush({});
    http.expectNone((r) => r.method === 'PATCH');
    http.expectNone((r) => r.method === 'DELETE');
    http.expectNone((r) => r.method === 'PUT');
  });
});
