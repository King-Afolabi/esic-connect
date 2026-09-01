import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NotificationService } from '../../../core/notifications/notification.service';
import { PlanningImport } from './planning-import';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const CLASSES_URL = '/api/v1/class-groups';
const IMPORTS_URL = '/api/v1/planning-imports';
const notifications = { info: vi.fn(), error: vi.fn() };

interface Internals {
  form: { controls: { classGroupPublicId: { setValue: (v: string) => void } } };
  onFileSelected: (event: Event) => void;
  submit: () => void;
  submitError: () => string | null;
  fileError: () => string | null;
}

function classPage(content: unknown[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

function fileInputEvent(file: File | null): Event {
  return { target: { files: file ? [file] : [] } } as unknown as Event;
}

async function setup() {
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'planning/import', component: PlanningImport },
        { path: 'planning/import/:jobId', component: Stub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: notifications },
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl('/planning/import', PlanningImport);
  harness.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  const location = TestBed.inject(Location);
  const component = harness.routeDebugElement?.componentInstance as unknown as Internals;
  return { harness, http, location, component };
}

describe('PlanningImport', () => {
  it('loads the active classes with the ACTIVE / code sort and shows the form', async () => {
    const { harness, http } = await setup();
    const req = http.expectOne((r) => r.url === CLASSES_URL);
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('sort')).toBe('code,asc');
    expect(req.request.params.get('size')).toBe('100');
    req.flush(classPage([{ publicId: 'c-1', code: 'C1', name: 'Classe 1' }]));
    harness.detectChanges();
    const root = harness.routeNativeElement as HTMLElement;
    // Le <mat-select> rend ses <mat-option> dans un overlay CDK à
    // l'ouverture : on vérifie le formulaire, pas le texte des options.
    expect(root.querySelector('mat-select')).not.toBeNull();
    expect(root.querySelector('input[type="file"]')).not.toBeNull();
    http.verify();
  });

  it('rejects a non-CSV file and never calls the API', async () => {
    const { harness, http, component } = await setup();
    http.expectOne((r) => r.url === CLASSES_URL).flush(classPage([{ publicId: 'c-1', code: 'C1', name: 'X' }]));
    harness.detectChanges();

    component.onFileSelected(fileInputEvent(new File(['x'], 'planning.pdf', { type: 'application/pdf' })));
    component.form.controls.classGroupPublicId.setValue('c-1');
    component.submit();
    expect(component.fileError()).toContain('CSV');
    http.expectNone(IMPORTS_URL);
    http.verify();
  });

  it('simulates a valid CSV and navigates to the review of the created job', async () => {
    const { harness, http, location, component } = await setup();
    http.expectOne((r) => r.url === CLASSES_URL).flush(classPage([{ publicId: 'c-1', code: 'C1', name: 'X' }]));
    harness.detectChanges();

    component.form.controls.classGroupPublicId.setValue('c-1');
    component.onFileSelected(fileInputEvent(new File(['slot_key\nS1'], 'planning.csv', { type: 'text/csv' })));
    component.submit();

    const req = http.expectOne(IMPORTS_URL);
    expect(req.request.method).toBe('POST');
    expect((req.request.body as FormData).get('classGroupPublicId')).toBe('c-1');
    req.flush({ publicId: 'job-9' }, { status: 201, statusText: 'Created' });
    await harness.fixture.whenStable();
    expect(location.path()).toBe('/planning/import/job-9');
    expect(notifications.info).toHaveBeenCalled();
    http.verify();
  });

  it('surfaces a server error message from a rejected simulation', async () => {
    const { harness, http, component } = await setup();
    http.expectOne((r) => r.url === CLASSES_URL).flush(classPage([{ publicId: 'c-1', code: 'C1', name: 'X' }]));
    harness.detectChanges();

    component.form.controls.classGroupPublicId.setValue('c-1');
    component.onFileSelected(fileInputEvent(new File(['x'], 'planning.csv', { type: 'text/csv' })));
    component.submit();
    http.expectOne(IMPORTS_URL).flush(
      { status: 400, code: 'PLAN_MISSING_COLUMNS', message: 'Colonnes obligatoires absentes.', path: '', correlationId: null, details: [] },
      { status: 400, statusText: 'Bad Request' },
    );
    harness.detectChanges();
    expect(component.submitError()).toContain('Colonnes obligatoires');
    http.verify();
  });

  it('writes nothing to browser storage', async () => {
    const { http } = await setup();
    http.expectOne((r) => r.url === CLASSES_URL).flush(classPage([]));
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
});
