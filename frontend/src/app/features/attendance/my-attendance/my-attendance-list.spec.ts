import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { MyAttendanceList } from './my-attendance-list';

const URL = '/api/v1/me/attendance';

const ROW = {
  attendancePublicId: null,
  sessionPublicId: 's-1',
  sessionTitle: 'Atelier',
  sessionStartsAt: '2026-09-10T08:00:00Z',
  checkpointPublicId: 'cp-1',
  checkpointLabel: 'Arrivée',
  checkpointType: 'START',
  checkpointRequired: true,
  classCode: 'C1',
  status: 'ABSENT',
  lateMinutes: null,
  comment: null,
  recordedAt: null,
  justificationPublicId: null,
  justificationStatus: null,
  canJustify: true,
};

interface Internals {
  startJustify: (row: unknown) => void;
  submitJustify: () => void;
  justifyForm: { patchValue: (v: Record<string, unknown>) => void };
}

function setup(roles: Role[] = ['STUDENT']) {
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
    ],
  });
  const fixture = TestBed.createComponent(MyAttendanceList);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http, internals: fixture.componentInstance as unknown as Internals, effectiveRoles };
}

describe('MyAttendanceList', () => {
  let fixture: ComponentFixture<MyAttendanceList>;
  let http: HttpTestingController;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('loads the student rows and shows a "déposer un justificatif" action on justifiable absences', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush({
      content: [ROW],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    expect(text()).toContain('Atelier');
    expect(text()).toContain('Absent');
    expect(text()).toContain('Déposer un justificatif');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('applies the period filter, resetting the page and only sending non-empty params', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    (fixture.componentInstance as unknown as { filters: { patchValue: (v: unknown) => void } }).filters.patchValue({
      from: '2026-09-01',
      status: 'ABSENT',
    });
    (fixture.componentInstance as unknown as { applyFilters: () => void }).applyFilters();
    const req = http.expectOne((r) => r.url === URL);
    expect(req.request.params.get('status')).toBe('ABSENT');
    expect(req.request.params.get('from')).toContain('2026-09-01');
    expect(req.request.params.has('to')).toBe(false);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('submits a justification for the selected checkpoint and reloads', () => {
    let internals!: Internals;
    ({ fixture, http, internals } = setup());
    http.expectOne((r) => r.url === URL).flush({ content: [ROW], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    internals.startJustify(ROW);
    internals.justifyForm.patchValue({ category: 'MEDICAL', comment: 'certificat' });
    internals.submitJustify();

    const post = http.expectOne('/api/v1/me/attendance/justifications');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toMatchObject({ checkpointPublicId: 'cp-1', category: 'MEDICAL' });
    post.flush({});
    http.expectOne((r) => r.url === URL).flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('renders a forbidden panel on a 403 and never touches browser storage', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush(
      { timestamp: 't', status: 403, code: 'ACCESS_DENIED', message: 'x', path: '/', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé");
    expect(localStorage.length).toBe(0);
  });

  it('does not submit a justification once the active role context is no longer STUDENT', () => {
    let internals!: Internals;
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup());
    http.expectOne((r) => r.url === URL).flush({ content: [ROW], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    internals.startJustify(ROW);
    internals.justifyForm.patchValue({ category: 'MEDICAL', comment: 'x' });
    effectiveRoles.set(['ADMIN']);
    fixture.detectChanges();
    internals.submitJustify();
    http.expectNone('/api/v1/me/attendance/justifications');
  });
});
