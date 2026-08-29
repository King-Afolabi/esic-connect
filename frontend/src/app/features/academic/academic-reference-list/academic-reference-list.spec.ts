import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { AcademicResourceSlug, PageResponse, ProgramResponse } from '../academic.models';
import { AcademicReferenceList } from './academic-reference-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string }) => void };
  applyFilters: () => void;
  resetFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
}

const PROGRAM: ProgramResponse = {
  publicId: 'pr-1',
  code: 'BTS-SIO',
  name: 'BTS Services informatiques aux organisations',
  programType: 'BTS',
  description: null,
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function setup(resource: AcademicResourceSlug, url: string) {
  localStorage.clear();
  sessionStorage.clear();

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { data: { resource } } } },
    ],
  });

  const fixture: ComponentFixture<AcademicReferenceList> =
    TestBed.createComponent(AcademicReferenceList);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as ListInternals;
  fixture.detectChanges();

  return {
    fixture,
    http,
    internals,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    expectList: (): TestRequest => http.expectOne((r) => r.url === url),
  };
}

describe('AcademicReferenceList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads the first page with the resource default sort and shows a loading state', () => {
    const { text, fixture, expectList } = setup('academic-years', '/api/v1/academic-years');
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('code,asc');
    expect(req.request.params.get('page')).toBe('0');
    expect(text()).toContain('Chargement');
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    req.flush(page([]));
  });

  it('renders one row per record with a keyboard-reachable link to the detail route', () => {
    const { fixture, text, expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(page([PROGRAM]));
    fixture.detectChanges();

    expect(text()).toContain('BTS-SIO');
    // programs config exposes a "Type" column
    expect(text()).toContain('BTS');
    const link = fixture.nativeElement.querySelector(
      'a[href="/academic/programs/pr-1"]',
    ) as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('Consulter');
  });

  it('shows the empty state when nothing matches', () => {
    const { fixture, text, expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun élément ne correspond');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('renders an access-denied panel on a 403 from the API', () => {
    const { fixture, text, expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(
      { status: 403, code: 'ACAD_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter ce référentiel");
  });

  it('shows a generic error with a retry that re-requests the list', () => {
    const { fixture, text, expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');

    const retry = [...fixture.nativeElement.querySelectorAll('button')].find((b: HTMLButtonElement) =>
      b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    expectList().flush(page([PROGRAM]));
    fixture.detectChanges();
    expect(text()).toContain('BTS-SIO');
  });

  it('applies the code/name and status filters and resets to the first page', () => {
    const { internals, expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(page([PROGRAM]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([PROGRAM]));

    internals.filters.setValue({ q: '  sio  ', status: 'ARCHIVED' });
    internals.applyFilters();

    const req = expectList();
    expect(req.request.params.get('q')).toBe('sio');
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('only ever sends a sort field from the resource whitelist', () => {
    const { internals, expectList } = setup('academic-years', '/api/v1/academic-years');
    expectList().flush(page([]));

    internals.onSortChange({ active: 'startDate', direction: 'desc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('startDate,desc');
    r1.flush(page([]));

    // A column outside the back-end whitelist falls back to the default field.
    internals.onSortChange({ active: 'archiveReason', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('code,asc');
    r2.flush(page([]));
  });

  it('requests the requested page and size on pagination', () => {
    const { internals, expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(page([PROGRAM]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([PROGRAM]));
  });

  it('writes nothing to browser storage', () => {
    const { expectList } = setup('programs', '/api/v1/programs');
    expectList().flush(page([PROGRAM]));
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
