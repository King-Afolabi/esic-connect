import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PlanningApiService } from './planning-api.service';

const IMPORTS = '/api/v1/planning-imports';
const VERSIONS = '/api/v1/planning/versions';

describe('PlanningApiService', () => {
  let service: PlanningApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [PlanningApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlanningApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('simulate POSTs a multipart body with the file and the class id', () => {
    const file = new File(['slot_key\nS1'], 'planning.csv', { type: 'text/csv' });
    service.simulate(file, 'c-1').subscribe();
    const req = http.expectOne(IMPORTS);
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body.get('classGroupPublicId')).toBe('c-1');
    expect((body.get('file') as File).name).toBe('planning.csv');
    req.flush({}, { status: 201, statusText: 'Created' });
  });

  it('getJob GETs /planning-imports/{id}', () => {
    service.getJob('j-1').subscribe();
    const req = http.expectOne(`${IMPORTS}/j-1`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('listRows GETs /rows with the sort and pagination only', () => {
    service.listRows('j-1', { sort: 'rowNumber,asc', page: 2, size: 50 }).subscribe();
    const req = http.expectOne((r) => r.url === `${IMPORTS}/j-1/rows`);
    expect(req.request.params.keys().sort()).toEqual(['page', 'size', 'sort']);
    expect(req.request.params.get('sort')).toBe('rowNumber,asc');
    req.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('publish POSTs to /publish', () => {
    service.publish('j-1').subscribe();
    const req = http.expectOne(`${IMPORTS}/j-1/publish`);
    expect(req.request.method).toBe('POST');
    req.flush({ jobPublicId: 'j-1', versionPublicId: 'v-1', versionNumber: 1, alreadyPublished: false });
  });

  it('cancel POSTs to /cancel and expects 204', () => {
    service.cancel('j-1').subscribe();
    const req = http.expectOne(`${IMPORTS}/j-1/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('listVersions GETs /planning/versions with the class filter', () => {
    service.listVersions('c-1', { sort: 'versionNumber,desc', size: 50 }).subscribe();
    const req = http.expectOne((r) => r.url === VERSIONS);
    expect(req.request.params.get('classGroupPublicId')).toBe('c-1');
    expect(req.request.params.get('sort')).toBe('versionNumber,desc');
    req.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('getVersion GETs /planning/versions/{id}', () => {
    service.getVersion('v-1').subscribe();
    const req = http.expectOne(`${VERSIONS}/v-1`);
    expect(req.request.method).toBe('GET');
    req.flush({ version: {}, entries: [] });
  });

  it('never sends a client scope / manager parameter', () => {
    service.listRows('j-1', { page: 0, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === `${IMPORTS}/j-1/rows`);
    expect(req.request.params.keys().sort()).toEqual(['page', 'size']);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
