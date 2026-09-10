import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AcademicReferenceForm } from './academic-reference-form';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const ROUTES = [
  {
    path: 'academic/academic-years/new',
    data: { resource: 'academic-years', mode: 'create' },
    component: AcademicReferenceForm,
  },
  {
    path: 'academic/academic-years/:publicId/edit',
    data: { resource: 'academic-years', mode: 'edit' },
    component: AcademicReferenceForm,
  },
  {
    path: 'academic/academic-years/:publicId',
    component: Stub,
  },
  {
    path: 'academic/programs/new',
    data: { resource: 'programs', mode: 'create' },
    component: AcademicReferenceForm,
  },
  {
    path: 'academic/programs/:programPublicId/levels/new',
    data: { resource: 'program-levels', mode: 'create' },
    component: AcademicReferenceForm,
  },
  {
    path: 'academic/program-levels/:publicId',
    component: Stub,
  },
  {
    path: 'academic/programs/:publicId',
    component: Stub,
  },
  {
    path: 'academic/promotions/new',
    data: { resource: 'promotions', mode: 'create' },
    component: AcademicReferenceForm,
  },
  {
    path: 'academic/promotions/:publicId',
    component: Stub,
  },
  {
    path: 'academic/class-groups/new',
    data: { resource: 'class-groups', mode: 'create' },
    component: AcademicReferenceForm,
  },
  {
    path: 'academic/class-groups/:publicId',
    component: Stub,
  },
];

interface FormInternals {
  form: {
    invalid: boolean;
    get: (key: string) => { setValue: (v: unknown) => void; disabled: boolean } | null;
  };
  submit: () => void;
  submitError: () => string | null;
  onPromotionChange: (id: string) => void;
  programOptions: () => { value: string; label: string }[];
  levelOptions: () => { value: string; label: string }[];
}

async function setup(url: string) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter(ROUTES),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url, AcademicReferenceForm);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  const location = TestBed.inject(Location);
  const component = harness.routeDebugElement?.componentInstance as unknown as FormInternals;
  return { harness, http, location, component };
}

describe('AcademicReferenceForm — académic-years', () => {
  it('POSTs the trimmed fields and navigates to the detail on create', async () => {
    const { harness, http, location, component } = await setup('/academic/academic-years/new');
    component.form.get('code')?.setValue('AY-27');
    component.form.get('name')?.setValue('  2026-2027  ');
    component.form.get('startDate')?.setValue('2026-09-01');
    component.form.get('endDate')?.setValue('2027-06-30');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne('/api/v1/academic-years');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      code: 'AY-27',
      name: '2026-2027',
      startDate: '2026-09-01',
      endDate: '2027-06-30',
    });
    req.flush({
      publicId: 'y-1',
      code: 'AY-27',
      name: '2026-2027',
      startDate: '2026-09-01',
      endDate: '2027-06-30',
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    await harness.fixture.whenStable();
    expect(location.path()).toBe('/academic/academic-years/y-1');
    http.verify();
  });

  it('does not submit an invalid form', async () => {
    const { harness, http, component } = await setup('/academic/academic-years/new');
    component.form.get('name')?.setValue('X');
    harness.detectChanges();
    component.submit();
    http.expectNone('/api/v1/academic-years');
    http.verify();
  });

  it('loads the year, freezes the code and PATCHes the modifiable fields on edit', async () => {
    const { harness, http, location, component } = await setup(
      '/academic/academic-years/y-1/edit',
    );
    http.expectOne('/api/v1/academic-years/y-1').flush({
      publicId: 'y-1',
      code: 'AY-27',
      name: '2026-2027',
      startDate: '2026-09-01',
      endDate: '2027-06-30',
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    harness.detectChanges();
    expect(component.form.get('code')?.disabled).toBe(true);

    component.form.get('name')?.setValue('2026-2027 renommée');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne('/api/v1/academic-years/y-1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      name: '2026-2027 renommée',
      startDate: '2026-09-01',
      endDate: '2027-06-30',
    });
    req.flush({
      publicId: 'y-1',
      code: 'AY-27',
      name: '2026-2027 renommée',
      startDate: '2026-09-01',
      endDate: '2027-06-30',
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    await harness.fixture.whenStable();
    expect(location.path()).toBe('/academic/academic-years/y-1');
    http.verify();
  });
});

describe('AcademicReferenceForm — programs', () => {
  it('POSTs the program type with the other fields', async () => {
    const { harness, http, component } = await setup('/academic/programs/new');
    component.form.get('code')?.setValue('PRG-1');
    component.form.get('name')?.setValue('BTS SIO');
    component.form.get('programType')?.setValue('BTS');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne('/api/v1/programs');
    expect(req.request.body).toEqual({
      code: 'PRG-1',
      name: 'BTS SIO',
      programType: 'BTS',
      description: null,
    });
    req.flush({
      publicId: 'p-1',
      code: 'PRG-1',
      name: 'BTS SIO',
      programType: 'BTS',
      description: null,
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    http.verify();
  });
});

describe('AcademicReferenceForm — program-levels (nichés sous une formation)', () => {
  it('POSTs to /programs/{programPublicId}/levels', async () => {
    const { harness, http, component } = await setup('/academic/programs/p-1/levels/new');
    component.form.get('code')?.setValue('N1');
    component.form.get('name')?.setValue('1ère année');
    component.form.get('sequenceNumber')?.setValue(1);
    harness.detectChanges();
    component.submit();

    const req = http.expectOne('/api/v1/programs/p-1/levels');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'N1', name: '1ère année', sequenceNumber: 1 });
    req.flush({
      publicId: 'lvl-1',
      programPublicId: 'p-1',
      code: 'N1',
      name: '1ère année',
      sequenceNumber: 1,
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    http.verify();
  });
});

describe('AcademicReferenceForm — promotions (sélecteurs formation/année)', () => {
  it('loads program and academic-year options before showing the form, then POSTs the selection', async () => {
    const { harness, http, component } = await setup('/academic/promotions/new');

    http
      .expectOne((r) => r.url === '/api/v1/programs')
      .flush({
        content: [
          {
            publicId: 'p-1',
            code: 'PRG-1',
            name: 'BTS SIO',
            programType: 'BTS',
            description: null,
            status: 'ACTIVE',
            archivedAt: null,
            archiveReason: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
        page: 0,
        size: 200,
        totalElements: 1,
        totalPages: 1,
      });
    http
      .expectOne((r) => r.url === '/api/v1/academic-years')
      .flush({
        content: [
          {
            publicId: 'y-1',
            code: 'AY-27',
            name: '2026-2027',
            startDate: '2026-09-01',
            endDate: '2027-06-30',
            status: 'ACTIVE',
            archivedAt: null,
            archiveReason: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
        page: 0,
        size: 200,
        totalElements: 1,
        totalPages: 1,
      });
    harness.detectChanges();

    expect(component.programOptions()).toEqual([{ value: 'p-1', label: 'PRG-1 — BTS SIO' }]);

    component.form.get('programPublicId')?.setValue('p-1');
    component.form.get('academicYearPublicId')?.setValue('y-1');
    component.form.get('code')?.setValue('PROMO-1');
    component.form.get('name')?.setValue('Promo A');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne('/api/v1/promotions');
    expect(req.request.body).toEqual({
      programPublicId: 'p-1',
      academicYearPublicId: 'y-1',
      code: 'PROMO-1',
      name: 'Promo A',
      startDate: null,
      endDate: null,
    });
    req.flush({
      publicId: 'promo-1',
      programPublicId: 'p-1',
      academicYearPublicId: 'y-1',
      code: 'PROMO-1',
      name: 'Promo A',
      startDate: null,
      endDate: null,
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '',
      updatedAt: '',
    });
    http.verify();
  });
});

describe('AcademicReferenceForm — class-groups (niveaux dépendants de la promotion choisie)', () => {
  it('loads levels for the selected promotion, then POSTs the full selection', async () => {
    const { harness, http, component } = await setup('/academic/class-groups/new');

    http
      .expectOne((r) => r.url === '/api/v1/promotions')
      .flush({
        content: [
          {
            publicId: 'promo-1',
            programPublicId: 'p-1',
            academicYearPublicId: 'y-1',
            code: 'PROMO-1',
            name: 'Promo A',
            startDate: null,
            endDate: null,
            status: 'ACTIVE',
            archivedAt: null,
            archiveReason: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
        page: 0,
        size: 200,
        totalElements: 1,
        totalPages: 1,
      });
    http
      .expectOne((r) => r.url === '/api/v1/sites')
      .flush({
        content: [{ publicId: 'site-1', name: 'Campus Paris', code: 'PAR' }],
        page: 0,
        size: 200,
        totalElements: 1,
        totalPages: 1,
      });
    harness.detectChanges();

    component.onPromotionChange('promo-1');
    http
      .expectOne((r) => r.url === '/api/v1/programs/p-1/levels')
      .flush({
        content: [
          {
            publicId: 'lvl-1',
            programPublicId: 'p-1',
            code: 'N1',
            name: '1ère année',
            sequenceNumber: 1,
            status: 'ACTIVE',
            archivedAt: null,
            archiveReason: null,
            createdAt: '',
            updatedAt: '',
          },
        ],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    harness.detectChanges();
    expect(component.levelOptions()).toEqual([{ value: 'lvl-1', label: 'N1 — 1ère année' }]);

    component.form.get('promotionPublicId')?.setValue('promo-1');
    component.form.get('programLevelPublicId')?.setValue('lvl-1');
    component.form.get('sitePublicId')?.setValue('site-1');
    component.form.get('code')?.setValue('CLASSE-A');
    component.form.get('name')?.setValue('Classe A');
    component.form.get('capacity')?.setValue(30);
    harness.detectChanges();
    component.submit();

    const req = http.expectOne('/api/v1/class-groups');
    expect(req.request.body).toEqual({
      promotionPublicId: 'promo-1',
      programLevelPublicId: 'lvl-1',
      sitePublicId: 'site-1',
      code: 'CLASSE-A',
      name: 'Classe A',
      capacity: 30,
    });
    req.flush({
      publicId: 'class-1',
      promotionPublicId: 'promo-1',
      programLevelPublicId: 'lvl-1',
      sitePublicId: 'site-1',
      code: 'CLASSE-A',
      name: 'Classe A',
      capacity: 30,
      status: 'ACTIVE',
      archivedAt: null,
      archiveReason: null,
      createdAt: '',
      updatedAt: '',
    });
    http.verify();
  });
});
