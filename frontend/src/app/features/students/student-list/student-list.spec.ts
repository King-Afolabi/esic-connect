import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { PageResponse, StudentProfileResponse } from '../students.models';
import { StudentList } from './student-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string }) => void };
  applyFilters: () => void;
  resetFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
  startAssign: (profilePublicId: string) => void;
  assignForm: { controls: { classGroupPublicId: { setValue: (v: string) => void } } };
  confirmAssign: () => void;
  startComplete: (userPublicId: string) => void;
  completeForm: { controls: { classGroupPublicId: { setValue: (v: string) => void } } };
  confirmComplete: () => void;
}

const URL = '/api/v1/student-profiles';
const CLASS_GROUPS_URL = '/api/v1/class-groups';
const USERS_URL = '/api/v1/users';

function page(content: StudentProfileResponse[]): PageResponse<StudentProfileResponse> {
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

const PROFILE: StudentProfileResponse = {
  publicId: 'p-1',
  userPublicId: 'u-1',
  firstName: 'Alice',
  lastName: 'Durand',
  studentNumber: 'ESIC-2026-0001',
  birthDate: '2004-05-10',
  workStudy: true,
  companyName: 'ACME',
  status: 'ACTIVE',
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
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

  /**
   * Chaque profil renvoyé déclenche une résolution de sa classe active
   * (`StudentProfileResponse` ne la porte pas). `PROFILE` (id `p-1`) est le
   * seul profil non vide utilisé dans cette suite.
   */
  function flushEnrollmentLookup(): void {
    http
      .expectOne((r) => r.url === '/api/v1/enrollments' && r.params.get('student') === 'p-1')
      .flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
  }

  it('requests the first page with the default sort (createdAt,desc) and shows a loading state', () => {
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('createdAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(text()).toContain('Chargement des apprenants');
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    req.flush(page([PROFILE]));
    flushEnrollmentLookup();
  });

  it('renders one row per profile with a keyboard-reachable link to the detail route', () => {
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();
    fixture.detectChanges();

    expect(text()).toContain('ESIC-2026-0001');
    expect(text()).toContain('ACME');
    const link = fixture.nativeElement.querySelector('a[href="/students/p-1"]') as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('Consulter');
  });

  it('shows the empty state when no profile matches', () => {
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun profil apprenant');
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
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();
    fixture.detectChanges();
    expect(text()).toContain('ESIC-2026-0001');
  });

  it('applies the student-number and status filters and resets to the first page', () => {
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();

    internals.filters.setValue({ q: '  ESIC-2026  ', status: 'ARCHIVED' });
    internals.applyFilters();

    const req = expectList();
    expect(req.request.params.get('q')).toBe('ESIC-2026');
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('only ever sends a whitelisted sort field', () => {
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();

    internals.onSortChange({ active: 'studentNumber', direction: 'asc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('studentNumber,asc');
    r1.flush(page([PROFILE]));
    flushEnrollmentLookup();

    // A field outside the back-end whitelist falls back to the default.
    internals.onSortChange({ active: 'companyName', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('createdAt,asc');
    r2.flush(page([PROFILE]));
    flushEnrollmentLookup();
  });

  it('requests the requested page and size on pagination', () => {
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([PROFILE]));
    flushEnrollmentLookup();
  });

  it('writes nothing to browser storage', () => {
    expectList().flush(page([PROFILE]));
    flushEnrollmentLookup();
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
                q: 'ESIC-2026',
                status: 'ARCHIVED',
                sort: 'studentNumber',
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

    const req = http.expectOne((r) => r.url === '/api/v1/student-profiles');
    expect(req.request.params.get('q')).toBe('ESIC-2026');
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('sort')).toBe('studentNumber,asc');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush({ content: [], page: 2, size: 50, totalElements: 130, totalPages: 3 });
    http.verify();
  });
});

/**
 * « Un apprenant sans classe reste un apprenant » (retour terrain) : un
 * profil sans inscription active reste visible avec un badge « Sans
 * classe » et une action pour lui en attribuer une, et — pour les rôles
 * qui gèrent les comptes — les comptes STUDENT sans profil apparaissent
 * dans un panneau dédié plutôt que de disparaître silencieusement.
 */
describe('StudentList — apprenants sans classe et comptes sans profil', () => {
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

  it('shows "Sans classe" and lets an admin assign one to a profile without active enrollment', () => {
    setup(['ADMIN']);
    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([PROFILE]));
    http.expectOne((r) => r.url === USERS_URL).flush({ content: [], page: 0, size: 500, totalElements: 0, totalPages: 0 });
    http
      .expectOne((r) => r.url === URL && r.params.get('size') === '500')
      .flush({ content: [PROFILE], page: 0, size: 500, totalElements: 1, totalPages: 1 });
    http
      .expectOne((r) => r.url === '/api/v1/enrollments' && r.params.get('student') === 'p-1')
      .flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(text()).toContain('Sans classe');

    internals.startAssign('p-1');
    fixture.detectChanges();
    internals.assignForm.controls.classGroupPublicId.setValue('c-1');
    internals.confirmAssign();

    const enrollReq = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/enrollments');
    expect(enrollReq.request.body).toEqual({ studentProfilePublicId: 'p-1', classGroupPublicId: 'c-1' });
    enrollReq.flush({});

    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([PROFILE]));
    http
      .expectOne((r) => r.url === '/api/v1/enrollments' && r.params.get('student') === 'p-1')
      .flush({ content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 });
  });

  it('lists STUDENT accounts without a profile for an admin, and completes one', () => {
    setup(['ADMIN']);
    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([]));
    http
      .expectOne((r) => r.url === USERS_URL)
      .flush({
        content: [{ publicId: 'u-orphan', email: 'orphan@esic.test', firstName: 'Sans', lastName: 'Profil', status: 'ACTIVE', roles: ['STUDENT'], createdAt: '', lastLoginAt: null }],
        page: 0,
        size: 500,
        totalElements: 1,
        totalPages: 1,
      });
    http
      .expectOne((r) => r.url === URL && r.params.get('size') === '500')
      .flush({ content: [], page: 0, size: 500, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();

    expect(text()).toContain('Comptes apprenants sans profil');
    expect(text()).toContain('orphan@esic.test');

    internals.startComplete('u-orphan');
    fixture.detectChanges();
    internals.completeForm.controls.classGroupPublicId.setValue('c-1');
    internals.confirmComplete();

    const profileReq = http.expectOne((r) => r.method === 'POST' && r.url === URL);
    expect(profileReq.request.body).toEqual({ userPublicId: 'u-orphan', studentNumber: null });
    profileReq.flush({ ...PROFILE, publicId: 'p-new', userPublicId: 'u-orphan' });

    const enrollReq = http.expectOne((r) => r.method === 'POST' && r.url === '/api/v1/enrollments');
    expect(enrollReq.request.body).toEqual({ studentProfilePublicId: 'p-new', classGroupPublicId: 'c-1' });
    enrollReq.flush({});

    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([]));
    http.expectOne((r) => r.url === USERS_URL).flush({ content: [], page: 0, size: 500, totalElements: 0, totalPages: 0 });
    http
      .expectOne((r) => r.url === URL && r.params.get('size') === '500')
      .flush({ content: [], page: 0, size: 500, totalElements: 0, totalPages: 0 });
  });

  it('does not attempt to load orphan accounts for a TEACHER (no access to GET /users)', () => {
    setup(['TEACHER']);
    http.expectOne((r) => r.url === URL && r.params.get('page') === '0').flush(page([]));
    // Aucune requête vers /api/v1/users : http.verify() (afterEach) le confirme.
  });
});
