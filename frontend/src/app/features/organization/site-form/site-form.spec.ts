import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationService } from '../../../core/notifications/notification.service';
import { SiteResponse } from '../organization.models';
import { SiteForm } from './site-form';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const CREATE_URL = '/api/v1/sites';
const ID = '2f1a9b7c-0000-4000-8000-000000000000';
const notifications = { info: vi.fn(), error: vi.fn() };

interface FormInternals {
  form: {
    invalid: boolean;
    controls: {
      code: { setValue: (v: string) => void; disabled: boolean; hasError: (k: string) => boolean };
      name: { setValue: (v: string) => void };
      timeZoneId: { setValue: (v: string) => void; hasError: (k: string) => boolean };
    };
  };
  submit: () => void;
  submitError: () => string | null;
}

const SITE: SiteResponse = {
  publicId: ID,
  code: 'PAR',
  name: 'Campus Paris',
  addressLine1: '1 rue A',
  addressLine2: null,
  postalCode: '75001',
  city: 'Paris',
  countryCode: 'FR',
  timeZoneId: 'Europe/Paris',
  status: 'ACTIVE',
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

async function setup(mode: 'create' | 'edit') {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'organization/sites/new', data: { mode: 'create' }, component: SiteForm },
        { path: 'organization/sites/:publicId/edit', data: { mode: 'edit' }, component: SiteForm },
        { path: 'organization/sites/:publicId', component: Stub },
        { path: 'organization/sites', component: Stub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
    ],
  });
  const harness = await RouterTestingHarness.create();
  const url =
    mode === 'create' ? '/organization/sites/new' : `/organization/sites/${ID}/edit`;
  await harness.navigateByUrl(url, SiteForm);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  const location = TestBed.inject(Location);
  const component = harness.routeDebugElement?.componentInstance as unknown as FormInternals;
  return { harness, http, location, component };
}

describe('SiteForm (create)', () => {
  it('POSTs the trimmed fields, nulls the empty optionals and navigates to the detail', async () => {
    const { harness, http, location, component } = await setup('create');
    component.form.controls.code.setValue('PAR');
    component.form.controls.name.setValue('  Campus Paris  ');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne(CREATE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      code: 'PAR',
      name: 'Campus Paris',
      addressLine1: null,
      addressLine2: null,
      postalCode: null,
      city: null,
      countryCode: null,
      timeZoneId: 'Europe/Paris',
    });
    req.flush({ ...SITE, publicId: 's-9' });
    await harness.fixture.whenStable();
    expect(location.path()).toBe('/organization/sites/s-9');
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });

  it('does not submit an invalid form', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.name.setValue('X');
    harness.detectChanges();
    component.submit();
    http.expectNone(CREATE_URL);
    http.verify();
  });

  it('binds ORG_DUPLICATE_CODE to the code field and shows a global message', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('PAR');
    component.form.controls.name.setValue('Campus');
    harness.detectChanges();
    component.submit();
    http.expectOne(CREATE_URL).flush(
      { status: 409, code: 'ORG_DUPLICATE_CODE', message: 'Ce code est déjà utilisé.', path: '', correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    harness.detectChanges();
    expect(component.form.controls.code.hasError('server')).toBe(true);
    expect(component.submitError()).toContain('déjà utilisé');
    http.verify();
  });

  it('binds ORG_INVALID_TIME_ZONE to the timezone field', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('PAR');
    component.form.controls.name.setValue('Campus');
    component.form.controls.timeZoneId.setValue('Nowhere/Nowhere');
    harness.detectChanges();
    component.submit();
    http.expectOne(CREATE_URL).flush(
      { status: 400, code: 'ORG_INVALID_TIME_ZONE', message: 'Fuseau inconnu.', path: '', correlationId: null, details: [] },
      { status: 400, statusText: 'Bad Request' },
    );
    harness.detectChanges();
    expect(component.form.controls.timeZoneId.hasError('server')).toBe(true);
    http.verify();
  });

  it('prevents a double submit', async () => {
    const { harness, http, component } = await setup('create');
    component.form.controls.code.setValue('PAR');
    component.form.controls.name.setValue('Campus');
    harness.detectChanges();
    component.submit();
    component.submit();
    http.expectOne(CREATE_URL).flush({ ...SITE, publicId: 's-2' });
    http.verify();
  });
});

describe('SiteForm (edit)', () => {
  it('loads the site, freezes the code and PATCHes the modifiable fields', async () => {
    const { harness, http, location, component } = await setup('edit');
    http.expectOne(`/api/v1/sites/${ID}`).flush(SITE);
    harness.detectChanges();
    expect(component.form.controls.code.disabled).toBe(true);

    component.form.controls.name.setValue('Campus Paris rénové');
    harness.detectChanges();
    component.submit();

    const req = http.expectOne(`/api/v1/sites/${ID}`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      name: 'Campus Paris rénové',
      addressLine1: '1 rue A',
      addressLine2: null,
      postalCode: '75001',
      city: 'Paris',
      countryCode: 'FR',
      timeZoneId: 'Europe/Paris',
    });
    req.flush(SITE);
    await harness.fixture.whenStable();
    expect(location.path()).toBe(`/organization/sites/${ID}`);
    http.verify();
  });

  it('shows a not-found panel on a 404 while loading', async () => {
    const { harness, http } = await setup('edit');
    http.expectOne(`/api/v1/sites/${ID}`).flush(
      { status: 404, code: 'SITE_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();
    expect((harness.routeNativeElement as HTMLElement).textContent).toContain(
      'Aucun site ne correspond',
    );
    http.verify();
  });
});
