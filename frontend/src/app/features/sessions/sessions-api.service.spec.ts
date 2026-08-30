import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SessionsApiService } from './sessions-api.service';

const BASE = '/api/v1';

describe('SessionsApiService', () => {
  let service: SessionsApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SessionsApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('listSessions GETs /sessions and only sends the filters that are set', () => {
    service.listSessions({ status: 'OPEN', sort: 'startsAt,desc', page: 1, size: 50 }).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/sessions`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('sort')).toBe('startsAt,desc');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 1, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('listSessions omits null / empty filter params entirely', () => {
    service.listSessions({ status: null, sort: 'startsAt,desc' }).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/sessions`);
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.keys().sort()).toEqual(['sort']);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('listEligibleTeachers GETs /sessions/teachers', () => {
    service.listEligibleTeachers().subscribe();
    const req = http.expectOne(`${BASE}/sessions/teachers`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getSession GETs /sessions/{publicId} (encoded)', () => {
    service.getSession('a b/c').subscribe();
    const req = http.expectOne(`${BASE}/sessions/a%20b%2Fc`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('createSession POSTs the exact body to /sessions', () => {
    const body = {
      teacherPublicId: 't-1',
      classPublicIds: ['c-1', 'c-2'],
      startsAt: '2026-09-10T06:00:00.000Z',
      endsAt: '2026-09-10T10:00:00.000Z',
      timeZoneId: 'Europe/Paris',
      reason: 'rattrapage',
      title: null,
    };
    service.createSession(body).subscribe();
    const req = http.expectOne(`${BASE}/sessions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({});
  });

  it('openSession / closeSession POST an empty body and accept 204', () => {
    service.openSession('s-1').subscribe();
    const open = http.expectOne(`${BASE}/sessions/s-1/open`);
    expect(open.request.method).toBe('POST');
    expect(open.request.body).toEqual({});
    open.flush(null, { status: 204, statusText: 'No Content' });

    service.closeSession('s-1').subscribe();
    const close = http.expectOne(`${BASE}/sessions/s-1/close`);
    expect(close.request.method).toBe('POST');
    close.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('issueAttendanceToken POSTs to /sessions/{id}/attendance-token (never a GET, never a token in the URL)', () => {
    service.issueAttendanceToken('s-1').subscribe();
    const req = http.expectOne(`${BASE}/sessions/s-1/attendance-token`);
    expect(req.request.method).toBe('POST');
    expect(req.request.urlWithParams).toBe(`${BASE}/sessions/s-1/attendance-token`);
    req.flush({
      token: 'opaque',
      shortCode: 'ABCD2345',
      expiresAt: '2026-09-10T06:00:30Z',
      sessionPublicId: 's-1',
      ttlSeconds: 30,
    });
  });

  it('getSessionAttendance GETs /sessions/{id}/attendance', () => {
    service.getSessionAttendance('s-1').subscribe();
    const req = http.expectOne(`${BASE}/sessions/s-1/attendance`);
    expect(req.request.method).toBe('GET');
    req.flush({ sessionPublicId: 's-1', checkpointPublicId: 'cp', expectedCount: 0, presentCount: 0, records: [] });
  });

  it('validateAttendance POSTs only shortCode to /attendance/validate and never puts it in the URL', () => {
    service.validateAttendance({ shortCode: 'ABCD2345' }).subscribe();
    const req = http.expectOne(`${BASE}/attendance/validate`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ shortCode: 'ABCD2345' });
    expect(req.request.urlWithParams).toBe(`${BASE}/attendance/validate`);
    req.flush({
      attendancePublicId: 'a-1',
      sessionPublicId: 's-1',
      sessionTitle: null,
      recordedAt: '2026-09-10T06:01:00Z',
      source: 'SHORT_CODE',
    });
  });

  // --- V10 : points de contrôle, présence manuelle, correction --------

  it('listCheckpoints / createCheckpoint hit /sessions/{id}/checkpoints with the right method & body', () => {
    service.listCheckpoints('s-1').subscribe();
    const list = http.expectOne(`${BASE}/sessions/s-1/checkpoints`);
    expect(list.request.method).toBe('GET');
    list.flush([]);

    service.createCheckpoint('s-1', { label: 'Fin', type: 'END', required: false }).subscribe();
    const create = http.expectOne(`${BASE}/sessions/s-1/checkpoints`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ label: 'Fin', type: 'END', required: false });
    create.flush({});
  });

  it('open / close / cancel checkpoint POST to the nested route (encoded), cancel carries the reason', () => {
    service.openCheckpoint('s-1', 'cp 1').subscribe();
    http.expectOne(`${BASE}/sessions/s-1/checkpoints/cp%201/open`).flush(null, { status: 204, statusText: 'No Content' });
    service.closeCheckpoint('s-1', 'cp-1').subscribe();
    http.expectOne(`${BASE}/sessions/s-1/checkpoints/cp-1/close`).flush(null, { status: 204, statusText: 'No Content' });
    const cancel = () => {
      service.cancelCheckpoint('s-1', 'cp-1', { reason: 'erreur' }).subscribe();
      const req = http.expectOne(`${BASE}/sessions/s-1/checkpoints/cp-1/cancel`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ reason: 'erreur' });
      req.flush(null, { status: 204, statusText: 'No Content' });
    };
    cancel();
  });

  it('issueCheckpointToken POSTs to the per-checkpoint route with an empty body and no query params', () => {
    service.issueCheckpointToken('s-1', 'cp-1').subscribe();
    const req = http.expectOne(`${BASE}/sessions/s-1/checkpoints/cp-1/attendance-token`);
    expect(req.request.method).toBe('POST');
    expect(req.request.params.keys()).toEqual([]);
    expect(req.request.body).toEqual({});
    req.flush({ token: 'X', shortCode: 'Y', expiresAt: 't', sessionPublicId: 's-1', checkpointPublicId: 'cp-1', ttlSeconds: 30 });
  });

  it('recordManual / correct / cancel / history hit the attendance sub-routes with exact bodies', () => {
    service
      .recordManual('s-1', { enrollmentPublicId: 'e-1', checkpointPublicId: 'cp-1', status: 'ABSENT', comment: 'x' })
      .subscribe();
    const manual = http.expectOne(`${BASE}/sessions/s-1/attendance/manual`);
    expect(manual.request.method).toBe('POST');
    expect(manual.request.body).toEqual({
      enrollmentPublicId: 'e-1',
      checkpointPublicId: 'cp-1',
      status: 'ABSENT',
      comment: 'x',
    });
    manual.flush({});

    service.correctAttendance('s-1', 'a-1', { status: 'PRESENT', reason: 'r' }).subscribe();
    const correct = http.expectOne(`${BASE}/sessions/s-1/attendance/a-1/correct`);
    expect(correct.request.body).toEqual({ status: 'PRESENT', reason: 'r' });
    correct.flush({});

    service.cancelAttendance('s-1', 'a-1', { reason: 'doublon' }).subscribe();
    http.expectOne(`${BASE}/sessions/s-1/attendance/a-1/cancel`).flush({});

    service.attendanceHistory('s-1', 'a-1').subscribe();
    const history = http.expectOne(`${BASE}/sessions/s-1/attendance/a-1/history`);
    expect(history.request.method).toBe('GET');
    history.flush([]);
  });

  it('never exposes a client parameter that could widen a teacher / manager scope', () => {
    service.listSessions({ status: 'OPEN' }).subscribe();
    const req = http.expectOne((r) => r.url === `${BASE}/sessions`);
    expect(req.request.params.keys().sort()).toEqual(['status']);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });
});
