import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { expectNoAxeViolations } from '../../../../testing/axe';
import { AcademicReferenceForm } from './academic-reference-form';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const ROUTES = [
  {
    path: 'academic/academic-years/new',
    data: { resource: 'academic-years', mode: 'create' },
    component: AcademicReferenceForm,
  },
  { path: 'academic/academic-years/:publicId', component: Stub },
];

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur le formulaire
 * générique du référentiel académique — représentatif des cinq
 * ressources qu'il sert (années, formations, niveaux, promotions,
 * classes). Voir `src/testing/axe.ts` pour le périmètre.
 */
describe('AcademicReferenceForm — accessibilité (axe-core)', () => {
  it("ne présente aucune violation axe-core en mode création (années académiques)", async () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter(ROUTES), provideHttpClient(), provideHttpClientTesting()],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/academic/academic-years/new', AcademicReferenceForm);
    harness.detectChanges();
    await expectNoAxeViolations(harness.routeNativeElement!);
  });
});
