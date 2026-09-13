import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ClassGroupResponse } from '../../../academic/academic.models';
import { ClassPicker } from './class-picker';

interface Internals {
  filters: { setValue: (v: { q: string }) => void };
  onPageChange: (e: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
}

const URL = '/api/v1/class-groups';

const CLASS: ClassGroupResponse = {
  publicId: 'c-1',
  promotionPublicId: 'pr-1',
  programLevelPublicId: 'lv-1',
  sitePublicId: null,
  code: 'BTS-SIO-1-A',
  name: 'BTS SIO 1 A',
  academicYearPublicId: 'ay-1',
  academicYearCode: 'AY-2026',
  capacity: 24,
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

function page(content: ClassGroupResponse[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

describe('ClassPicker', () => {
  let fixture: ComponentFixture<ClassPicker>;
  let http: HttpTestingController;
  let internals: Internals;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ClassPicker);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as Internals;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const expectList = (): TestRequest => http.expectOne((r) => r.url === URL);

  it('lists classes from the academic class-groups endpoint with a link to the class screen', () => {
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('code,asc');
    req.flush(page([CLASS]));
    fixture.detectChanges();
    expect(text()).toContain('BTS-SIO-1-A');
    expect(fixture.nativeElement.querySelector('a[href="/alternation/classes/c-1"]')).not.toBeNull();
  });

  it('makes the whole row clickable (Lot 2) toward the same destination as "Gérer le rythme"', () => {
    expectList().flush(page([CLASS]));
    fixture.detectChanges();

    const row = fixture.nativeElement.querySelector('tr[mat-row]') as HTMLTableRowElement;
    const link = fixture.nativeElement.querySelector(
      'a[href="/alternation/classes/c-1"]',
    ) as HTMLAnchorElement;
    expect(row.getAttribute('tabindex')).toBe('0');

    const linkClickSpy = vi.spyOn(link, 'click').mockImplementation(() => {});
    const cell = row.querySelector('td') as HTMLElement;
    cell.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(linkClickSpy).toHaveBeenCalledTimes(1);
  });

  it('shows the empty state', () => {
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucune classe ne correspond');
  });

  it('shows an access-denied panel on 403', () => {
    expectList().flush(
      { status: 403, code: 'ACAD_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter les classes");
  });

  it('searches live as the user types (Lot 4), debounced, and resets to the first page', () => {
    vi.useFakeTimers();
    expectList().flush(page([CLASS]));
    internals.onPageChange({ pageIndex: 2, pageSize: 20, length: 100 });
    expectList().flush(page([CLASS]));

    internals.filters.setValue({ q: '  BTS ' });
    // Aucune requête avant la fin du délai (Lot 4 : recherche différée).
    http.expectNone((r) => r.url === URL);
    vi.advanceTimersByTime(300);
    vi.useRealTimers();

    const req = expectList();
    expect(req.request.params.get('q')).toBe('BTS');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });
});
