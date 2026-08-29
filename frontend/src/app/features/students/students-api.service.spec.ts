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

  describe('listProfiles', () => {
    it('GETs /api/v1/student-profiles and only sends the filters that are set', () => {
      service
        .listProfiles({ q: 'ESIC-2026', status: 'ACTIVE', sort: 'studentNumber,asc', page: 2, size: 50 })
        .subscribe();

      const req = http.expectOne((r) => r.url === '/api/v1/student-profiles');
      expect(req.request.method).toBe('GET');
      expect(req.request.params.get('q')).toBe('ESIC-2026');
      expect(req.request.params.get('status')).toBe('ACTIVE');
      expect(req.request.params.get('sort')).toBe('studentNumber,asc');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('50');
      req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
    });

    it('omits empty / null filter params entirely', () => {
      service.listProfiles({ q: null, status: null, sort: 'createdAt,desc', page: 0, size: 20 }).subscribe();

      const req = http.expectOne((r) => r.url === '/api/v1/student-profiles');
      expect(req.request.params.has('q')).toBe(false);
      expect(req.request.params.has('status')).toBe(false);
      expect(req.request.params.get('sort')).toBe('createdAt,desc');
      req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    });
  });

  it('getProfile GETs /api/v1/student-profiles/{publicId}', () => {
    service.getProfile('abc-123').subscribe();
    const req = http.expectOne('/api/v1/student-profiles/abc-123');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('listEnrollments GETs /api/v1/enrollments with the student filter and sort', () => {
    service.listEnrollments({ student: 'profile-9', sort: 'startDate,desc', size: 100 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/enrollments');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('student')).toBe('profile-9');
    expect(req.request.params.get('sort')).toBe('startDate,desc');
    expect(req.request.params.get('size')).toBe('100');
    expect(req.request.params.has('classGroup')).toBe(false);
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('getUserIdentity GETs /api/v1/users/{publicId}', () => {
    service.getUserIdentity('user-7').subscribe();
    const req = http.expectOne('/api/v1/users/user-7');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
