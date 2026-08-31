import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { Role } from '../../../../core/models/role';
import { RoleContextService } from '../../../../core/auth/role-context.service';
import { JobResponse, PageResponse, RowResponse } from '../student-import.models';
import { StudentImportReview } from './student-import-review';

interface Internals {
  jobState: () => { kind: string };
  rowsState: () => { kind: string };
  confirmState: () => { kind: string; message?: string; result?: unknown };
  confirmable: () => boolean;
  canWrite: () => boolean;
  applyFilters: () => void;
  openConfirm: () => void;
  confirm: () => void;
  cancel: () => void;
  filters: { setValue: (v: { rowStatus: string; severity: string; action: string }) => void };
}

const JOB_URL = '/api/v1/student-imports/job-1';
const ROWS_URL = '/api/v1/student-imports/job-1/rows';

const JOB: JobResponse = {
  publicId: 'job-1',
  status: 'SIMULATED',
  fileName: 'apprenants.csv',
  fileSha256: 'a'.repeat(64),
  fileSizeBytes: 1024,
  csvSeparator: ',',
  scopeProgramCode: null,
  scopeClassCode: null,
  confirmable: true,
  summary: {
    total: 3,
    valid: 3,
    warning: 0,
    error: 0,
    blocking: 0,
    plannedCreate: 3,
    plannedUpdate: 0,
    plannedTransfer: 0,
    plannedNoop: 0,
  },
  issues: [],
  simulatedAt: '2026-08-31T08:00:00Z',
  expiresAt: '2026-09-07T08:00:00Z',
  confirmedAt: null,
  appliedSummary: null,
  createdAt: '2026-08-31T08:00:00Z',
};

const ROW: RowResponse = {
  publicId: 'r-1',
  rowNumber: 2,
  rowStatus: 'VALID',
  plannedAction: 'CREATE_ACCOUNT_AND_ENROLL',
  lastName: 'Doe',
  firstName: 'Jane',
  email: 'jane@x.test',
  phone: null,
  formationCode: 'BTS',
  classCode: 'C1',
  academicYear: '2026-2027',
  studentNumber: null,
  birthDate: null,
  workStudy: null,
  companyName: null,
  resolvedClassPublicId: 'c-1',
  resolvedUserPublicId: null,
  resolvedEnrollmentPublicId: null,
  studentNumberGenerated: true,
  appliedOutcome: null,
  issues: [],
};

function rowsPage(content: RowResponse[]): PageResponse<RowResponse> {
  return { content, page: 0, size: 50, totalElements: content.length, totalPages: 1 };
}

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: { get: () => 'job-1' } } },
      },
    ],
  });
  const fixture = TestBed.createComponent(StudentImportReview);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  http.expectOne(JOB_URL).flush(JOB);
  http.expectOne((r) => r.url === ROWS_URL).flush(rowsPage([ROW]));
  return {
    fixture,
    http,
    effectiveRoles,
    internals: fixture.componentInstance as unknown as Internals,
  };
}

describe('StudentImportReview', () => {
  it('loads the job summary and the rows on init', () => {
    const { internals } = setup();
    expect(internals.jobState().kind).toBe('ready');
    expect(internals.rowsState().kind).toBe('ready');
    expect(internals.confirmable()).toBe(true);
  });

  it('re-queries the rows from page 0 when filters are applied', () => {
    const { internals, http } = setup();
    internals.filters.setValue({ rowStatus: 'ERROR', severity: '', action: '' });
    internals.applyFilters();
    const req = http.expectOne(
      (r) => r.url === ROWS_URL && r.params.get('rowStatus') === 'ERROR' && r.params.get('page') === '0',
    );
    req.flush(rowsPage([]));
  });

  it('confirms and shows the memorised report', () => {
    const { internals, http } = setup();
    internals.openConfirm();
    expect(internals.confirmState().kind).toBe('panel');
    internals.confirm();
    http.expectOne((r) => r.url === `${JOB_URL}/confirm` && r.method === 'POST').flush({
      jobPublicId: 'job-1',
      alreadyApplied: false,
      created: 3,
      updated: 0,
      transferred: 0,
      invited: 3,
      ignored: 0,
    });
    http.expectOne(JOB_URL).flush({ ...JOB, status: 'APPLIED' });
    const state = internals.confirmState();
    expect(state.kind).toBe('done');
    expect((state.result as { created: number }).created).toBe(3);
  });

  it('handles IMP_STALE_SIMULATION by reloading and blocking confirmation', () => {
    const { internals, http } = setup();
    internals.openConfirm();
    internals.confirm();
    http.expectOne((r) => r.url === `${JOB_URL}/confirm`).flush(
      {
        timestamp: 't',
        status: 409,
        code: 'IMP_STALE_SIMULATION',
        message: 'La simulation n’est plus à jour.',
        path: JOB_URL,
        correlationId: null,
        details: [],
      },
      { status: 409, statusText: 'Conflict' },
    );
    http.expectOne(JOB_URL).flush(JOB);
    http.expectOne((r) => r.url === ROWS_URL).flush(rowsPage([{ ...ROW, rowStatus: 'ERROR' }]));
    expect(internals.confirmState().kind).toBe('stale');
    expect(internals.confirmable()).toBe(false);
  });

  it('reports a controlled message for IMP_SIMULATION_EXPIRED, never a false success', () => {
    const { internals, http } = setup();
    internals.openConfirm();
    internals.confirm();
    http.expectOne((r) => r.url === `${JOB_URL}/confirm`).flush(
      {
        timestamp: 't',
        status: 409,
        code: 'IMP_SIMULATION_EXPIRED',
        message: 'Cette simulation a expiré : relancez un import.',
        path: JOB_URL,
        correlationId: null,
        details: [],
      },
      { status: 409, statusText: 'Conflict' },
    );
    const state = internals.confirmState();
    expect(state.kind).toBe('error');
    expect(state.message).toContain('expiré');
  });

  it('cancels a SIMULATED job and reloads', () => {
    const { internals, http } = setup();
    internals.cancel();
    http.expectOne((r) => r.url === `${JOB_URL}/cancel` && r.method === 'POST').flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    http.expectOne(JOB_URL).flush({ ...JOB, status: 'CANCELLED' });
    expect(internals.confirmState().kind).toBe('idle');
  });

  it('hides write actions and ignores a late response when the role context is lost', () => {
    const { internals, http, effectiveRoles } = setup(['ADMIN']);
    internals.openConfirm();
    internals.confirm();
    const req = http.expectOne((r) => r.url === `${JOB_URL}/confirm`);
    effectiveRoles.set(['TEACHER']); // droit d'écriture perdu pendant l'appel
    req.flush({
      jobPublicId: 'job-1',
      alreadyApplied: false,
      created: 3,
      updated: 0,
      transferred: 0,
      invited: 3,
      ignored: 0,
    });
    expect(internals.canWrite()).toBe(false);
    expect(internals.confirmState().kind).toBe('running'); // réponse tardive ignorée, aucune fausse confirmation
  });

  it('never touches browser storage', () => {
    setup();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
