import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { CourseSessionResponse, PageResponse } from '../sessions.models';
import { SessionList } from './session-list';

interface ListInternals {
  filters: { setValue: (v: { status: string }) => void };
  applyFilters: () => void;
  resetFilters: () => void;
  onSortChange: (sort: { active: string; direction: 'asc' | 'desc' | '' }) => void;
  onPageChange: (event: { pageIndex: number; pageSize: number; length: number }) => void;
  retry: () => void;
}

const URL = '/api/v1/sessions';

const SESSION: CourseSessionResponse = {
  publicId: 's-1',
  status: 'OPEN',
  title: 'Rattrapage',
  exceptionReason: 'séance exceptionnelle',
  teacher: { publicId: 't-1', firstName: 'Alice', lastName: 'Martin' },
  classes: [{ publicId: 'c-1', code: 'BTS-SIO-1' }],
  startsAt: '2026-09-10T06:00:00Z',
  endsAt: '2026-09-10T10:00:00Z',
  timeZoneId: 'Europe/Paris',
  openedAt: '2026-09-10T05:55:00Z',
  closedAt: null,
  checkpointPublicId: 'cp-1',
  checkpointOpen: true,
  createdAt: '2026-09-01T10:00:00Z',
  updatedAt: '2026-09-10T05:55:00Z',
};

function page(content: CourseSessionResponse[]): PageResponse<CourseSessionResponse> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function setup(canCreate: boolean) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(
    canCreate ? (['ADMIN'] as Role[]) : (['TEACHER'] as Role[]),
  );
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  });
  const fixture = TestBed.createComponent(SessionList);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as ListInternals;
  fixture.detectChanges();
  return { fixture, http, internals, effectiveRoles };
}

describe('SessionList', () => {
  let fixture: ComponentFixture<SessionList>;
  let http: HttpTestingController;
  let internals: ListInternals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const expectList = (): TestRequest => http.expectOne((r) => r.url === URL);

  afterEach(() => http.verify());

  it('requests the first page with the default sort startsAt,desc and shows a loading state', () => {
    ({ fixture, http, internals } = setup(true));
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('startsAt,desc');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    expect(text()).toContain('Chargement des séances');
    req.flush(page([SESSION]));
  });

  it('renders one row per session with a keyboard-reachable detail link', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([SESSION]));
    fixture.detectChanges();
    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/sessions/s-1"]',
    ) as HTMLAnchorElement;
    expect(link).not.toBeNull();
    expect(link.getAttribute('aria-label')).toContain('Consulter la séance');
    expect(text()).toContain('Alice Martin');
    expect(text()).toContain('BTS-SIO-1');
    expect(text()).toContain('Ouverte');
  });

  it('shows the empty state when there is no session', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucune séance ne correspond');
  });

  it('renders a forbidden panel on a 403 API response', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(
      { timestamp: 't', status: 403, code: 'SESSION_OPERATION_FORBIDDEN', message: 'x', path: '/', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé");
  });

  it('shows a generic error with a working Réessayer button', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');
    internals.retry();
    expectList().flush(page([SESSION]));
  });

  it('applies the status filter and resets to page 0', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([SESSION]));
    internals.onPageChange({ pageIndex: 3, pageSize: 20, length: 100 });
    expectList().flush(page([SESSION]));

    internals.filters.setValue({ status: 'CLOSED' });
    internals.applyFilters();
    const req = expectList();
    expect(req.request.params.get('status')).toBe('CLOSED');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([]));
  });

  it('keeps the sort field within the whitelist, falling back to the default', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([SESSION]));
    internals.onSortChange({ active: 'teacher', direction: 'asc' });
    const req = expectList();
    expect(req.request.params.get('sort')).toBe('startsAt,asc');
    req.flush(page([SESSION]));
  });

  it('passes page and size on pagination', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([SESSION]));
    internals.onPageChange({ pageIndex: 2, pageSize: 50, length: 200 });
    const req = expectList();
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('50');
    req.flush(page([SESSION]));
  });

  it('hides the "Nouvelle séance" link for a non-create role and stores nothing', () => {
    ({ fixture, http, internals } = setup(false));
    expectList().flush(page([SESSION]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions/new"]')).toBeNull();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('shows the "Nouvelle séance" link for a create role', () => {
    ({ fixture, http, internals } = setup(true));
    expectList().flush(page([SESSION]));
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions/new"]'),
    ).not.toBeNull();
  });

  it('hides the create link when the active role context drops the create role', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(true));
    expectList().flush(page([SESSION]));
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions/new"]'),
    ).not.toBeNull();

    // L'utilisateur cumule ADMIN + TEACHER mais bascule sur le contexte TEACHER.
    effectiveRoles.set(['TEACHER']);
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions/new"]'),
    ).toBeNull();
  });
});
