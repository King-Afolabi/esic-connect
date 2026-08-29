import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import {
  AcademicResourceSlug,
  ClassGroupResponse,
  ProgramLevelResponse,
  ProgramResponse,
  PromotionResponse,
} from '../academic.models';
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

const LEVEL: ProgramLevelResponse = {
  publicId: 'lv-1',
  programPublicId: ID,
  code: 'BTS-SIO-1',
  name: 'Première année',
  sequenceNumber: 1,
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

const PROMOTION: PromotionResponse = {
  publicId: 'pm-1',
  programPublicId: ID,
  academicYearPublicId: 'ay-1',
  code: 'SIO-2026',
  name: 'Promotion 2026',
  startDate: null,
  endDate: null,
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

const CLASS_GROUP: ClassGroupResponse = {
  publicId: 'cg-1',
  promotionPublicId: 'pm-1',
  programLevelPublicId: 'lv-1',
  sitePublicId: null,
  code: 'SIO-2026-A',
  name: 'Groupe A',
  capacity: 24,
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

async function setup(resource: AcademicResourceSlug) {
  localStorage.clear();
  sessionStorage.clear();

  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: `academic/${resource}/:publicId`, component: AcademicReferenceDetail, data: { resource } },
        { path: `academic/${resource}`, component: Stub },
        { path: 'academic/program-levels/:publicId', component: Stub },
        { path: 'academic/promotions/:publicId', component: Stub },
        { path: 'academic/class-groups/:publicId', component: Stub },
        { path: 'dashboard', component: Stub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });

  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/academic/${resource}/${ID}`, AcademicReferenceDetail);
  harness.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  return {
    harness,
    http,
    text: () => harness.routeNativeElement?.textContent ?? '',
    oneReq: (url: string): TestRequest => http.expectOne(url),
    matchReq: (predicate: (url: string) => boolean): TestRequest =>
      http.expectOne((r) => predicate(r.url)),
  };
}

describe('AcademicReferenceDetail', () => {
  it('shows a loading state, then the record facts once loaded', async () => {
    const { harness, http, text, oneReq } = await setup('programs');
    expect(text()).toContain('Chargement de la fiche');

    oneReq(`/api/v1/programs/${ID}`).flush(PROGRAM);
    harness.detectChanges();

    http.expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`).flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
    http
      .expectOne((r) => r.url === '/api/v1/promotions')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    expect(text()).toContain('Formation BTS-SIO');
    expect(text()).toContain('Filière informatique');
    expect(text()).toContain('Actif');
    http.verify();
  });

  it('requests each child sub-list with a real filter and renders the rows', async () => {
    const { harness, http, text, oneReq } = await setup('programs');
    oneReq(`/api/v1/programs/${ID}`).flush(PROGRAM);
    harness.detectChanges();

    http.expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`).flush({
      content: [LEVEL],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    const promoReq = http.expectOne((r) => r.url === '/api/v1/promotions');
    expect(promoReq.request.params.get('program')).toBe(ID);
    promoReq.flush({ content: [PROMOTION], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();

    expect(text()).toContain('Niveaux de cette formation');
    expect(text()).toContain('BTS-SIO-1');
    expect(text()).toContain('SIO-2026');
    expect(
      harness.routeNativeElement?.querySelector('a[href="/academic/program-levels/lv-1"]'),
    ).not.toBeNull();
    http.verify();
  });

  it('shows the per-section empty message when a child sub-list is empty', async () => {
    const { harness, http, text, oneReq } = await setup('programs');
    oneReq(`/api/v1/programs/${ID}`).flush(PROGRAM);
    harness.detectChanges();
    http
      .expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`)
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http
      .expectOne((r) => r.url === '/api/v1/promotions')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    expect(text()).toContain("Aucun niveau n'est défini pour cette formation.");
    http.verify();
  });

  it('renders a not-found panel on a 404 and makes no child calls', async () => {
    const { harness, http, text, oneReq } = await setup('programs');
    oneReq(`/api/v1/programs/${ID}`).flush(
      { status: 404, code: 'PROGRAM_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();

    expect(text()).toContain('Aucun élément ne correspond à cet identifiant.');
    expect(harness.routeNativeElement?.querySelector('a[href="/academic/programs"]')).not.toBeNull();
    http.expectNone(() => true);
    http.verify();
  });

  it('renders an access-denied panel on a 403 (out of pedagogical scope)', async () => {
    const { harness, http, text, oneReq } = await setup('programs');
    oneReq(`/api/v1/programs/${ID}`).flush(
      { status: 403, code: 'ACAD_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    harness.detectChanges();

    expect(text()).toContain("Vous n'êtes pas autorisé à consulter cet élément");
    http.verify();
  });

  it('lets the user retry a child section after a failure', async () => {
    const { harness, http, text, oneReq } = await setup('programs');
    oneReq(`/api/v1/programs/${ID}`).flush(PROGRAM);
    harness.detectChanges();

    http
      .expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`)
      .flush(null, { status: 500, statusText: 'Server Error' });
    http
      .expectOne((r) => r.url === '/api/v1/promotions')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    expect(text()).toContain('Une erreur est survenue');
    const retry = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find((b) =>
      b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    http.expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`).flush({
      content: [LEVEL],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    harness.detectChanges();

    expect(text()).toContain('BTS-SIO-1');
    http.verify();
  });

  it('renders a leaf resource (class-groups) with parent links and no child calls', async () => {
    const { harness, http, text, oneReq } = await setup('class-groups');
    oneReq(`/api/v1/class-groups/${ID}`).flush({ ...CLASS_GROUP, publicId: ID });
    harness.detectChanges();

    expect(text()).toContain('Classe SIO-2026-A');
    expect(text()).toContain('24');
    expect(harness.routeNativeElement?.querySelector('a[href="/academic/promotions/pm-1"]')).not.toBeNull();
    http.expectNone(() => true);
    http.verify();
  });

  it('writes nothing to browser storage', async () => {
    const { harness, http, oneReq } = await setup('programs');
    oneReq(`/api/v1/programs/${ID}`).flush(PROGRAM);
    harness.detectChanges();
    http
      .expectOne((r) => r.url === `/api/v1/programs/${ID}/levels`)
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http
      .expectOne((r) => r.url === '/api/v1/promotions')
      .flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
});
