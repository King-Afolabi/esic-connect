import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AlternationApiService } from './alternation-api.service';

const BASE = '/api/v1/alternation';

describe('AlternationApiService', () => {
  let service: AlternationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AlternationApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AlternationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('patterns', () => {
    it('GETs /patterns and only sends the filters that are set', () => {
      service
        .listPatterns({ q: 'RY', status: 'ACTIVE', type: 'CUSTOM', sort: 'code,asc', page: 2, size: 50 })
        .subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/patterns`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('q')).toBe('RY');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      expect(req.request.params.get('type')).toBe('CUSTOM');
      expect(req.request.params.get('sort')).toBe('code,asc');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('omits null / empty filter params entirely', () => {
      service.listPatterns({ q: null, status: null, type: null, sort: 'code,asc' }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/patterns`);
      expect(req.request.params.has('q')).toBe(false);
      expect(req.request.params.has('status')).toBe(false);
      expect(req.request.params.has('type')).toBe(false);
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('getPattern GETs /patterns/{publicId}', () => {
      service.getPattern('p-1').subscribe();
      const req = http.expectOne(`${BASE}/patterns/p-1`);
      expect(req.request.method).toBe('GET');
      req.flush({});
    });

    it('createPattern POSTs the body to /patterns', () => {
      const body = { code: 'RY-1', name: 'Rythme', type: 'CUSTOM' as const, configuration: { a: 1 } };
      service.createPattern(body).subscribe();
      const req = http.expectOne(`${BASE}/patterns`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('updatePattern PATCHes /patterns/{publicId}', () => {
      const body = { name: 'X', configuration: {} };
      service.updatePattern('p-9', body).subscribe();
      const req = http.expectOne(`${BASE}/patterns/p-9`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('archivePattern POSTs the reason to /patterns/{publicId}/archive', () => {
      service.archivePattern('p-2', { reason: 'obsolète' }).subscribe();
      const req = http.expectOne(`${BASE}/patterns/p-2/archive`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'obsolète' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('restorePattern POSTs to /patterns/{publicId}/restore', () => {
      service.restorePattern('p-3').subscribe();
      const req = http.expectOne(`${BASE}/patterns/p-3/restore`);
      expect(req.request.method).toBe('POST');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('class assignments', () => {
    it('listAssignmentsByClass GETs the nested route with sort and size only', () => {
      service.listAssignmentsByClass('c-1', { sort: 'validFrom,desc', size: 100 }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/classes/c-1/assignments`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('sort')).toBe('validFrom,desc');
      expect(req.request.params.get('size')).toBe('100');
      req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('listAssignments GETs the flat route with the class filter', () => {
      service.listAssignments({ class: 'c-1', status: 'ACTIVE', sort: 'validFrom,desc' }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/class-assignments`);
      expect(req.request.params.get('class')).toBe('c-1');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });

    it('assignClass POSTs the exact body to /class-assignments', () => {
      const body = {
        classGroupPublicId: 'c-1',
        workStudyPatternPublicId: 'p-1',
        cycleStartDate: '2026-09-07',
        validFrom: '2026-09-07',
        validUntil: null,
      };
      service.assignClass(body).subscribe();
      const req = http.expectOne(`${BASE}/class-assignments`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('closeAssignment POSTs reason + effectiveDate to /class-assignments/{id}/close', () => {
      service.closeAssignment('a-1', { reason: 'fin', effectiveDate: '2026-12-31' }).subscribe();
      const req = http.expectOne(`${BASE}/class-assignments/a-1/close`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'fin', effectiveDate: '2026-12-31' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('getClassContext GETs /classes/{id}/context with the date param', () => {
      service.getClassContext('c-1', '2026-09-08').subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/classes/c-1/context`);
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('date')).toBe('2026-09-08');
      req.flush({});
    });
  });

  describe('student exceptions', () => {
    it('listExceptionsByEnrollment GETs the nested route', () => {
      service.listExceptionsByEnrollment('e-1', { sort: 'startAt,desc', size: 100 }).subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/enrollments/e-1/exceptions`);
      expect(req.request.params.get('sort')).toBe('startAt,desc');
      expect(req.request.params.get('size')).toBe('100');
      req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    });

    it('createException POSTs the exact body to /student-exceptions', () => {
      const body = {
        enrollmentPublicId: 'e-1',
        type: 'COMPANY_PERIOD' as const,
        startAt: '2026-09-07T06:00:00.000Z',
        endAt: '2026-09-11T06:00:00.000Z',
        timeZoneId: 'Europe/Paris',
        reason: 'stage',
      };
      service.createException(body).subscribe();
      const req = http.expectOne(`${BASE}/student-exceptions`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(body);
      req.flush({});
    });

    it('cancelException POSTs the reason to /student-exceptions/{id}/cancel', () => {
      service.cancelException('x-1', { reason: 'erreur' }).subscribe();
      const req = http.expectOne(`${BASE}/student-exceptions/x-1/cancel`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'erreur' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('getEnrollmentContext GETs /enrollments/{id}/context with the date param', () => {
      service.getEnrollmentContext('e-1', '2026-09-08').subscribe();
      const req = http.expectOne((r) => r.url === `${BASE}/enrollments/e-1/context`);
      expect(req.request.params.get('date')).toBe('2026-09-08');
      req.flush({});
    });
  });

  it('never exposes a client parameter that could widen a pedagogical-manager scope', () => {
    // Les listes n'acceptent que class/status/sort/page/size (affectations)
    // ou sort/page/size (exceptions) : aucun paramètre "scope", "program",
    // "manager"… Le périmètre est décidé côté serveur.
    service.listAssignments({ class: 'c-1' }).subscribe();
    const a = http.expectOne((r) => r.url === `${BASE}/class-assignments`);
    expect(a.request.params.keys().sort()).toEqual(['class']);
    a.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    service.listExceptionsByEnrollment('e-1', { page: 0, size: 20 }).subscribe();
    const b = http.expectOne((r) => r.url === `${BASE}/enrollments/e-1/exceptions`);
    expect(b.request.params.keys().sort()).toEqual(['page', 'size']);
    b.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
