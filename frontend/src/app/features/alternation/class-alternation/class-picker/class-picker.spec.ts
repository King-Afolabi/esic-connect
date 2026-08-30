import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ClassGroupResponse } from '../../../academic/academic.models';
import { ClassPicker } from './class-picker';

interface Internals {
  filters: { setValue: (v: { q: string }) => void };
  applyFilters: () => void;
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

  it('applies the q filter and resets to the first page', () => {
    expectList().flush(page([CLASS]));
    internals.onPageChange({ pageIndex: 2, pageSize: 20, length: 100 });
    expectList().flush(page([CLASS]));
    internals.filters.setValue({ q: '  BTS ' });
    internals.applyFilters();
    const req = expectList();
    expect(req.request.params.get('q')).toBe('BTS');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });
});
