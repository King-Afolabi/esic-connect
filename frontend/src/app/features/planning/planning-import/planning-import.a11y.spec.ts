import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { PlanningImport } from './planning-import';

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur l'écran de
 * téléversement d'un planning (G1-B). Voir `src/testing/axe.ts`.
 */
describe('PlanningImport — accessibilité (axe-core)', () => {
  it("ne présente aucune violation axe-core une fois les classes chargées", async () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(PlanningImport);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === '/api/v1/class-groups').flush({
      content: [{ publicId: 'c-1', code: 'C1', name: 'Classe 1' }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    fixture.detectChanges();
    await expectNoAxeViolations(fixture.nativeElement);
    http.verify();
  });
});
