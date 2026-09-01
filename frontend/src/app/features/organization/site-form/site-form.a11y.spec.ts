import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { SiteForm } from './site-form';

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur le formulaire de
 * création d'un site (G1-A) — un formulaire administratif représentatif
 * du référentiel organisationnel. Voir `src/testing/axe.ts` pour le
 * périmètre.
 */
describe('SiteForm — accessibilité (axe-core)', () => {
  it("ne présente aucune violation axe-core en mode création", async () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: { mode: 'create' }, paramMap: { get: () => null } } },
        },
      ],
    });
    const fixture = TestBed.createComponent(SiteForm);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture.nativeElement);
  });
});
