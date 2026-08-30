import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationService } from '../../../../core/notifications/notification.service';
import { WorkStudyPatternResponse } from '../../alternation.models';
import { PatternForm } from './pattern-form';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const CREATE_URL = '/api/v1/alternation/patterns';
const ID = '2f1a9b7c-0000-4000-8000-000000000000';
const notifications = { info: vi.fn(), error: vi.fn() };

interface FormInternals {
  form: {
    controls: {
      code: { setValue: (v: string) => void; disabled: boolean; hasError: (k: string) => boolean };
      name: { setValue: (v: string) => void };
      type: { setValue: (v: string) => void; disabled: boolean };
      customCompanyDays: { setValue: (v: string[]) => void };
      threeTwo: { setValue: (v: Record<string, string>) => void };
      customWeekRoles: { setValue: (v: string[]) => void };
    };
  };
  submit: () => void;
  built: () => { configuration: Record<string, unknown>; errors: string[] };
}

async function setup(mode: 'create' | 'edit') {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        {
          path: 'alternation/patterns/new',
          data: { mode: 'create' },
          component: PatternForm,
        },
        {
          path: 'alternation/patterns/:publicId/edit',
          data: { mode: 'edit' },
          component: PatternForm,
        },
        { path: 'alternation/patterns/:publicId', component: Stub },
        { path: 'alternation/patterns', component: Stub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
    ],
  });
  const harness = await RouterTestingHarness.create();
  const url =
    mode === 'create' ? '/alternation/patterns/new' : `/alternation/patterns/${ID}/edit`;
  await harness.navigateByUrl(url, PatternForm);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  const location = TestBed.inject(Location);
  const component = harness.routeDebugElement?.componentInstance as unknown as FormInternals;
  return { harness, http, location, component };
}

describe('PatternForm (create)', () => {
  it('POSTs a 3j/2j configuration with explicit day arrays and navigates to the detail', async () => {
    const { harness, http, location, component } = await setup('create');
    component.form.controls.code.setValue('RY-1');
    component.form.controls.name.setValue('Trois jours');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne(CREATE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({
      code: 'RY-1',
      name: 'Trois jours',
      type: 'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY',
      configuration: {
        schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY'],
        companyDays: ['THURSDAY', 'FRIDAY'],
      },
    });
    expect(req.request.body.cycleLengthWeeks).toBeUndefined();
    const created: WorkStudyPatternResponse = {
      publicId: 'p-9',
      code: 'RY-1',
      name: 'Trois jours',
      description: null,
      type: 'THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY',
      cycleLengthWeeks: 1,
      configuration: {},
      status: 'ACTIVE',
      archiveReason: null,
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-08-01T00:00:00Z',
    };
    req.flush(created);
    await harness.fixture.whenStable();
    expect(location.path()).toBe('/alternation/patterns/p-9');
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });

  it('sends companyDays explicitly (even empty) for a CUSTOM pattern', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('RY-C');
    component.form.controls.name.setValue('Custom');
    component.form.controls.type.setValue('CUSTOM');
    harness.detectChanges();
    component.form.controls.customCompanyDays.setValue([]);
    harness.detectChanges();
    component.submit();

    const req = http.expectOne(CREATE_URL);
    expect(req.request.body.type).toBe('CUSTOM');
    expect(req.request.body.configuration).toMatchObject({ companyDays: [] });
    expect(Object.keys(req.request.body.configuration as object).sort()).toEqual([
      'companyDays',
      'cycleLengthWeeks',
      'companyWeeks',
      'schoolDays',
      'schoolWeeks',
    ].sort());
    req.flush({ publicId: 'p-c' });
    http.verify();
  });

  it('does not submit while a client-side configuration error stands', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('RY-BAD');
    component.form.controls.name.setValue('Bad');
    // All weekdays school → 3j/2j classification incomplete.
    component.form.controls.threeTwo.setValue({
      MONDAY: 'SCHOOL',
      TUESDAY: 'SCHOOL',
      WEDNESDAY: 'SCHOOL',
      THURSDAY: 'SCHOOL',
      FRIDAY: 'SCHOOL',
    });
    harness.detectChanges();
    expect(component.built().errors.length).toBeGreaterThan(0);
    component.submit();
    http.expectNone(CREATE_URL);
  });

  it('binds ALT_DUPLICATE_CODE to the code field', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('RY-DUP');
    component.form.controls.name.setValue('Dup');
    harness.detectChanges();
    component.submit();
    http.expectOne(CREATE_URL).flush(
      { status: 409, code: 'ALT_DUPLICATE_CODE', message: 'Code déjà utilisé.', path: '', correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    harness.detectChanges();
    expect(component.form.controls.code.hasError('server')).toBe(true);
    http.verify();
  });

  it('prevents a double submit', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('RY-2');
    component.form.controls.name.setValue('Deux');
    harness.detectChanges();
    component.submit();
    component.submit();
    http.expectOne(CREATE_URL).flush({ publicId: 'p-2' });
    http.verify();
  });
});

describe('PatternForm (edit)', () => {
  const EXISTING: WorkStudyPatternResponse = {
    publicId: ID,
    code: 'RY-EDIT',
    name: 'À modifier',
    description: null,
    type: 'ONE_WEEK_SCHOOL_OUT_OF_FOUR',
    cycleLengthWeeks: 4,
    configuration: {
      cycleLengthWeeks: 4,
      schoolWeeks: [1],
      companyWeeks: [2, 3, 4],
      schoolDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
      companyDays: [],
    },
    status: 'ACTIVE',
    archiveReason: null,
    createdAt: '2026-08-01T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  };

  it('loads the pattern, freezes code + type and PATCHes name + configuration', async () => {
    const { harness, http, location, component } = await setup('edit');
    const load: TestRequest = http.expectOne(`/api/v1/alternation/patterns/${ID}`);
    expect(load.request.method).toBe('GET');
    load.flush(EXISTING);
    harness.detectChanges();

    expect(component.form.controls.code.disabled).toBe(true);
    expect(component.form.controls.type.disabled).toBe(true);

    component.form.controls.name.setValue('Nouveau nom');
    harness.detectChanges();
    component.submit();

    const patch = http.expectOne(`/api/v1/alternation/patterns/${ID}`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toMatchObject({ name: 'Nouveau nom' });
    expect(patch.request.body.code).toBeUndefined();
    expect(patch.request.body.type).toBeUndefined();
    patch.flush(EXISTING);
    await harness.fixture.whenStable();
    expect(location.path()).toBe(`/alternation/patterns/${ID}`);
    http.verify();
  });

  it('shows a not-found state when the pattern is missing', async () => {
    const { harness, http } = await setup('edit');
    http.expectOne(`/api/v1/alternation/patterns/${ID}`).flush(
      { status: 404, code: 'ALT_PATTERN_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('Aucun modèle de rythme');
    http.verify();
  });
});
