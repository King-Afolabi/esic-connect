import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { expectNoAxeViolations } from '../../../testing/axe';
import { MyPlanning } from './my-planning';

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur « Mon planning ».
 * Voir `src/testing/axe.ts` pour le périmètre.
 */
describe('MyPlanning — accessibilité (axe-core)', () => {
  it('ne présente aucune violation axe-core avec des séances affichées', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(MyPlanning);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === '/api/v1/me/planning').flush({
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
          roomCode: 'B12',
        },
      ],
    });
    fixture.detectChanges();
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('ne présente aucune violation axe-core quand la liste est vide', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(MyPlanning);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === '/api/v1/me/planning').flush({ role: 'STUDENT', sessions: [] });
    fixture.detectChanges();
    await expectNoAxeViolations(fixture.nativeElement);
  });
});
