import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AcademicApiService } from './academic-api.service';

describe('AcademicApiService', () => {
  let service: AcademicApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AcademicApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AcademicApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listAcademicYears GETs /api/v1/academic-years and only sends the filters that are set', () => {
    service
      .listAcademicYears({ q: 'BTS', status: 'ACTIVE', sort: 'code,asc', page: 1, size: 50 })
      .subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/academic-years');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('BTS');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('sort')).toBe('code,asc');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('omits empty / null filter params entirely', () => {
    service.listPrograms({ q: null, status: null, sort: 'code,asc', page: 0, size: 20 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/v1/programs');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.get('sort')).toBe('code,asc');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('getAcademicYear GETs /api/v1/academic-years/{publicId}', () => {
    service.getAcademicYear('ay-1').subscribe();
    const req = http.expectOne('/api/v1/academic-years/ay-1');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getProgram GETs /api/v1/programs/{publicId}', () => {
    service.getProgram('pr-9').subscribe();
    http.expectOne('/api/v1/programs/pr-9').flush({});
  });

  it('listProgramLevels GETs the nested /api/v1/programs/{id}/levels route', () => {
    service.listProgramLevels('pr-9', { sort: 'sequenceNumber,asc', size: 100 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/programs/pr-9/levels');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('sort')).toBe('sequenceNumber,asc');
    expect(req.request.params.get('size')).toBe('100');
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('getProgramLevel GETs /api/v1/program-levels/{publicId}', () => {
    service.getProgramLevel('lv-3').subscribe();
    http.expectOne('/api/v1/program-levels/lv-3').flush({});
  });

  it('listPromotions passes the program / academicYear filters only when set', () => {
    service.listPromotions({ academicYear: 'ay-1', sort: 'code,asc', size: 100 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/promotions');
    expect(req.request.params.get('academicYear')).toBe('ay-1');
    expect(req.request.params.has('program')).toBe(false);
    expect(req.request.params.get('size')).toBe('100');
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('getPromotion GETs /api/v1/promotions/{publicId}', () => {
    service.getPromotion('pm-2').subscribe();
    http.expectOne('/api/v1/promotions/pm-2').flush({});
  });

  it('listClassGroups passes the promotion / programLevel filters only when set', () => {
    service.listClassGroups({ programLevel: 'lv-3', sort: 'code,asc', size: 100 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/v1/class-groups');
    expect(req.request.params.get('programLevel')).toBe('lv-3');
    expect(req.request.params.has('promotion')).toBe(false);
    expect(req.request.params.has('site')).toBe(false);
    req.flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
  });

  it('getClassGroup GETs /api/v1/class-groups/{publicId}', () => {
    service.getClassGroup('cg-5').subscribe();
    http.expectOne('/api/v1/class-groups/cg-5').flush({});
  });
});
