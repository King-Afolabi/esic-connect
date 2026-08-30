import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { AttendanceReport } from './attendance-report';
import { AttendanceSummary } from './attendance-summary';
import { JustificationQueue } from './justification-queue';

const SUMMARY_URL = '/api/v1/attendance/reports/summary';
const SESSIONS_URL = '/api/v1/attendance/reports/sessions';
const SESSIONS_EXPORT_URL = '/api/v1/attendance/reports/sessions/export';
const JUSTIF_URL = '/api/v1/attendance/justifications';

const EMPTY_TOTALS = {
  expectedHalfDays: 4,
  presentHalfDays: 3,
  absentHalfDays: 1,
  excusedHalfDays: 0,
  companyHalfDays: 2,
  unknownHalfDays: 1,
  lateCount: 1,
  attendanceRate: 0.75,
  unjustifiedAbsenceRate: 0.25,
};

function base(roles: Role[]) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  return {
    effectiveRoles,
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  };
}

describe('AttendanceSummary', () => {
  it('renders the synthesis cards and the COMPANY / UNKNOWN caveat notes', () => {
    const { providers } = base(['SCHOOL_ADMINISTRATION']);
    TestBed.configureTestingModule({ providers });
    const fixture = TestBed.createComponent(AttendanceSummary);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url === SUMMARY_URL).flush({
      from: null,
      to: null,
      classCount: 1,
      sessionCount: 2,
      totals: EMPTY_TOTALS,
      pendingJustifications: 3,
      notes: ['Les demi-journées en contexte COMPANY sont exclues du dénominateur.'],
    });
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('75,0 %');
    expect(text).toContain('Justificatifs en attente');
    expect(text).toContain('contexte COMPANY sont exclues');
    expect(localStorage.length).toBe(0);
    http.verify();
  });

  it('renders a forbidden panel on 403', () => {
    const { providers } = base(['PEDAGOGICAL_MANAGER']);
    TestBed.configureTestingModule({ providers });
    const fixture = TestBed.createComponent(AttendanceSummary);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url === SUMMARY_URL).flush(
      { timestamp: 't', status: 403, code: 'ACCESS_DENIED', message: 'x', path: '/', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain("Vous n'êtes pas autorisé");
    http.verify();
  });
});

describe('AttendanceReport', () => {
  function setupReport(kind: 'sessions' | 'classes' | 'students') {
    const { providers } = base(['ADMIN']);
    TestBed.configureTestingModule({
      providers: [
        ...providers,
        { provide: ActivatedRoute, useValue: { snapshot: { data: { kind }, paramMap: { get: () => null } } } },
      ],
    });
    const fixture = TestBed.createComponent(AttendanceReport);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    return { fixture, http };
  }

  it('loads the sessions report and forwards paging', () => {
    const { fixture, http } = setupReport('sessions');
    const req = http.expectOne((r) => r.url === SESSIONS_URL);
    expect(req.request.params.get('size')).toBe('20');
    req.flush({
      content: [
        {
          sessionPublicId: 's-1',
          sessionTitle: 'Atelier',
          startsAt: '2026-09-10T08:00:00Z',
          endsAt: '2026-09-10T12:00:00Z',
          classCodes: 'C1',
          teacherName: 'A. Martin',
          checkpointCount: 1,
          expectedCount: 2,
          presentCount: 1,
          lateCount: 0,
          absentCount: 1,
          excusedCount: 0,
          attendanceRate: 0.5,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Atelier');
    http.verify();
  });

  it('exportCsv requests a blob and triggers a programmatic download (no navigation URL)', () => {
    const createUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    const { fixture, http } = setupReport('sessions');
    http.expectOne((r) => r.url === SESSIONS_URL).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    (fixture.componentInstance as unknown as { exportCsv: () => void }).exportCsv();
    const req = http.expectOne((r) => r.url === SESSIONS_EXPORT_URL);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['session_id;titre'], { type: 'text/csv' }), {
      headers: { 'content-disposition': 'attachment; filename="assiduite_sessions.csv"' },
    });

    expect(createUrlSpy).toHaveBeenCalledOnce();
    expect(clickSpy).toHaveBeenCalledOnce();
    expect(revokeSpy).toHaveBeenCalledWith('blob:x');
    createUrlSpy.mockRestore();
    revokeSpy.mockRestore();
    clickSpy.mockRestore();
    http.verify();
  });

  it('only forwards a whitelisted sort value and drops anything off-list', () => {
    const { fixture, http } = setupReport('students');
    http.expectOne((r) => r.url === '/api/v1/attendance/reports/students').flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    const internals = fixture.componentInstance as unknown as {
      filters: { patchValue: (v: Record<string, unknown>) => void };
      apply: () => void;
    };

    internals.filters.patchValue({ sort: 'studentNumber,desc' });
    internals.apply();
    const ok = http.expectOne((r) => r.url === '/api/v1/attendance/reports/students');
    expect(ok.request.params.get('sort')).toBe('studentNumber,desc');
    ok.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    // Valeur hors liste blanche (injectée dans le contrôle) : jamais transmise.
    internals.filters.patchValue({ sort: 'email,asc' });
    internals.apply();
    const guarded = http.expectOne((r) => r.url === '/api/v1/attendance/reports/students');
    expect(guarded.request.params.has('sort')).toBe(false);
    guarded.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    http.verify();
  });
});

describe('JustificationQueue', () => {
  function setupQueue(roles: Role[]) {
    const b = base(roles);
    TestBed.configureTestingModule({ providers: b.providers });
    const fixture = TestBed.createComponent(JustificationQueue);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    return { fixture, http, effectiveRoles: b.effectiveRoles };
  }

  const PENDING = {
    publicId: 'j-1',
    status: 'PENDING',
    category: 'MEDICAL',
    externalReference: null,
    comment: 'certificat',
    submittedAt: '2026-09-11T09:00:00Z',
    reviewedAt: null,
    decisionReason: null,
    sessionPublicId: 's-1',
    sessionTitle: 'Atelier',
    sessionStartsAt: '2026-09-10T08:00:00Z',
    checkpointPublicId: 'cp-1',
    checkpointLabel: 'Arrivée',
    classCode: 'C1',
    studentProfilePublicId: 'p-1',
    studentNumber: 'ESIC-1',
    firstName: 'Bob',
    lastName: 'Durand',
    attendanceStatus: 'ABSENT',
  };

  it('lists PENDING justifications and reviews one with an accept decision', () => {
    const { fixture, http } = setupQueue(['SCHOOL_ADMINISTRATION']);
    const first = http.expectOne((r) => r.url === JUSTIF_URL);
    expect(first.request.params.get('status')).toBe('PENDING');
    first.flush([PENDING]);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Bob Durand');

    const internals = fixture.componentInstance as unknown as {
      startReview: (r: unknown) => void;
      reviewForm: { patchValue: (v: unknown) => void };
      submitReview: () => void;
    };
    internals.startReview(PENDING);
    internals.reviewForm.patchValue({ decision: 'ACCEPTED', decisionReason: '' });
    internals.submitReview();
    const review = http.expectOne(`${JUSTIF_URL}/j-1/review`);
    expect(review.request.body).toEqual({ decision: 'ACCEPTED', decisionReason: null });
    review.flush({ ...PENDING, status: 'ACCEPTED' });
    http.expectOne((r) => r.url === JUSTIF_URL).flush([]);
    http.verify();
  });

  it('blocks a reject without a reason before any HTTP call', () => {
    const { fixture, http } = setupQueue(['ADMIN']);
    http.expectOne((r) => r.url === JUSTIF_URL).flush([PENDING]);
    const internals = fixture.componentInstance as unknown as {
      startReview: (r: unknown) => void;
      reviewForm: { patchValue: (v: unknown) => void };
      submitReview: () => void;
    };
    internals.startReview(PENDING);
    internals.reviewForm.patchValue({ decision: 'REJECTED', decisionReason: '' });
    internals.submitReview();
    http.expectNone(`${JUSTIF_URL}/j-1/review`);
    http.verify();
  });

  it('does not review once the active role context loses the manage right', () => {
    const { fixture, http, effectiveRoles } = setupQueue(['SCHOOL_ADMINISTRATION']);
    http.expectOne((r) => r.url === JUSTIF_URL).flush([PENDING]);
    const internals = fixture.componentInstance as unknown as {
      startReview: (r: unknown) => void;
      reviewForm: { patchValue: (v: unknown) => void };
      submitReview: () => void;
    };
    internals.startReview(PENDING);
    internals.reviewForm.patchValue({ decision: 'ACCEPTED', decisionReason: '' });
    effectiveRoles.set(['TEACHER']);
    fixture.detectChanges();
    internals.submitReview();
    http.expectNone(`${JUSTIF_URL}/j-1/review`);
    http.verify();
  });

  it('closes the panel via effect and drops a review success that lands after the right was lost', () => {
    const { fixture, http, effectiveRoles } = setupQueue(['SCHOOL_ADMINISTRATION']);
    http.expectOne((r) => r.url === JUSTIF_URL).flush([PENDING]);
    const internals = fixture.componentInstance as unknown as {
      startReview: (r: unknown) => void;
      reviewForm: { patchValue: (v: unknown) => void };
      submitReview: () => void;
      reviewId: () => string | null;
    };
    internals.startReview(PENDING);
    internals.reviewForm.patchValue({ decision: 'ACCEPTED', decisionReason: '' });
    internals.submitReview();
    const review = http.expectOne(`${JUSTIF_URL}/j-1/review`);

    effectiveRoles.set(['TEACHER']);
    fixture.detectChanges();
    expect(internals.reviewId()).toBeNull();

    review.flush({ ...PENDING, status: 'ACCEPTED' });
    fixture.detectChanges();
    // Aucun rechargement de la file, aucun faux succès.
    http.expectNone((r) => r.url === JUSTIF_URL);
    const notifications = TestBed.inject(NotificationService);
    expect(notifications.info).not.toHaveBeenCalledWith('Justificatif examiné.');
    http.verify();
  });
});
