import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StudentsApiService } from './students-api.service';

describe('StudentsApiService', () => {
  let service: StudentsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StudentsApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StudentsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('listStudents', () => {
    it('GETs /api/v1/students and only sends the filters that are set', () => {
      service
        .listStudents({ q: 'Durand', status: 'ACTIVE', sort: 'lastName,asc', page: 2, size: 50 })
        .subscribe();

      const req = http.expectOne((r) => r.url === '/api/v1/students');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('q')).toBe('Durand');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      expect(req.request.params.get('sort')).toBe('lastName,asc');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('omits empty / null filter params entirely', () => {
      service.listStudents({ q: null, status: null, sort: 'createdAt,desc', page: 0, size: 20 }).subscribe();

      const req = http.expectOne((r) => r.url === '/api/v1/students');
      expect(req.request.params.has('q')).toBe(false);
      expect(req.request.params.has('status')).toBe(false);
      expect(req.request.params.get('sort')).toBe('createdAt,desc');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
  });

  it('getStudent GETs /api/v1/students/{userPublicId}', () => {
    service.getStudent('user-abc-123').subscribe();
    const req = http.expectOne('/api/v1/students/user-abc-123');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('listEnrollments GETs /api/v1/enrollments with the student (account) filter and sort', () => {
    service.listEnrollments({ student: 'user-9', sort: 'startDate,desc', size: 100 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/enrollments');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('student')).toBe('user-9');
    expect(req.request.params.get('sort')).toBe('startDate,desc');
    expect(req.request.params.get('size')).toBe('100');
    expect(req.request.params.has('classGroup')).toBe(false);
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('enrollStudent POSTs /api/v1/enrollments with the account (not profile) id', () => {
    service
      .enrollStudent({ studentUserPublicId: 'user-42', classGroupPublicId: 'class-1', startDate: null })
      .subscribe();

    const req = http.expectOne('/api/v1/enrollments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.studentUserPublicId).toBe('user-42');
    req.flush({});
  });

  it('createStudentAccount POSTs /api/v1/users with the optional student number and birth date inline (refonte 2026-09: no separate profile route)', () => {
    service
      .createStudentAccount({
        email: 'jane@example.test',
        firstName: 'Jane',
        lastName: 'Doe',
        role: 'STUDENT',
        studentNumber: 'ESIC-2026-0999',
        birthDate: null,
      })
      .subscribe();

    const req = http.expectOne('/api/v1/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.studentNumber).toBe('ESIC-2026-0999');
    req.flush({ publicId: 'user-42' });
  });
});
