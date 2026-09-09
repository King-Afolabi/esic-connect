import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { RoleContextService } from '../../../core/auth/role-context.service';
import { Role } from '../../../core/models/role';
import { NotificationService } from '../../../core/notifications/notification.service';
import { BulkResult, PageResponse, UserSummaryResponse } from '../administration.models';
import { UserList } from './user-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string; role: string }) => void };
  applyFilters: () => void;
  resetFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
  toggleAllOnPage: () => void;
  toggleRow: (publicId: string) => void;
  selectedCount: () => number;
  bulkForm: { setValue: (v: { action: string; reason: string }) => void };
  previewBulk: () => void;
  confirmBulk: () => void;
  cancelBulkPreview: () => void;
  clearSelection: () => void;
}

const URL = '/api/v1/users';

function page(content: UserSummaryResponse[]): PageResponse<UserSummaryResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

const USER: UserSummaryResponse = {
  publicId: 'u-1',
  email: 'alice.dupont@esic.test',
  firstName: 'Alice',
  lastName: 'Dupont',
  status: 'ACTIVE',
  roles: ['TEACHER', 'PEDAGOGICAL_MANAGER'],
  createdAt: '2026-08-01T10:00:00Z',
  lastLoginAt: '2026-08-20T08:30:00Z',
};

describe('UserList', () => {
  let fixture: ComponentFixture<UserList>;
  let http: HttpTestingController;
  let internals: ListInternals;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    fixture = TestBed.createComponent(UserList);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as ListInternals;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const expectList = (): TestRequest => http.expectOne((r) => r.url === URL);

  it('requests the first page with the default sort (createdAt,desc) and shows a loading state', () => {
    const req = expectList();
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(text()).toContain('Chargement des comptes');
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    req.flush(page([USER]));
  });

  it('renders one row per account with role labels and a keyboard-reachable link to the detail route', () => {
    expectList().flush(page([USER]));
    fixture.detectChanges();

    expect(text()).toContain('alice.dupont@esic.test');
    expect(text()).toContain('Alice Dupont');
    expect(text()).toContain('Formateur, Responsable pédagogique');
    const link = fixture.nativeElement.querySelector(
      'a[href="/administration/u-1"]',
    ) as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('Consulter');
  });

  it('shows the empty state when no account matches', () => {
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun compte ne correspond');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('renders an access-denied panel on a 403 from the API', () => {
    expectList().flush(
      {
        status: 403,
        code: 'USER_OPERATION_FORBIDDEN',
        message: 'x',
        path: '',
        correlationId: null,
        details: [],
      },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter les comptes utilisateurs");
  });

  it('shows a generic error with a retry that re-requests the list', () => {
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');

    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    expectList().flush(page([USER]));
    fixture.detectChanges();
    expect(text()).toContain('alice.dupont@esic.test');
  });

  it('applies the q, status and role filters and resets to the first page', () => {
    expectList().flush(page([USER]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([USER]));

    internals.filters.setValue({ q: '  alice  ', status: 'SUSPENDED', role: 'STUDENT' });
    internals.applyFilters();

    const req = expectList();
    expect(req.request.params.get('q')).toBe('alice');
    expect(req.request.params.get('status')).toBe('SUSPENDED');
    expect(req.request.params.get('role')).toBe('STUDENT');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('only ever sends a whitelisted sort field', () => {
    expectList().flush(page([USER]));

    internals.onSortChange({ active: 'lastName', direction: 'asc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('lastName,asc');
    r1.flush(page([USER]));

    // A field outside the back-end whitelist falls back to the default.
    internals.onSortChange({ active: 'roles', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('createdAt,asc');
    r2.flush(page([USER]));
  });

  it('requests the requested page and size on pagination', () => {
    expectList().flush(page([USER]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([USER]));
  });

  it('writes nothing to browser storage', () => {
    expectList().flush(page([USER]));
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});

const BULK_URL = '/api/v1/users/bulk';

function bulkResult(overrides: Partial<BulkResult> = {}): BulkResult {
  return {
    applied: false,
    action: 'SUSPEND',
    requested: 1,
    eligible: 1,
    ignored: 0,
    rejected: 0,
    outcomes: [{ userId: 'u-1', email: 'alice.dupont@esic.test', outcome: 'ELIGIBLE', reason: '' }],
    ...overrides,
  };
}

describe('UserList — opérations de masse (EF-USER-004)', () => {
  let fixture: ComponentFixture<UserList>;
  let http: HttpTestingController;
  let internals: ListInternals;
  const notifications = { info: vi.fn(), error: vi.fn() };

  function setup(effectiveRoles: Role[]): void {
    notifications.info.mockReset();
    notifications.error.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RoleContextService, useValue: { effectiveRoles: signal(effectiveRoles) } },
        { provide: NotificationService, useValue: notifications },
      ],
    });
    fixture = TestBed.createComponent(UserList);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as ListInternals;
    fixture.detectChanges();
    http.expectOne((r) => r.url === URL).flush(page([USER]));
    fixture.detectChanges();
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('shows no selection column nor bulk panel for a role outside LIFECYCLE_ROLES', () => {
    setup(['TEACHER']);
    expect(fixture.nativeElement.querySelector('mat-checkbox')).toBeNull();
  });

  it('shows the selection column and count for ADMIN, matching the server LIFECYCLE_ROLES', () => {
    setup(['ADMIN']);
    expect(fixture.nativeElement.querySelector('mat-checkbox')).not.toBeNull();
    internals.toggleRow('u-1');
    fixture.detectChanges();
    expect(text()).toContain('1 compte(s) sélectionné(s)');
  });

  it('preview (confirm omitted) shows eligible/ignored/rejected without applying anything', () => {
    setup(['ADMIN']);
    internals.toggleRow('u-1');
    internals.bulkForm.setValue({ action: 'SUSPEND', reason: 'Test' });
    internals.previewBulk();

    const req = http.expectOne((r) => r.url === BULK_URL);
    expect(req.request.body).toEqual({
      action: 'SUSPEND',
      userIds: ['u-1'],
      reason: 'Test',
      confirm: false,
    });
    req.flush(bulkResult());
    fixture.detectChanges();

    expect(text()).toContain("Aperçu — rien n'a encore été écrit.");
    expect(text()).toContain('alice.dupont@esic.test');
    expect(notifications.info).not.toHaveBeenCalled();
  });

  it('confirming re-posts with confirm: true, clears the selection and reloads the list', () => {
    setup(['ADMIN']);
    internals.toggleRow('u-1');
    internals.bulkForm.setValue({ action: 'SUSPEND', reason: 'Test' });
    internals.previewBulk();
    http.expectOne((r) => r.url === BULK_URL).flush(bulkResult());
    fixture.detectChanges();

    internals.confirmBulk();
    const confirmReq = http.expectOne((r) => r.url === BULK_URL);
    expect(confirmReq.request.body).toEqual({
      action: 'SUSPEND',
      userIds: ['u-1'],
      reason: 'Test',
      confirm: true,
    });
    confirmReq.flush(bulkResult({ applied: true }));
    fixture.detectChanges();

    expect(notifications.info).toHaveBeenCalledTimes(1);
    expect(internals.selectedCount()).toBe(0);
    // La liste est rechargée après une exécution réelle.
    http.expectOne((r) => r.url === URL).flush(page([USER]));
  });

  it('cancelling the preview discards it without ever calling confirm', () => {
    setup(['ADMIN']);
    internals.toggleRow('u-1');
    internals.bulkForm.setValue({ action: 'SUSPEND', reason: 'Test' });
    internals.previewBulk();
    http.expectOne((r) => r.url === BULK_URL).flush(bulkResult());
    fixture.detectChanges();

    internals.cancelBulkPreview();
    fixture.detectChanges();
    expect(text()).not.toContain('éligible');
    http.expectNone(BULK_URL);
  });

  it('surfaces a server error on the bulk panel instead of throwing', () => {
    setup(['ADMIN']);
    internals.toggleRow('u-1');
    internals.bulkForm.setValue({ action: 'SUSPEND', reason: 'Test' });
    internals.previewBulk();
    http
      .expectOne((r) => r.url === BULK_URL)
      .flush(
        { status: 400, code: 'USER_INVALID_FILTER', message: 'Action inconnue.', path: '', correlationId: null, details: [] },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();
    expect(text()).toContain('Action inconnue.');
  });
});
