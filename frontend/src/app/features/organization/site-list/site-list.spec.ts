import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { PageResponse, SiteResponse } from '../organization.models';
import { SiteList } from './site-list';

interface ListInternals {
  filters: { setValue: (v: { q: string; status: string }) => void };
  applyFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
}

const SITE: SiteResponse = {
  publicId: 's-1',
  code: 'PAR',
  name: 'Campus Paris',
  addressLine1: null,
  addressLine2: null,
  postalCode: null,
  city: 'Paris',
  countryCode: 'FR',
  timeZoneId: 'Europe/Paris',
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

function page<T>(content: T[]): PageResponse<T> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function setup(roles: Role[] = ['ADMIN']) {
  localStorage.clear();
  sessionStorage.clear();
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
  const fixture: ComponentFixture<SiteList> = TestBed.createComponent(SiteList);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as ListInternals;
  fixture.detectChanges();
  return {
    fixture,
    http,
    internals,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    expectList: (): TestRequest => http.expectOne((r) => r.url === '/api/v1/sites'),
  };
}

describe('SiteList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('loads the first page with the default sort and shows a loading state', () => {
    const { text, fixture, expectList } = setup();
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('code,asc');
    expect(req.request.params.get('page')).toBe('0');
    expect(text()).toContain('Chargement');
    expect(fixture.nativeElement.querySelector('mat-progress-bar')).not.toBeNull();
    req.flush(page([]));
  });

  it('renders one row per site with a keyboard-reachable link to the detail route', () => {
    const { fixture, text, expectList } = setup();
    expectList().flush(page([SITE]));
    fixture.detectChanges();
    expect(text()).toContain('PAR');
    expect(text()).toContain('Campus Paris');
    const link = fixture.nativeElement.querySelector(
      'a[href="/organization/sites/s-1"]',
    ) as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('Consulter');
  });

  it('shows the "Nouveau site" action for an ADMIN context and hides it for a PEDAGOGICAL_MANAGER', () => {
    const admin = setup(['ADMIN']);
    admin.expectList().flush(page([SITE]));
    admin.fixture.detectChanges();
    expect(admin.fixture.nativeElement.querySelector('a[href="/organization/sites/new"]')).not.toBeNull();

    const rp = setup(['PEDAGOGICAL_MANAGER']);
    rp.expectList().flush(page([SITE]));
    rp.fixture.detectChanges();
    expect(rp.fixture.nativeElement.querySelector('a[href="/organization/sites/new"]')).toBeNull();
  });

  it('shows the empty state when nothing matches', () => {
    const { fixture, text, expectList } = setup();
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun site ne correspond');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('renders an access-denied panel on a 403 from the API', () => {
    const { fixture, text, expectList } = setup();
    expectList().flush(
      { status: 403, code: 'ORG_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à consulter le référentiel des sites");
  });

  it('shows a generic error with a retry that re-requests the list', () => {
    const { fixture, text, expectList } = setup();
    expectList().flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');
    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(
      (b: HTMLButtonElement) => b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    expectList().flush(page([SITE]));
    fixture.detectChanges();
    expect(text()).toContain('PAR');
  });

  it('applies the code/name and status filters and resets to the first page', () => {
    const { internals, expectList } = setup();
    expectList().flush(page([SITE]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([SITE]));

    internals.filters.setValue({ q: '  par  ', status: 'ARCHIVED' });
    internals.applyFilters();

    const req = expectList();
    expect(req.request.params.get('q')).toBe('par');
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('only ever sends a sort field from the back-end whitelist', () => {
    const { internals, expectList } = setup();
    expectList().flush(page([]));
    internals.onSortChange({ active: 'name', direction: 'desc' });
    const r1 = expectList();
    expect(r1.request.params.get('sort')).toBe('name,desc');
    r1.flush(page([]));

    internals.onSortChange({ active: 'archiveReason', direction: 'asc' });
    const r2 = expectList();
    expect(r2.request.params.get('sort')).toBe('code,asc');
    r2.flush(page([]));
  });

  it('requests the requested page and size on pagination', () => {
    const { internals, expectList } = setup();
    expectList().flush(page([SITE]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([SITE]));
  });

  it('writes nothing to browser storage', () => {
    const { expectList } = setup();
    expectList().flush(page([SITE]));
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
