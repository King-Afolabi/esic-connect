import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { expectNoAxeViolations } from '../../../../testing/axe';
import { ProgramResponse } from '../academic.models';
import { AcademicReferenceDetail } from './academic-reference-detail';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const ID = '2f1a9b7c-0000-4000-8000-000000000000';

const PROGRAM: ProgramResponse = {
  publicId: ID,
  code: 'BTS-SIO',
  name: 'BTS Services informatiques aux organisations',
  programType: 'BTS',
  description: 'Filière informatique',
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur la fiche
 * générique du référentiel académique — représentative des cinq
 * ressources qu'elle sert. Voir `src/testing/axe.ts` pour le périmètre.
 */
describe('AcademicReferenceDetail — accessibilité (axe-core)', () => {
  it('ne présente aucune violation axe-core sur la fiche d’une formation', async () => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'academic/programs/:publicId', component: AcademicReferenceDetail, data: { resource: 'programs' } },
          { path: 'academic/programs', component: Stub },
          { path: 'academic/program-levels/:publicId', component: Stub },
          { path: 'dashboard', component: Stub },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/academic/programs/${ID}`, AcademicReferenceDetail);
    harness.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`/api/v1/programs/${ID}`).flush(PROGRAM);
    harness.detectChanges();

    const emptyPage = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
    http.expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`).flush(emptyPage);
    http.expectOne((r) => r.url === '/api/v1/promotions').flush(emptyPage);
    harness.detectChanges();

    await expectNoAxeViolations(harness.routeNativeElement!);
    http.verify();
  });
});
