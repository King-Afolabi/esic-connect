import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { CheckpointView, CourseSessionResponse } from '../sessions.models';
import { SessionDetail } from './session-detail';

const GET_URL = '/api/v1/sessions/s-1';
const ATTENDANCE_URL = '/api/v1/sessions/s-1/attendance';
const CANDIDATES_URL = '/api/v1/sessions/s-1/attendance/candidates';
const EXPORT_URL = '/api/v1/sessions/s-1/attendance/export';
const TOKEN_URL = '/api/v1/sessions/s-1/checkpoints/cp-1/attendance-token';

const CANDIDATES = [
  {
    studentProfilePublicId: 'sp-1',
    enrollmentPublicId: 'e-1',
    studentNumber: 'ESIC-2026-001',
    firstName: 'Bob',
    lastName: 'Durand',
    classCode: 'C1',
  },
];

const CP_OPEN: CheckpointView = {
  publicId: 'cp-1',
  label: 'Arrivée',
  type: 'START',
  status: 'OPEN',
  required: true,
  displayOrder: 0,
  openedAt: '2026-09-10T05:55:00Z',
  closedAt: null,
};

const OPEN_SESSION: CourseSessionResponse = {
  publicId: 's-1',
  status: 'OPEN',
  title: 'Rattrapage',
  exceptionReason: 'séance exceptionnelle',
  teacher: { publicId: 't-1', firstName: 'Alice', lastName: 'Martin' },
  classes: [{ publicId: 'c-1', code: 'C1' }],
  startsAt: '2026-09-10T06:00:00Z',
  endsAt: '2026-09-10T10:00:00Z',
  timeZoneId: 'Europe/Paris',
  openedAt: '2026-09-10T05:55:00Z',
  closedAt: null,
  checkpointPublicId: 'cp-1',
  checkpointOpen: true,
  checkpoints: [CP_OPEN],
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-10T05:55:00Z',
};

const EMPTY_ATTENDANCE = {
  sessionPublicId: 's-1',
  checkpointPublicId: 'cp-1',
  expectedCount: 2,
  presentCount: 0,
  records: [],
  checkpoints: [
    {
      checkpointPublicId: 'cp-1',
      label: 'Arrivée',
      type: 'START',
      status: 'OPEN',
      required: true,
      expectedCount: 2,
      presentCount: 0,
      lateCount: 0,
      absentCount: 0,
      excusedCount: 0,
      derivedAbsentCount: 2,
      records: [],
    },
  ],
};

const TOKEN = {
  token: 'OPAQUE-SERVER-TOKEN',
  shortCode: 'ABCD2345',
  expiresAt: '2026-09-10T06:00:30Z',
  sessionPublicId: 's-1',
  checkpointPublicId: 'cp-1',
  ttlSeconds: 30,
};

interface DetailInternals {
  startOpen: () => void;
  confirmOpen: () => void;
  startClose: () => void;
  confirmClose: () => void;
  refreshToken: () => void;
  refreshAttendance: () => void;
  toggleCheckpointForm: () => void;
  submitCheckpoint: () => void;
  checkpointForm: { patchValue: (v: Record<string, unknown>) => void };
  toggleManualForm: () => void;
  submitManual: () => void;
  loadCandidates: () => void;
  candidates: () => unknown[];
  candidatesState: () => string;
  manualForm: {
    patchValue: (v: Record<string, unknown>) => void;
    getRawValue: () => Record<string, unknown>;
  };
  exportAttendanceCsv: () => void;
  startCancelCheckpoint: (cp: CheckpointView) => void;
  startCancelRow: (id: string) => void;
  checkpointCancelForm: { patchValue: (v: Record<string, unknown>) => void; value: unknown };
  attendanceCancelForm: { patchValue: (v: Record<string, unknown>) => void; value: unknown };
  checkpointCancelId: () => string | null;
  attendanceCancelId: () => string | null;
  toggleHistory: (id: string) => void;
  showCheckpointForm: () => boolean;
  showManualForm: () => boolean;
}

function setup(roles: Role[]) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      { provide: RoleContextService, useValue: { effectiveRoles } },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ publicId: 's-1' }) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(SessionDetail);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as DetailInternals;
  fixture.detectChanges();
  return { fixture, http, internals, effectiveRoles };
}

function initialLoad(http: HttpTestingController, session = OPEN_SESSION): void {
  http.expectOne(GET_URL).flush(session);
  http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);
}

describe('SessionDetail', () => {
  let fixture: ComponentFixture<SessionDetail>;
  let http: HttpTestingController;
  let internals: DetailInternals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => {
    vi.useRealTimers();
    http.match(ATTENDANCE_URL).forEach((req) => {
      if (!req.cancelled) {
        req.flush(EMPTY_ATTENDANCE);
      }
    });
    http.verify();
  });

  it('loads the session facts and the per-checkpoint attendance breakdown', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    fixture.detectChanges();
    expect(text()).toContain('Rattrapage');
    expect(text()).toContain('Ouverte');
    expect(text()).toContain('Alice Martin');
    expect(text()).toContain('0 présent(s)');
    expect(text()).toContain('sur 2 attendu(s)');
  });

  it('shows the open button for a PLANNED session and opens it after confirmation', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http, {
      ...OPEN_SESSION,
      status: 'PLANNED',
      openedAt: null,
      checkpointOpen: false,
      checkpoints: [{ ...CP_OPEN, status: 'PLANNED', openedAt: null }],
    });
    fixture.detectChanges();
    expect(text()).toContain('Ouvrir la séance');

    internals.startOpen();
    internals.confirmOpen();
    http.expectOne((r) => r.url.endsWith('/open') && r.method === 'POST').flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    http.expectOne(GET_URL).flush(OPEN_SESSION);
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);
  });

  it('issues a checkpoint token, shows the short code and a <qrcode>, never the token as text', () => {
    ({ fixture, http, internals } = setup(['TEACHER']));
    initialLoad(http);
    fixture.detectChanges();

    internals.refreshToken();
    http.expectOne((r) => r.url === TOKEN_URL && r.method === 'POST').flush(TOKEN);
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('qrcode')).not.toBeNull();
    expect(text()).toContain('ABCD2345');
    expect(text()).not.toContain('OPAQUE-SERVER-TOKEN');
  });

  it('renews the token shortly before expiry and replaces the previous one', () => {
    vi.useFakeTimers();
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    internals.refreshToken();
    http.expectOne(TOKEN_URL).flush(TOKEN);

    vi.advanceTimersByTime(28_000);
    http.expectOne(TOKEN_URL).flush({ ...TOKEN, token: 'SECOND-TOKEN', shortCode: 'EFGH6789' });
    fixture.detectChanges();
    expect(text()).toContain('EFGH6789');
  });

  it('stops the renewal and clears the token when the session is closed', () => {
    vi.useFakeTimers();
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    internals.refreshToken();
    http.expectOne(TOKEN_URL).flush(TOKEN);

    internals.startClose();
    internals.confirmClose();
    http.expectOne((r) => r.url.endsWith('/close')).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(GET_URL).flush({
      ...OPEN_SESSION,
      status: 'CLOSED',
      closedAt: '2026-09-10T07:00:00Z',
      checkpointOpen: false,
      checkpoints: [{ ...CP_OPEN, status: 'CLOSED', closedAt: '2026-09-10T07:00:00Z' }],
    });
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);
    fixture.detectChanges();

    vi.advanceTimersByTime(60_000);
    http.expectNone(TOKEN_URL);
    expect(text()).toContain("l'émargement est fermé");
  });

  it('hides the QR and stops the renewal when the active role context loses the manage right', () => {
    vi.useFakeTimers();
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['ADMIN']));
    initialLoad(http);
    internals.refreshToken();
    http.expectOne(TOKEN_URL).flush(TOKEN);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('qrcode')).not.toBeNull();

    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('qrcode')).toBeNull();

    vi.advanceTimersByTime(60_000);
    http.expectNone(TOKEN_URL);
  });

  it('shows a controlled error and stops rotation when the token backend is unavailable', () => {
    vi.useFakeTimers();
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    internals.refreshToken();
    http.expectOne(TOKEN_URL).flush(
      {
        timestamp: 't',
        status: 503,
        code: 'ATT_TOKEN_BACKEND_UNAVAILABLE',
        message: 'Service momentanément indisponible.',
        path: '/',
        correlationId: null,
        details: [],
      },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();
    expect(text()).toContain('momentanément indisponible');
    vi.advanceTimersByTime(60_000);
    http.expectNone(TOKEN_URL);
  });

  it('refreshes the roster manually and via a modest poll that stops on destroy', () => {
    vi.useFakeTimers();
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);

    internals.refreshAttendance();
    http.expectOne(ATTENDANCE_URL).flush({
      ...EMPTY_ATTENDANCE,
      checkpoints: [
        {
          ...EMPTY_ATTENDANCE.checkpoints[0],
          presentCount: 1,
          derivedAbsentCount: 1,
          records: [
            {
              attendancePublicId: 'a-1',
              studentProfilePublicId: 'sp',
              enrollmentPublicId: 'e',
              studentNumber: 'ESIC-1',
              firstName: 'Bob',
              lastName: 'Durand',
              status: 'PRESENT',
              lateMinutes: null,
              comment: null,
              recordedAt: '2026-09-10T06:01:00Z',
              source: 'SHORT_CODE',
            },
          ],
        },
      ],
    });
    fixture.detectChanges();
    expect(text()).toContain('ESIC-1');
    expect(text()).toContain('Bob Durand');

    vi.advanceTimersByTime(15_000);
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);

    fixture.destroy();
    vi.advanceTimersByTime(30_000);
    http.expectNone(ATTENDANCE_URL);
  });

  it('stops issuing poll requests once the active role context loses the read right', () => {
    vi.useFakeTimers();
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['ADMIN']));
    initialLoad(http);

    vi.advanceTimersByTime(15_000);
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);

    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();

    vi.advanceTimersByTime(60_000);
    http.expectNone(ATTENDANCE_URL);
  });

  it('ignores a token emission response that lands after the manage right was lost', () => {
    vi.useFakeTimers();
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['ADMIN']));
    initialLoad(http);

    internals.refreshToken();
    const req = http.expectOne(TOKEN_URL);

    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();

    req.flush(TOKEN);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('qrcode')).toBeNull();
    vi.advanceTimersByTime(60_000);
    http.expectNone(TOKEN_URL);
  });

  it('creates a checkpoint and reloads the session', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    fixture.detectChanges();

    internals.toggleCheckpointForm();
    internals.checkpointForm.patchValue({ label: 'Retour de pause', type: 'CUSTOM', required: true });
    internals.submitCheckpoint();

    const post = http.expectOne((r) => r.url === '/api/v1/sessions/s-1/checkpoints' && r.method === 'POST');
    expect(post.request.body).toMatchObject({ label: 'Retour de pause', type: 'CUSTOM' });
    post.flush({});
    http.expectOne(GET_URL).flush(OPEN_SESSION);
    expect(internals.showCheckpointForm()).toBe(false);
  });

  it('loads the session candidates and records a manual attendance for the selected enrollment', () => {
    ({ fixture, http, internals } = setup(['TEACHER']));
    initialLoad(http);
    fixture.detectChanges();

    internals.toggleManualForm();
    const candidates = http.expectOne((r) => r.url === CANDIDATES_URL && r.method === 'GET');
    candidates.flush(CANDIDATES);
    fixture.detectChanges();
    expect(internals.candidatesState()).toBe('ready');
    expect(internals.candidates()).toHaveLength(1);
    // Le contrat client ne porte ni e-mail ni identifiant SQL.
    expect(Object.keys(internals.candidates()[0] as object)).not.toContain('email');

    internals.manualForm.patchValue({ enrollmentPublicId: 'e-1', status: 'ABSENT', comment: 'absent constaté' });
    internals.submitManual();

    const post = http.expectOne((r) => r.url === '/api/v1/sessions/s-1/attendance/manual' && r.method === 'POST');
    expect(post.request.body).toMatchObject({
      enrollmentPublicId: 'e-1',
      checkpointPublicId: 'cp-1',
      status: 'ABSENT',
      comment: 'absent constaté',
    });
    post.flush({});
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);
    expect(internals.showManualForm()).toBe(false);
    expect(internals.candidates()).toEqual([]);
  });

  it('surfaces empty / error / forbidden states for the manual candidates list', () => {
    ({ fixture, http, internals } = setup(['TEACHER']));
    initialLoad(http);
    fixture.detectChanges();

    internals.toggleManualForm();
    http.expectOne(CANDIDATES_URL).flush([]);
    fixture.detectChanges();
    expect(internals.candidatesState()).toBe('empty');
    expect(text()).toContain('Aucune inscription active');

    internals.loadCandidates();
    http.expectOne(CANDIDATES_URL).flush(
      { timestamp: 't', status: 403, code: 'ATT_OPERATION_FORBIDDEN', message: 'x', path: '/', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(internals.candidatesState()).toBe('forbidden');

    internals.loadCandidates();
    http.expectOne(CANDIDATES_URL).flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(internals.candidatesState()).toBe('error');
  });

  it('never mixes the checkpoint-cancel and attendance-cancel reason forms', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    fixture.detectChanges();

    internals.startCancelCheckpoint(CP_OPEN);
    internals.checkpointCancelForm.patchValue({ reason: 'point de contrôle en trop' });
    internals.startCancelRow('a-9');

    expect(internals.checkpointCancelId()).toBeNull();
    expect(internals.attendanceCancelId()).toBe('a-9');
    expect((internals.attendanceCancelForm.value as { reason: string }).reason).toBe('');

    internals.attendanceCancelForm.patchValue({ reason: 'doublon' });
    internals.startCancelCheckpoint(CP_OPEN);
    expect((internals.checkpointCancelForm.value as { reason: string }).reason).toBe('');
    expect(internals.attendanceCancelId()).toBeNull();
  });

  it('exports this session attendance as a CSV blob without putting anything in the URL', () => {
    ({ fixture, http, internals } = setup(['SCHOOL_ADMINISTRATION']));
    initialLoad(http);
    fixture.detectChanges();

    const createUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    try {
      internals.exportAttendanceCsv();
      const req = http.expectOne((r) => r.url === EXPORT_URL && r.method === 'GET');
      expect(req.request.responseType).toBe('blob');
      expect(req.request.urlWithParams).toBe(EXPORT_URL);
      req.flush(new Blob(['a;b\r\n'], { type: 'text/csv' }), {
        headers: { 'content-disposition': 'attachment; filename="attendance-session_s-1.csv"' },
      });
      expect(clickSpy).toHaveBeenCalledOnce();
      expect(revokeSpy).toHaveBeenCalledWith('blob:x');
    } finally {
      createUrlSpy.mockRestore();
      revokeSpy.mockRestore();
      clickSpy.mockRestore();
    }
  });

  it('drops a manual-record success that lands after the manage right was lost', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['ADMIN']));
    initialLoad(http);
    fixture.detectChanges();

    internals.toggleManualForm();
    http.expectOne(CANDIDATES_URL).flush(CANDIDATES);
    internals.manualForm.patchValue({ enrollmentPublicId: 'e-1', status: 'ABSENT', comment: 'x' });
    internals.submitManual();
    const post = http.expectOne('/api/v1/sessions/s-1/attendance/manual');

    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();

    post.flush({});
    fixture.detectChanges();
    // Aucun rafraîchissement de la liste des présences n'est déclenché.
    http.expectNone(ATTENDANCE_URL);
    const notifications = TestBed.inject(NotificationService);
    expect(notifications.info).not.toHaveBeenCalledWith('Présence enregistrée.');
    expect(internals.showManualForm()).toBe(false);
  });

  it('fetches the correction history of a row on demand', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    internals.toggleHistory('a-1');
    const req = http.expectOne('/api/v1/sessions/s-1/attendance/a-1/history');
    expect(req.request.method).toBe('GET');
    req.flush([
      {
        publicId: 'h-1',
        action: 'CREATED_MANUALLY',
        previousStatus: null,
        newStatus: 'ABSENT',
        previousLateMinutes: null,
        newLateMinutes: null,
        previousComment: null,
        newComment: 'x',
        reason: 'constat',
        occurredAt: '2026-09-10T09:00:00Z',
      },
    ]);
    fixture.detectChanges();
    expect(text()).toContain('Créée manuellement');
  });

  it('closes the checkpoint and manual forms when the active role context loses the manage right', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['ADMIN']));
    initialLoad(http);
    internals.toggleCheckpointForm();
    internals.toggleManualForm();
    http.expectOne(CANDIDATES_URL).flush(CANDIDATES);
    expect(internals.showCheckpointForm()).toBe(true);

    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();
    expect(internals.showCheckpointForm()).toBe(false);
    expect(internals.showManualForm()).toBe(false);
    expect(internals.candidates()).toEqual([]);
    expect(internals.attendanceCancelId()).toBeNull();
  });

  it('renders a not-found panel on a 404 and never touches browser storage', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    http.expectOne(GET_URL).flush(
      {
        timestamp: 't',
        status: 404,
        code: 'SESSION_NOT_FOUND',
        message: 'Introuvable.',
        path: '/',
        correlationId: null,
        details: [],
      },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    expect(text()).toContain('Aucune séance ne correspond');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
