import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { CourseSessionResponse } from '../sessions.models';
import { SessionDetail } from './session-detail';

const GET_URL = '/api/v1/sessions/s-1';
const ATTENDANCE_URL = '/api/v1/sessions/s-1/attendance';
const TOKEN_URL = '/api/v1/sessions/s-1/attendance-token';

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
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-10T05:55:00Z',
};

const EMPTY_ATTENDANCE = {
  sessionPublicId: 's-1',
  checkpointPublicId: 'cp-1',
  expectedCount: 2,
  presentCount: 0,
  records: [],
};

const TOKEN = {
  token: 'OPAQUE-SERVER-TOKEN',
  shortCode: 'ABCD2345',
  expiresAt: '2026-09-10T06:00:30Z',
  sessionPublicId: 's-1',
  ttlSeconds: 30,
};

interface DetailInternals {
  startOpen: () => void;
  confirmOpen: () => void;
  startClose: () => void;
  confirmClose: () => void;
  refreshToken: () => void;
  refreshAttendance: () => void;
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

/** Répond au GET séance + GET présences émis à l'initialisation. */
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
    // Le polling modéré des présences peut avoir émis des GET pendant les
    // avances d'horloge simulée : on les draine avant la vérification.
    http.match(ATTENDANCE_URL).forEach((req) => {
      if (!req.cancelled) {
        req.flush(EMPTY_ATTENDANCE);
      }
    });
    http.verify();
  });

  it('loads the session facts and the attendance roster', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http);
    fixture.detectChanges();
    expect(text()).toContain('Rattrapage');
    expect(text()).toContain('Ouverte');
    expect(text()).toContain('Alice Martin');
    expect(text()).toContain('0 présent(s) sur 2 attendu(s)');
  });

  it('shows the open button for a PLANNED session and opens it after confirmation', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    initialLoad(http, { ...OPEN_SESSION, status: 'PLANNED', openedAt: null, checkpointOpen: false });
    fixture.detectChanges();
    expect(text()).toContain('Ouvrir la séance');

    internals.startOpen();
    internals.confirmOpen();
    http.expectOne((r) => r.url.endsWith('/open') && r.method === 'POST').flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    // reload
    http.expectOne(GET_URL).flush(OPEN_SESSION);
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);
  });

  it('issues an attendance token, shows the short code and a <qrcode>, never the token as text', () => {
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

    // ttl = 30 s, renouvellement 3 s avant -> ~27 s
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
    http.expectOne(GET_URL).flush({ ...OPEN_SESSION, status: 'CLOSED', closedAt: '2026-09-10T07:00:00Z', checkpointOpen: false });
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);
    fixture.detectChanges();

    // Aucun renouvellement ne doit plus partir.
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
      { timestamp: 't', status: 503, code: 'ATT_TOKEN_BACKEND_UNAVAILABLE', message: 'Service momentanément indisponible.', path: '/', correlationId: null, details: [] },
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
      presentCount: 1,
      records: [
        { studentProfilePublicId: 'sp', enrollmentPublicId: 'e', studentNumber: 'ESIC-1', firstName: 'Bob', lastName: 'Durand', recordedAt: '2026-09-10T06:01:00Z', source: 'SHORT_CODE' },
      ],
    });
    fixture.detectChanges();
    expect(text()).toContain('ESIC-1');
    expect(text()).toContain('Bob Durand');

    // Un tick de polling (15 s) tant que la séance est ouverte.
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

    // Un tick de polling tant que le contexte permet la lecture.
    vi.advanceTimersByTime(15_000);
    http.expectOne(ATTENDANCE_URL).flush(EMPTY_ATTENDANCE);

    // Bascule vers un contexte sans droit de lecture de la page.
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

    // Le contexte retire le droit de gestion pendant que l'émission est en vol.
    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();

    req.flush(TOKEN);
    fixture.detectChanges();

    // La réponse obsolète n'affiche pas de QR et ne programme aucun renouvellement.
    expect((fixture.nativeElement as HTMLElement).querySelector('qrcode')).toBeNull();
    vi.advanceTimersByTime(60_000);
    http.expectNone(TOKEN_URL);
  });

  it('renders a not-found panel on a 404 and a forbidden panel on a 403', () => {
    ({ fixture, http, internals } = setup(['ADMIN']));
    http.expectOne(GET_URL).flush(
      { timestamp: 't', status: 404, code: 'SESSION_NOT_FOUND', message: 'Introuvable.', path: '/', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();
    expect(text()).toContain('Aucune séance ne correspond');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
