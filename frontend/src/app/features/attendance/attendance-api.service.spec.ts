import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AttendanceApiService, triggerCsvDownload } from './attendance-api.service';

const BASE = '/api/v1';

describe('AttendanceApiService', () => {
  let service: AttendanceApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [AttendanceApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AttendanceApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listMyAttendance GETs /me/attendance and only sends the filters that are set', () => {
    service.listMyAttendance({ from: '2026-09-01T00:00:00Z', status: 'ABSENT', page: 2, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/me/attendance`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-09-01T00:00:00Z');
    expect(req.request.params.get('status')).toBe('ABSENT');
    expect(req.request.params.has('to')).toBe(false);
    req.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('submitJustification POSTs the body with no student identifier of its own', () => {
    service
      .submitJustification({ checkpointPublicId: 'cp-1', category: 'MEDICAL', comment: 'certificat' })
      .subscribe();
    const req = http.expectOne(`${BASE}/me/attendance/justifications`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ checkpointPublicId: 'cp-1', category: 'MEDICAL', comment: 'certificat' });
    expect(JSON.stringify(req.request.body)).not.toContain('studentPublicId');
    req.flush({});
  });

  it('amendJustification PUTs to /me/attendance/justifications/{id}', () => {
    service.amendJustification('j-1', { category: 'TRANSPORT', comment: 'grève' }).subscribe();
    const req = http.expectOne(`${BASE}/me/attendance/justifications/j-1`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('reviewJustification POSTs the decision to the review route', () => {
    service.reviewJustification('j-1', { decision: 'REJECTED', decisionReason: 'illisible' }).subscribe();
    const req = http.expectOne(`${BASE}/attendance/justifications/j-1/review`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'REJECTED', decisionReason: 'illisible' });
    req.flush({});
  });

  it('summary / reports GET the right routes and forward the filters', () => {
    service.summary({ from: 'a', classGroup: 'c-1' }).subscribe();
    const s = http.expectOne((r) => r.url === `${BASE}/attendance/reports/summary`);
    expect(s.request.params.get('classGroup')).toBe('c-1');
    s.flush({});

    service.studentsReport({ classGroup: 'c-1', studentProfile: 'p-1', page: 0, size: 20 }).subscribe();
    const st = http.expectOne((r) => r.url === `${BASE}/attendance/reports/students`);
    expect(st.request.params.get('studentProfile')).toBe('p-1');
    st.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('exportReport requests a blob with observe:response and never puts filters in a navigation URL', () => {
    service.exportReport('sessions', { from: 'a', classGroup: 'c-1' }).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/attendance/reports/sessions/export`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('classGroup')).toBe('c-1');
    req.flush(new Blob(['a;b'], { type: 'text/csv' }));
  });

  it('triggerCsvDownload uses the Content-Disposition filename and revokes the object URL', () => {
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clickSpy = vi.fn();
    const anchor = { href: '', download: '', click: clickSpy, remove: vi.fn() } as unknown as HTMLAnchorElement;
    vi.spyOn(document, 'createElement').mockReturnValue(anchor);
    vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n as never);

    const response = new HttpResponse<Blob>({
      body: new Blob(['x'], { type: 'text/csv' }),
      headers: new HttpHeaders({
        'content-disposition': 'attachment; filename="assiduite_sessions.csv"',
      }),
    });
    triggerCsvDownload(response, 'fallback.csv');

    expect(anchor.download).toBe('assiduite_sessions.csv');
    expect(clickSpy).toHaveBeenCalledOnce();
    expect(createSpy).toHaveBeenCalledOnce();
    expect(revokeSpy).toHaveBeenCalledWith('blob:x');
    vi.restoreAllMocks();
  });

  it('never writes to browser storage', () => {
    service.listMyAttendance({}).subscribe();
    http.expectOne(`${BASE}/me/attendance`).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
