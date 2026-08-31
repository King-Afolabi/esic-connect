import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StudentImportApiService } from './student-import-api.service';

const BASE = '/api/v1/student-imports';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), StudentImportApiService],
  });
  return {
    api: TestBed.inject(StudentImportApiService),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('StudentImportApiService', () => {
  it('POSTs a multipart FormData for a simulation, with optional scope parts', () => {
    const { api, http } = setup();
    const file = new File(['last_name\nDoe\n'], 'apprenants.csv', { type: 'text/csv' });
    api.simulate(file, ' PRG ', '').subscribe();

    const req: TestRequest = http.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect(body instanceof FormData).toBe(true);
    expect((body.get('file') as File).name).toBe('apprenants.csv');
    expect(body.get('programCode')).toBe('PRG');
    expect(body.has('classCode')).toBe(false);
    req.flush({});
    http.verify();
  });

  it('GETs the job list with only the provided params', () => {
    const { api, http } = setup();
    api.listJobs({ status: 'SIMULATED', sort: 'createdAt,desc', page: 0, size: 10 }).subscribe();
    const req = http.expectOne(
      (r) => r.url === BASE && r.params.get('status') === 'SIMULATED' && r.params.get('sort') === 'createdAt,desc',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });

    api.listJobs({}).subscribe();
    const bare = http.expectOne(BASE);
    expect(bare.request.params.keys()).toEqual([]);
    bare.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    http.verify();
  });

  it('GETs a job and its rows with encoded id and conditional filters', () => {
    const { api, http } = setup();
    api.getJob('a b').subscribe();
    http.expectOne(`${BASE}/a%20b`).flush({});

    api.listRows('j1', { rowStatus: 'ERROR', severity: null, action: 'NONE', sort: 'rowNumber,asc' }).subscribe();
    const req = http.expectOne(
      (r) =>
        r.url === `${BASE}/j1/rows` &&
        r.params.get('rowStatus') === 'ERROR' &&
        r.params.get('action') === 'NONE' &&
        !r.params.has('severity'),
    );
    req.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
    http.verify();
  });

  it('POSTs confirm (empty body) and cancel', () => {
    const { api, http } = setup();
    api.confirm('j1').subscribe();
    const confirm = http.expectOne(`${BASE}/j1/confirm`);
    expect(confirm.request.method).toBe('POST');
    expect(confirm.request.body).toEqual({});
    confirm.flush({
      jobPublicId: 'j1',
      alreadyApplied: false,
      created: 1,
      updated: 0,
      transferred: 0,
      invited: 1,
      ignored: 0,
    });

    api.cancel('j1').subscribe();
    const cancel = http.expectOne(`${BASE}/j1/cancel`);
    expect(cancel.request.method).toBe('POST');
    cancel.flush(null, { status: 204, statusText: 'No Content' });
    http.verify();
  });
});
