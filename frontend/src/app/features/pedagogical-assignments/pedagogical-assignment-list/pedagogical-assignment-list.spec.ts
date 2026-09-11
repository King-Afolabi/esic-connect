import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NotificationService } from '../../../core/notifications/notification.service';
import { PedagogicalAssignmentList } from './pedagogical-assignment-list';

const ASSIGNMENTS_URL = '/api/v1/pedagogical-assignments';
const PROGRAMS_URL = '/api/v1/programs';
const USERS_URL = '/api/v1/users';

function programsPage() {
  return {
    content: [
      { publicId: 'prg-1', code: 'BTS-CIEL', name: 'BTS CIEL', programType: 'BTS', description: null, status: 'ACTIVE', archivedAt: null, archiveReason: null, createdAt: '', updatedAt: '' },
    ],
    page: 0,
    size: 200,
    totalElements: 1,
    totalPages: 1,
  };
}

function assignmentsPage(content: unknown[] = []) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

const ASSIGNMENT = {
  publicId: 'pa-1',
  programPublicId: 'prg-1',
  programCode: 'BTS-CIEL',
  userPublicId: 'u-1',
  type: 'PRIMARY_MANAGER',
  status: 'ACTIVE',
  validFrom: '2026-09-01',
  validUntil: null,
  reason: null,
  closeReason: null,
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
};

function setup() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(PedagogicalAssignmentList);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http };
}

describe('PedagogicalAssignmentList', () => {
  let fixture: ComponentFixture<PedagogicalAssignmentList>;
  let http: HttpTestingController;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('loads assignments filtered on ACTIVE by default and resolves manager names', () => {
    ({ fixture, http } = setup());

    const listReq = http.expectOne((r) => r.url === ASSIGNMENTS_URL);
    expect(listReq.request.params.get('status')).toBe('ACTIVE');
    listReq.flush(assignmentsPage([ASSIGNMENT]));
    http.expectOne((r) => r.url === PROGRAMS_URL).flush(programsPage());
    fixture.detectChanges();

    const userReq = http.expectOne((r) => r.url === `${USERS_URL}/u-1`);
    userReq.flush({
      publicId: 'u-1',
      email: 'manager@esic.test',
      firstName: 'Fatou',
      lastName: 'Diallo',
      phone: null,
      status: 'ACTIVE',
      emailVerifiedAt: null,
      lastLoginAt: null,
      suspendedAt: null,
      suspensionReason: null,
      archivedAt: null,
      createdAt: '',
      updatedAt: '',
      roleAssignments: [],
    });
    fixture.detectChanges();

    expect(text()).toContain('Fatou Diallo');
    expect(text()).toContain('BTS-CIEL');
  });

  it('shows a forbidden panel on a 403 response', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === ASSIGNMENTS_URL).flush(
      { timestamp: 't', status: 403, code: 'ACAD_FORBIDDEN', message: 'x', path: '/', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    http.expectOne((r) => r.url === PROGRAMS_URL).flush(programsPage());
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé");
  });

  it('creates a new assignment after picking a manager from the live search results', () => {
    vi.useFakeTimers();
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === ASSIGNMENTS_URL).flush(assignmentsPage([]));
    http.expectOne((r) => r.url === PROGRAMS_URL).flush(programsPage());
    fixture.detectChanges();

    const internals = fixture.componentInstance as unknown as {
      toggleCreateForm: () => void;
      createForm: {
        controls: { programPublicId: { setValue: (v: string) => void }; managerSearch: { setValue: (v: string) => void } };
      };
      selectManager: (u: { publicId: string; firstName: string; lastName: string; email: string }) => void;
      submitCreate: () => void;
    };

    internals.toggleCreateForm();
    fixture.detectChanges();
    internals.createForm.controls.programPublicId.setValue('prg-1');
    internals.createForm.controls.managerSearch.setValue('Fatou');
    vi.advanceTimersByTime(300);
    vi.useRealTimers();
    fixture.detectChanges();

    const searchReq = http.expectOne(
      (r) => r.url === USERS_URL && r.params.get('role') === 'PEDAGOGICAL_MANAGER',
    );
    searchReq.flush({
      content: [{ publicId: 'u-1', email: 'manager@esic.test', firstName: 'Fatou', lastName: 'Diallo', status: 'ACTIVE', roles: ['PEDAGOGICAL_MANAGER'], createdAt: '', lastLoginAt: null }],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();

    internals.selectManager({ publicId: 'u-1', firstName: 'Fatou', lastName: 'Diallo', email: 'manager@esic.test' });
    internals.submitCreate();

    const createReq = http.expectOne((r) => r.url === ASSIGNMENTS_URL && r.method === 'POST');
    expect(createReq.request.body.userPublicId).toBe('u-1');
    expect(createReq.request.body.programPublicId).toBe('prg-1');
    expect(createReq.request.body.type).toBe('PRIMARY_MANAGER');
    createReq.flush(ASSIGNMENT);

    http.expectOne((r) => r.url === ASSIGNMENTS_URL).flush(assignmentsPage([ASSIGNMENT]));
    http.expectOne((r) => r.url === `${USERS_URL}/u-1`).flush({
      publicId: 'u-1', email: 'manager@esic.test', firstName: 'Fatou', lastName: 'Diallo', phone: null,
      status: 'ACTIVE', emailVerifiedAt: null, lastLoginAt: null, suspendedAt: null, suspensionReason: null,
      archivedAt: null, createdAt: '', updatedAt: '', roleAssignments: [],
    });
  });

  it('closes an active assignment with a mandatory reason', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === ASSIGNMENTS_URL).flush(assignmentsPage([ASSIGNMENT]));
    http.expectOne((r) => r.url === PROGRAMS_URL).flush(programsPage());
    fixture.detectChanges();
    http.expectOne((r) => r.url === `${USERS_URL}/u-1`).flush({
      publicId: 'u-1', email: 'manager@esic.test', firstName: 'Fatou', lastName: 'Diallo', phone: null,
      status: 'ACTIVE', emailVerifiedAt: null, lastLoginAt: null, suspendedAt: null, suspensionReason: null,
      archivedAt: null, createdAt: '', updatedAt: '', roleAssignments: [],
    });
    fixture.detectChanges();

    const internals = fixture.componentInstance as unknown as {
      startClose: (id: string) => void;
      closeForm: { controls: { reason: { setValue: (v: string) => void } } };
      confirmClose: () => void;
    };
    internals.startClose('pa-1');
    internals.closeForm.controls.reason.setValue('Fin de contrat');
    internals.confirmClose();

    const closeReq = http.expectOne((r) => r.url === `${ASSIGNMENTS_URL}/pa-1/close`);
    expect(closeReq.request.body).toEqual({ reason: 'Fin de contrat', effectiveDate: null });
    closeReq.flush(null);

    http.expectOne((r) => r.url === ASSIGNMENTS_URL).flush(assignmentsPage([]));
  });
});
