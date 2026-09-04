import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MyTransparency } from './my-transparency';

const URL = '/api/v1/me/attendance/transparency';

const RECORDED = {
  occurredAt: '2026-09-10T08:05:00Z',
  event: 'RECORDED',
  actorRole: 'SELF',
  channel: 'SHORT_CODE',
  sessionPublicId: 's-1',
  sessionTitle: 'Atelier',
  sessionStartsAt: '2026-09-10T08:00:00Z',
  checkpointLabel: 'Arrivée',
  previousStatus: null,
  newStatus: 'PRESENT',
  previousLateMinutes: null,
  newLateMinutes: null,
  reason: null,
};

const CORRECTED = {
  occurredAt: '2026-09-10T09:00:00Z',
  event: 'STATUS_CORRECTED',
  actorRole: 'SCHOOL_ADMINISTRATION',
  channel: null,
  sessionPublicId: 's-1',
  sessionTitle: 'Atelier',
  sessionStartsAt: '2026-09-10T08:00:00Z',
  checkpointLabel: 'Arrivée',
  previousStatus: 'PRESENT',
  newStatus: 'LATE',
  previousLateMinutes: null,
  newLateMinutes: 20,
  reason: 'arrivée constatée à 9 h 20',
};

function page(content: unknown[]) {
  return { content, page: 0, size: 25, totalElements: content.length, totalPages: 1 };
}

function setup() {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture = TestBed.createComponent(MyTransparency);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http };
}

describe('MyTransparency', () => {
  let fixture: ComponentFixture<MyTransparency>;
  let http: HttpTestingController;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('shows the student their own émargement with its channel', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush(page([RECORDED]));
    fixture.detectChanges();

    expect(text()).toContain('Émargement enregistré');
    expect(text()).toContain('Code court');
    expect(text()).toContain('Vous');
    expect(text()).toContain('Atelier');
  });

  it('shows a correction as "avant → après" with its motive and the AUTHOR ROLE only', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush(page([CORRECTED]));
    fixture.detectChanges();

    expect(text()).toContain('Présence corrigée');
    expect(text()).toContain('20 min de retard');
    expect(text()).toContain('arrivée constatée à 9 h 20');
    // Le serveur ne transmet que la fonction ; l'écran ne peut donc pas
    // nommer un agent, et ne doit pas essayer.
    expect(text()).toContain("L'administration");
  });

  it('never sends a student identifier — the server resolves it from the JWT alone', () => {
    ({ fixture, http } = setup());
    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.keys()).not.toContain('studentPublicId');
    expect(request.request.params.keys()).not.toContain('enrollmentPublicId');
    request.flush(page([]));
    fixture.detectChanges();
    expect(text()).toContain('Aucun événement');
  });

  it('sends the date filters as instants and resets them', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush(page([]));
    fixture.detectChanges();

    const internals = fixture.componentInstance as unknown as {
      filters: { patchValue: (v: Record<string, unknown>) => void };
      applyFilters: () => void;
      resetFilters: () => void;
    };
    internals.filters.patchValue({ from: '2026-09-01' });
    internals.applyFilters();
    const filtered = http.expectOne((r) => r.url === URL);
    expect(filtered.request.params.get('from')).toContain('2026-09-01');
    filtered.flush(page([]));

    internals.resetFilters();
    const reset = http.expectOne((r) => r.url === URL);
    expect(reset.request.params.get('from')).toBeNull();
    reset.flush(page([]));
    fixture.detectChanges();
  });

  it('renders a controlled message on error, never the raw server body', () => {
    ({ fixture, http } = setup());
    http
      .expectOne((r) => r.url === URL)
      .flush({ message: 'stack trace interne' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(text()).not.toContain('stack trace interne');
    expect(text()).toContain('Réessayer');
  });

  it('stores nothing in the browser', () => {
    ({ fixture, http } = setup());
    http.expectOne((r) => r.url === URL).flush(page([RECORDED]));
    fixture.detectChanges();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
