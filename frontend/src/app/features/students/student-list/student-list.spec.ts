import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { PageResponse, StudentResponse } from '../students.models';
import { StudentList } from './student-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string }) => void };
  applyFilters: () => void;
  resetFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
  startAssign: (userPublicId: string) => void;
  assignForm: { controls: { classGroupPublicId: { setValue: (v: string) => void } } };
  confirmAssign: () => void;
}

const URL = '/api/v1/students';
const CLASS_GROUPS_URL = '/api/v1/class-groups';

function page(content: StudentResponse[]): PageResponse<StudentResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function classGroupsPage() {
  return {
    content: [{ publicId: 'c-1', code: 'C1', name: 'Classe 1', promotionPublicId: 'p', programLevelPublicId: 'l', sitePublicId: null, capacity: null, status: 'ACTIVE', archivedAt: null, archiveReason: null, createdAt: '', updatedAt: '' }],
    page: 0,
    size: 200,
    totalElements: 1,
    totalPages: 1,
  };
}

/** Apprenant complet : profil et inscription renseignés. */
const FULL_STUDENT: StudentResponse = {
  userPublicId: 'u-1',
  email: 'alice@esic-connect.test',
  firstName: 'Alice',
  lastName: 'Durand',
  accountStatus: 'ACTIVE',
  createdAt: '2026-08-01T10:00:00Z',
  lastLoginAt: null,
  studentProfilePublicId: 'p-1',
  studentNumber: 'ESIC-2026-0001',
  birthDate: '2004-05-10',
  workStudy: true,
  companyName: 'ACME',
  profileStatus: 'ACTIVE',
  currentEnrollmentPublicId: 'e-1',
  classGroupPublicId: 'c-1',
  classGroupCode: 'C1',
  academicYearPublicId: 'y-1',
  academicYearCode: '2026-2027',
  enrollmentStatus: 'ACTIVE',
};

/** Apprenant minimal : rôle STUDENT seul, ni profil ni inscription. */
const BARE_STUDENT: StudentResponse = {
  userPublicId: 'u-2',
  email: 'bare@esic-connect.test',
  firstName: 'Sans',
  lastName: 'RienDautre',
  accountStatus: 'ACTIVE',
  createdAt: '2026-08-02T10:00:00Z',
  lastLoginAt: null,
  studentProfilePublicId: null,
  studentNumber: null,
  birthDate: null,
  workStudy: null,
  companyName: null,
  profileStatus: null,
  currentEnrollmentPublicId: null,
  classGroupPublicId: null,
  classGroupCode: null,
  academicYearPublicId: null,
  academicYearCode: null,
  enrollmentStatus: null,
};

describe('StudentList', () => {
  let fixture: ComponentFixture<StudentList>;
  let http: HttpTestingController;
  let internals: ListInternals;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    fixture = TestBed.createComponent(StudentList);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as ListInternals;
    fixture.detectChanges();
    // Chargée une fois à la construction, indépendamment des filtres —
    // alimente le sélecteur de classe des actions « attribuer ».
    http.expectOne((r) => r.url === CLASS_GROUPS_URL).flush(classGroupsPage());
  });

  afterEach(() => http.verify());

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const expectList = (): TestRequest => http.expectOne((r) => r.url === URL);

  it('requests the first page with the default sort (createdAt,desc) and shows a loading state', () => {
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(text()).toContain('Chargement des apprenants');
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    req.flush(page([FULL_STUDENT]));
  });

  it('renders one row per STUDENT account with a keyboard-reachable link to the detail route', () => {
    expectList().flush(page([FULL_STUDENT]));
    fixture.detectChanges();

    expect(text()).toContain('ESIC-2026-0001');
    expect(text()).toContain('ACME');
    const link = fixture.nativeElement.querySelector('a[href="/students/u-1"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('Consulter');
  });

  it('shows a STUDENT account without any profile nor enrollment directly in the list — no separate panel', () => {
    // Refonte 2026-09 : plus de panneau « comptes sans profil » distinct
    // ni de comparaison côté client — le compte apparaît normalement.
    expectList().flush(page([BARE_STUDENT]));
    fixture.detectChanges();

    expect(text()).toContain('RienDautre');
    expect(text()).toContain('bare@esic-connect.test');
    expect(text()).toContain('Sans classe');
    const link = fixture.nativeElement.querySelector('a[href="/students/u-2"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
  });

  it('shows the empty state when no student matches', () => {
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun apprenant');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('renders an access-denied panel on a 403 from the API', () => {
    expectList().flush(
      { status: 403, code: 'ACCESS_DENIED', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter les apprenants");
  });

  it('shows a generic error with a retry that re-requests the list', () => {
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');

    const retry = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    expectList().flush(page([FULL_STUDENT]));
    fixture.detectChanges();
    expect(text()).toContain('ESIC-2026-0001');
  });

  it('applies the name/email and account-status filters and resets to the first page', () => {
    expectList().flush(page([FULL_STUDENT]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([FULL_STUDENT]));

    internals.filters.setValue({ q: '  Durand  ', status: 'SUSPENDED' });
    internals.applyFilters();

    const req = expectList();
    expect(req.request.params.get('q')).toBe('Durand');
    expect(req.request.params.get('status')).toBe('SUSPENDED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('only ever sends a whitelisted sort field', () => {
    expectList().flush(page([FULL_STUDENT]));

    internals.onSortChange({ active: 'lastName', direction: 'asc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('lastName,asc');
    r1.flush(page([FULL_STUDENT]));

    // A field outside the back-end whitelist falls back to the default.
    internals.onSortChange({ active: 'companyName', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('createdAt,asc');
    r2.flush(page([FULL_STUDENT]));
  });

  it('requests the requested page and size on pagination', () => {
    expectList().flush(page([FULL_STUDENT]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([FULL_STUDENT]));
  });

  it('writes nothing to browser storage', () => {
    expectList().flush(page([FULL_STUDENT]));
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});

describe('StudentList — restauration depuis l’URL (Lot G)', () => {
  it('reconstruit filtres, tri et page depuis les query params', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParams: {
                q: 'Durand',
                status: 'SUSPENDED',
                sort: 'lastName',
                dir: 'asc',
                page: '2',
                size: '50',
              },
            },
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(StudentList);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne((r) => r.url === CLASS_GROUPS_URL).flush(classGroupsPage());

    const req = http.expectOne((r) => r.url === URL);
    expect(req.request.params.get('q')).toBe('Durand');
    expect(req.request.params.get('status')).toBe('SUSPENDED');
    expect(req.request.params.get('sort')).toBe('lastName,asc');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 2, size: 50, totalElements: 130, totalPages: 3 });
    http.verify();
  });
});

/**
 * « Un apprenant sans classe reste un apprenant » (retour terrain) : un
 * compte STUDENT sans inscription active reste visible avec un badge
 * « Sans classe » et une action pour lui en attribuer une — réservée aux
 * rôles qui gèrent les inscriptions.
 */
describe('StudentList — apprenants sans classe', () => {
  let fixture: ComponentFixture<StudentList>;
  let http: HttpTestingController;
  let internals: ListInternals;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  function setup(roles: Role[]) {
    TestBed.resetTestingModule();
    const effectiveRoles: WritableSignal<Role[]> = signal(roles);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RoleContextService, useValue: { effectiveRoles } },
      ],
    });
    fixture = TestBed.createComponent(StudentList);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as ListInternals;
    fixture.detectChanges();
    http.expectOne((r) => r.url === CLASS_GROUPS_URL).flush(classGroupsPage());
  }

  afterEach(() => http.verify());

  it('shows "Sans classe" and lets an admin assign one to a student without an active enrollment', () => {
    setup(['ADMIN']);
    const withoutClass: StudentResponse = { ...FULL_STUDENT, classGroupPublicId: null, classGroupCode: null };
    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([withoutClass]));
    fixture.detectChanges();

    expect(text()).toContain('Sans classe');

    internals.startAssign('u-1');
    fixture.detectChanges();
    internals.assignForm.controls.classGroupPublicId.setValue('c-1');
    internals.confirmAssign();

    const enrollReq = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/enrollments');
    expect(enrollReq.request.body).toEqual({ studentUserPublicId: 'u-1', classGroupPublicId: 'c-1' });
    enrollReq.flush({});

    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([FULL_STUDENT]));
  });

  it('does not show the "assign a class" action for a TEACHER (read-only role)', () => {
    setup(['TEACHER']);
    const withoutClass: StudentResponse = { ...FULL_STUDENT, classGroupPublicId: null, classGroupCode: null };
    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([withoutClass]));
    fixture.detectChanges();

    expect(text()).toContain('Sans classe');
    const assignButton = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Attribuer une classe'),
    );
    expect(assignButton).toBeUndefined();
  });
});
