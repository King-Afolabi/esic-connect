import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { PageResponse, WorkStudyPatternResponse } from '../../alternation.models';
import { PatternList } from './pattern-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string; type: string }) => void };
  applyFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
}

const URL = '/api/v1/alternation/patterns';

const PATTERN: WorkStudyPatternResponse = {
  publicId: 'p-1',
  code: 'RY-3-2',
  name: 'Trois jours école',
  description: null,
  type: 'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY',
  cycleLengthWeeks: 1,
  configuration: {},
  status: 'ACTIVE',
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-02T10:00:00Z',
};

function page(content: WorkStudyPatternResponse[]): PageResponse<WorkStudyPatternResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function setup(canWrite: boolean) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { hasAnyRole: () => canWrite } },
    ],
  });
  const fixture = TestBed.createComponent(PatternList);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as ListInternals;
  fixture.detectChanges();
  return { fixture, http, internals };
}

describe('PatternList', () => {
  let fixture: ComponentFixture<PatternList>;
  let http: HttpTestingController;
  let internals: ListInternals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const expectList = (): TestRequest => http.expectOne((r) => r.url === URL);

  afterEach(() => http.verify());

  it('requests the first page with the default sort code,asc and shows a loading state', () => {
    ({ fixture, http, internals } = setup(true));
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('code,asc');
    expect(req.request.params.get('page')).toBe('0');
    expect(text()).toContain('Chargement des modèles de rythme');
    req.flush(page([PATTERN]));
  });

  it('renders one row per pattern with a link to the detail route', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([PATTERN]));
    fixture.detectChanges();
    expect(text()).toContain('RY-3-2');
    expect(text()).toContain('3 jours école / 2 jours entreprise');
    expect(fixture.nativeElement.querySelector('a[href="/alternation/patterns/p-1"]')).not.toBeNull();
  });

  it('shows the "Nouveau modèle" button for a write role', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([PATTERN]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a[href="/alternation/patterns/new"]')).not.toBeNull();
  });

  it('hides the "Nouveau modèle" button for a read-only role', () => {
    ({ fixture, http, internals } = setup(false));
    expectList().flush(page([PATTERN]));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a[href="/alternation/patterns/new"]')).toBeNull();
  });

  it('shows the empty state when nothing matches', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun modèle de rythme');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('renders an access-denied panel on a 403', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(
      { status: 403, code: 'ALT_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé");
  });

  it('shows a generic error with a retry that re-requests', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');
    internals.retry();
    expectList().flush(page([PATTERN]));
    fixture.detectChanges();
    expect(text()).toContain('RY-3-2');
  });

  it('applies the q / status / type filters and resets to the first page', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([PATTERN]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([PATTERN]));

    internals.filters.setValue({ q: '  RY  ', status: 'ARCHIVED', type: 'CUSTOM' });
    internals.applyFilters();
    const req = expectList();
    expect(req.request.params.get('q')).toBe('RY');
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('type')).toBe('CUSTOM');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('only ever sends a whitelisted sort field', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([PATTERN]));
    internals.onSortChange({ active: 'updatedAt', direction: 'desc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('updatedAt,desc');
    r1.flush(page([PATTERN]));
    internals.onSortChange({ active: 'configuration', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('code,asc');
    r2.flush(page([PATTERN]));
  });

  it('passes page and size on pagination', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([PATTERN]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([PATTERN]));
  });
});
