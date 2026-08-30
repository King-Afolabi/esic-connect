import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { EnrollmentPicker } from './enrollment-picker';

@Component({ selector: 'app-stub', template: 'stub' })
class Stub {}

const CLASSES_URL = '/api/v1/class-groups';
const ENROLLMENTS_URL = '/api/v1/enrollments';

const CLASS = {
  publicId: 'c-1',
  promotionPublicId: 'pr-1',
  programLevelPublicId: 'lv-1',
  sitePublicId: null,
  code: 'BTS-SIO-1-A',
  name: 'BTS SIO 1 A',
  capacity: 24,
  status: 'ACTIVE' as const,
  archivedAt: null,
  archiveReason: null,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z',
};

interface Internals {
  classSearch: { setValue: (v: { q: string }) => void };
  searchClasses: () => void;
  selectClass: (c: typeof CLASS) => void;
  manualForm: { setValue: (v: { enrollmentPublicId: string }) => void };
  openManual: () => void;
}

async function setup() {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'alternation/enrollments', component: EnrollmentPicker },
        { path: 'alternation/enrollments/:enrollmentPublicId', component: Stub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl('/alternation/enrollments', EnrollmentPicker);
  harness.detectChanges();
  return {
    harness,
    http: TestBed.inject(HttpTestingController),
    location: TestBed.inject(Location),
    text: () => harness.routeNativeElement?.textContent ?? '',
    component: harness.routeDebugElement?.componentInstance as unknown as Internals,
  };
}

describe('EnrollmentPicker', () => {
  it('searches classes then lists the enrollments of the chosen class', async () => {
    const { harness, http, text, component } = await setup();
    component.searchClasses();
    http.expectOne((r) => r.url === CLASSES_URL).flush({
      content: [CLASS],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    harness.detectChanges();
    expect(text()).toContain('BTS-SIO-1-A');

    component.selectClass(CLASS);
    const enr = http.expectOne((r) => r.url === ENROLLMENTS_URL);
    expect(enr.request.params.get('classGroup')).toBe('c-1');
    enr.flush({
      content: [
        {
          publicId: 'e-1',
          studentProfilePublicId: 'sp-1',
          studentNumber: 'ESIC-2026-0001',
          classGroupPublicId: 'c-1',
          classGroupCode: 'BTS-SIO-1-A',
          programPublicId: 'pr-1',
          programCode: 'BTS-SIO',
          academicYearPublicId: 'ay-1',
          academicYearCode: '2026-2027',
          startDate: '2026-09-02',
          endDate: null,
          status: 'ACTIVE',
          enrollmentSource: 'MANUAL',
          changeReason: null,
          previousEnrollmentPublicId: null,
          createdAt: '2026-09-02T08:00:00Z',
          updatedAt: '2026-09-02T08:00:00Z',
        },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    harness.detectChanges();
    expect(text()).toContain('ESIC-2026-0001');
    expect(
      harness.routeNativeElement?.querySelector('a[href="/alternation/enrollments/e-1"]'),
    ).not.toBeNull();
    http.verify();
  });

  it('shows an access-denied panel plus the manual entry when the enrollment list returns 403', async () => {
    const { harness, http, text, component } = await setup();
    component.selectClass(CLASS);
    http.expectOne((r) => r.url === ENROLLMENTS_URL).flush(
      { status: 403, code: 'ACCESS_DENIED', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    harness.detectChanges();
    expect(text()).toContain("réservée à l'administration");
    expect(text()).toContain("Ouvrir une inscription par identifiant");
    http.verify();
  });

  it('navigates to the enrollment screen from the manual identifier field', async () => {
    const { harness, location, component } = await setup();
    component.manualForm.setValue({ enrollmentPublicId: '  e-42  ' });
    component.openManual();
    await harness.fixture.whenStable();
    expect(location.path()).toBe('/alternation/enrollments/e-42');
  });
});
