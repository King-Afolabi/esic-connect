import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { CourseSessionResponse } from '../sessions.models';
import { SessionEdit } from './session-edit';

interface EditInternals {
  form: FormGroup;
  submit: () => void;
  retry: () => void;
  teachers: () => { publicId: string }[];
  classes: () => { publicId: string }[];
}

const SESSION_URL = '/api/v1/sessions/s-1';
const TEACHERS_URL = '/api/v1/sessions/teachers';
const CLASSES_URL = '/api/v1/class-groups';
const SUBJECTS_URL = '/api/v1/subjects';

const PLANNED_SESSION: CourseSessionResponse = {
  publicId: 's-1',
  status: 'PLANNED',
  title: 'Original',
  exceptionReason: 'motif original',
  teacher: { publicId: 't-1', firstName: 'Alice', lastName: 'Martin' },
  subject: null,
  roomCode: null,
  classes: [{ publicId: 'c-1', name: 'Classe 1', code: 'C1', academicYearCode: 'AY-2026' }],
  startsAt: '2026-09-10T06:00:00Z',
  endsAt: '2026-09-10T10:00:00Z',
  timeZoneId: 'Europe/Paris',
  attendanceMode: 'ON_SITE',
  remoteLink: null,
  openedAt: null,
  closedAt: null,
  cancellationReason: null,
  cancelledAt: null,
  checkpointPublicId: 'cp-1',
  checkpointOpen: false,
  checkpoints: [],
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-01T10:00:00Z',
};

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const navigate = vi.fn().mockResolvedValue(true);
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
  const router = TestBed.inject(Router);
  router.navigate = navigate as unknown as Router['navigate'];
  const fixture = TestBed.createComponent(SessionEdit);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as EditInternals;
  fixture.detectChanges();
  return { fixture, http, internals, navigate, effectiveRoles };
}

function loadReady(http: HttpTestingController, session: CourseSessionResponse = PLANNED_SESSION): void {
  http.expectOne(SESSION_URL).flush(session);
  http
    .expectOne(TEACHERS_URL)
    .flush([
      { publicId: 't-1', firstName: 'Alice', lastName: 'Martin' },
      { publicId: 't-2', firstName: 'Bob', lastName: 'Dupont' },
    ]);
  http
    .expectOne((r) => r.url === CLASSES_URL)
    .flush({
      content: [
        { publicId: 'c-1', code: 'C1', name: 'Classe 1', promotionPublicId: 'p', programLevelPublicId: 'l', sitePublicId: null, capacity: null, status: 'ACTIVE', archivedAt: null, archiveReason: null, createdAt: '', updatedAt: '' },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
  http
    .expectOne((r) => r.url === SUBJECTS_URL)
    .flush({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 });
}

describe('SessionEdit', () => {
  let fixture: ComponentFixture<SessionEdit>;
  let http: HttpTestingController;
  let internals: EditInternals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('prefills the form from the loaded session', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http);
    fixture.detectChanges();

    expect(internals.form.getRawValue()['teacherPublicId']).toBe('t-1');
    expect(internals.form.getRawValue()['reason']).toBe('motif original');
    expect(internals.form.getRawValue()['classPublicIds']).toEqual(['c-1']);
    expect(text()).toContain('Modifier la séance');
  });

  it('shows a not-editable panel when the session is no longer PLANNED', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http, { ...PLANNED_SESSION, status: 'OPEN' });
    fixture.detectChanges();

    expect(text()).toContain("n'est plus modifiable structurellement");
  });

  it('submits the edited fields via PATCH and navigates back to the session', () => {
    let navigate!: ReturnType<typeof vi.fn>;
    ({ fixture, http, internals, navigate } = setup());
    loadReady(http);
    fixture.detectChanges();

    internals.form.controls['teacherPublicId'].setValue('t-2');
    internals.form.controls['reason'].setValue('motif corrigé');
    internals.submit();

    const req = http.expectOne(SESSION_URL);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toMatchObject({
      teacherPublicId: 't-2',
      reason: 'motif corrigé',
      classPublicIds: ['c-1'],
      attendanceMode: 'ON_SITE',
      remoteLink: null,
    });
    req.flush({ ...PLANNED_SESSION, teacher: { publicId: 't-2', firstName: 'Bob', lastName: 'Dupont' } });
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's-1']);
  });

  it('requires a remote link once the modality is switched to REMOTE', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http);
    fixture.detectChanges();

    internals.form.controls['attendanceMode'].setValue('REMOTE');
    internals.submit();

    http.expectNone(SESSION_URL);
    expect(internals.form.controls['remoteLink'].hasError('required')).toBe(true);
  });

  it('neutralizes the form when the active role context loses the edit permission', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['ADMIN']));
    loadReady(http);
    fixture.detectChanges();

    effectiveRoles.set(['TEACHER']);
    fixture.detectChanges();

    expect(text()).toContain('ne permet plus de modifier');
    expect(internals.form.disabled).toBe(true);

    internals.submit();
    http.expectNone(SESSION_URL);
  });
});
