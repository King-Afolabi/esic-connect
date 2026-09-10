import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { MyPlanning } from './my-planning';

const URL = '/api/v1/me/planning';

function setup() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });
  const fixture: ComponentFixture<MyPlanning> = TestBed.createComponent(MyPlanning);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http };
}

describe('MyPlanning', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('affiche les séances du formateur', () => {
    const { fixture, http } = setup();
    http.expectOne((r) => r.url === URL).flush({
      role: 'TEACHER',
      sessions: [
        {
          sessionPublicId: 's-1',
          title: 'Cours de rattrapage',
          status: 'PLANNED',
          startsAt: '2026-09-15T08:00:00Z',
          endsAt: '2026-09-15T10:00:00Z',
          timeZoneId: 'Europe/Paris',
          teacher: { publicId: 't-1', firstName: 'Awa', lastName: 'Diallo' },
          classes: [{ publicId: 'c-1', code: 'C1' }],
          roomCode: null,
        },
      ],
    });
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Cours de rattrapage');
    expect(text).toContain('C1');
  });

  it('affiche une liste vide sans erreur', () => {
    const { fixture, http } = setup();
    http.expectOne((r) => r.url === URL).flush({ role: 'STUDENT', sessions: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Aucune séance sur la période sélectionnée.');
  });

  it("affiche un message d'accès refusé sur un 403", () => {
    const { fixture, http } = setup();
    http.expectOne((r) => r.url === URL).flush(
      { code: 'FORBIDDEN', status: 403, message: 'Accès refusé', timestamp: '', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Vous n'êtes pas autorisé");
  });

  it('relance la requête au clic sur Filtrer', () => {
    const { fixture, http } = setup();
    http.expectOne((r) => r.url === URL).flush({ role: 'STUDENT', sessions: [] });
    fixture.detectChanges();

    fixture.componentInstance['filters'].patchValue({ from: '2026-09-01' });
    fixture.componentInstance['applyFilters']();
    const req = http.expectOne((r) => r.url === URL);
    expect(req.request.params.get('from')).toBe('2026-09-01T00:00:00.000Z');
    req.flush({ role: 'STUDENT', sessions: [] });
  });
});
