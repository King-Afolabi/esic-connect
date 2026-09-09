import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { ClaimList } from './claim-list';

const URL = '/api/v1/claims';

const CLAIM = {
  publicId: 'c-1',
  authorPublicId: 'u-1',
  category: 'ATTENDANCE',
  subject: 'Absence du 10 septembre',
  status: 'OPEN',
  audience: 'PEDAGOGICAL_MANAGER',
  sessionPublicId: null,
  classGroupPublicId: null,
  periodStart: null,
  periodEnd: null,
  closedAt: null,
  createdAt: '2026-09-10T08:00:00Z',
  updatedAt: '2026-09-10T08:00:00Z',
};

interface Internals {
  toggleCreate: () => void;
  form: { patchValue: (v: Record<string, unknown>) => void };
  submit: () => void;
}

function page(content: unknown[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function setup(roles: Role[] = ['STUDENT']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  });
  const fixture = TestBed.createComponent(ClaimList);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http, internals: fixture.componentInstance as unknown as Internals };
}

describe('ClaimList', () => {
  let fixture: ComponentFixture<ClaimList>;
  let http: HttpTestingController;
  let internals: Internals;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('lists the claims the server chose to return, with their guichet', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush(page([CLAIM]));
    fixture.detectChanges();

    expect(text()).toContain('Absence du 10 septembre');
    expect(text()).toContain('Responsable pédagogique');
    expect(text()).toContain('Ouverte');
  });

  it('opens a claim addressed to a guichet, never to a person', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne((r) => r.url === URL).flush(page([]));
    fixture.detectChanges();

    internals.toggleCreate();
    internals.form.patchValue({
      category: 'ATTENDANCE',
      audience: 'TEACHER',
      subject: 'Présence non enregistrée',
      description: "J'ai bien émargé.",
    });
    internals.submit();

    const created = http.expectOne((r) => r.url === URL && r.method === 'POST');
    expect(created.request.body.audience).toBe('TEACHER');
    // Aucun destinataire nominatif n'existe dans le contrat : c'est ce
    // qui rend le transfert possible (docs/02 §20.3).
    expect(created.request.body).not.toHaveProperty('recipientPublicId');
    expect(created.request.body).not.toHaveProperty('authorPublicId');
    created.flush(CLAIM, { status: 201, statusText: 'Created' });

    http.expectOne((r) => r.url === URL && r.method === 'GET').flush(page([CLAIM]));
    fixture.detectChanges();
    expect(text()).toContain('Absence du 10 septembre');
  });

  it('refuses to submit an incomplete claim', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne((r) => r.url === URL).flush(page([]));
    fixture.detectChanges();

    internals.toggleCreate();
    internals.form.patchValue({ subject: '', description: '' });
    internals.submit();
    http.expectNone((r) => r.method === 'POST');
  });

  it('surfaces the server refusal when the author has no scope for the guichet', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne((r) => r.url === URL).flush(page([]));
    fixture.detectChanges();

    internals.toggleCreate();
    internals.form.patchValue({
      subject: 'Sujet',
      description: 'Description',
      audience: 'TEACHER',
    });
    internals.submit();
    http.expectOne((r) => r.method === 'POST').flush(
      {
        timestamp: '2026-09-10T08:00:00Z',
        status: 409,
        code: 'CLAIM_NO_SCOPE_FOR_AUDIENCE',
        message: "Sans classe active, adressez votre réclamation à l'administration scolaire.",
        path: URL,
        correlationId: null,
        details: [],
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(text()).toContain("adressez votre réclamation à l'administration scolaire");
  });

  it('never displays a raw 5xx body', () => {
    ({ fixture, http } = setup());
    http
      .expectOne((r) => r.url === URL)
      .flush({ message: 'stack interne' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).not.toContain('stack interne');
    expect(text()).toContain('Réessayer');
  });

  it('stores nothing in the browser', () => {
    ({ fixture, http } = setup(['PEDAGOGICAL_MANAGER']));
    http.expectOne((r) => r.url === URL).flush(page([CLAIM]));
    fixture.detectChanges();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
