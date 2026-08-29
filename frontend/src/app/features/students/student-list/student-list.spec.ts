import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { PageResponse, StudentProfileResponse } from '../students.models';
import { StudentList } from './student-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string }) => void };
  applyFilters: () => void;
  resetFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
}

const URL = '/api/v1/student-profiles';

function page(content: StudentProfileResponse[]): PageResponse<StudentProfileResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

const PROFILE: StudentProfileResponse = {
  publicId: 'p-1',
  userPublicId: 'u-1',
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
    req.flush(page([PROFILE]));
  });

  it('renders one row per profile with a keyboard-reachable link to the detail route', () => {
    expectList().flush(page([PROFILE]));
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
    fixture.detectChanges();
    expect(text()).toContain('ESIC-2026-0001');
  });

  it('applies the student-number and status filters and resets to the first page', () => {
    expectList().flush(page([PROFILE]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([PROFILE]));

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

    internals.onSortChange({ active: 'studentNumber', direction: 'asc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('studentNumber,asc');
    r1.flush(page([PROFILE]));

    // A field outside the back-end whitelist falls back to the default.
    internals.onSortChange({ active: 'companyName', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('createdAt,asc');
    r2.flush(page([PROFILE]));
  });

  it('requests the requested page and size on pagination', () => {
    expectList().flush(page([PROFILE]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([PROFILE]));
  });

  it('writes nothing to browser storage', () => {
    expectList().flush(page([PROFILE]));
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
