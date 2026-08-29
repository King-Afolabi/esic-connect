import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { EnrollmentResponse, StudentProfileResponse } from '../students.models';
import { StudentProfile } from './student-profile';

@Component({ selector: 'app-list-stub', template: 'list-stub' })
class ListStub {}
@Component({ selector: 'app-dash-stub', template: 'dash-stub' })
class DashStub {}

const ID = '2f1a9b7c-0000-4000-8000-000000000000';
const PROFILE_URL = `/api/v1/student-profiles/${ID}`;
const IDENTITY_URL = '/api/v1/users/u-1';
const ENROLLMENTS_URL = '/api/v1/enrollments';

const PROFILE: StudentProfileResponse = {
  publicId: ID,
  userPublicId: 'u-1',
  studentNumber: 'ESIC-2026-0007',
  birthDate: null,
  workStudy: false,
  companyName: null,
  status: 'ACTIVE',
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: '2026-08-02T10:00:00Z',
};

const ENROLLMENT: EnrollmentResponse = {
  publicId: 'e-1',
  studentProfilePublicId: ID,
  studentNumber: 'ESIC-2026-0007',
  classGroupPublicId: 'c-1',
  classGroupCode: 'BTS-SIO-1-A',
  programPublicId: 'pr-1',
  programCode: 'BTS-SIO',
  academicYearPublicId: 'ay-1',
  academicYearCode: '2026-2027',
  startDate: '2026-09-02',
  endDate: null,
  status: 'ACTIVE',
  enrollmentSource: 'CLASS_TRANSFER',
  changeReason: 'Réorientation',
  previousEnrollmentPublicId: 'e-0',
  createdAt: '2026-09-02T08:00:00Z',
  updatedAt: '2026-09-02T08:00:00Z',
};

async function setup(id = ID) {
  localStorage.clear();
  sessionStorage.clear();

  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'students', component: ListStub },
        { path: 'students/:publicId', component: StudentProfile },
        { path: 'dashboard', component: DashStub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });

  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/students/${id}`, StudentProfile);
  harness.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  return {
    harness,
    http,
    text: () => harness.routeNativeElement?.textContent ?? '',
    profileReq: (): TestRequest => http.expectOne(PROFILE_URL),
    enrollmentsReq: (): TestRequest => http.expectOne((r) => r.url === ENROLLMENTS_URL),
  };
}

describe('StudentProfile', () => {
  it('shows a loading state, then the profile facts once loaded', async () => {
    const { harness, http, text, profileReq, enrollmentsReq } = await setup();
    expect(text()).toContain('Chargement de la fiche');

    profileReq().flush(PROFILE);
    harness.detectChanges();

    http.expectOne(IDENTITY_URL).flush({
      publicId: 'u-1',
      email: 'lea.martin@esic.test',
      firstName: 'Léa',
      lastName: 'Martin',
    });
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    expect(text()).toContain('Léa Martin');
    expect(text()).toContain('lea.martin@esic.test');
    expect(text()).toContain('ESIC-2026-0007');
    expect(text()).toContain('Actif');
    http.verify();
  });

  it('still renders the profile when the optional identity call fails', async () => {
    const { harness, http, text, profileReq, enrollmentsReq } = await setup();
    profileReq().flush(PROFILE);
    harness.detectChanges();

    http.expectOne(IDENTITY_URL).flush(null, { status: 500, statusText: 'Server Error' });
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    expect(text()).toContain('Apprenant ESIC-2026-0007');
    expect(text()).not.toContain('Adresse électronique');
    http.verify();
  });

  it('requests the enrollment history for this student, newest first, and renders it', async () => {
    const { harness, http, text, profileReq, enrollmentsReq } = await setup();
    profileReq().flush(PROFILE);
    harness.detectChanges();
    http.expectOne(IDENTITY_URL).flush({ publicId: 'u-1', email: 'x@y.z', firstName: 'A', lastName: 'B' });

    const req = enrollmentsReq();
    expect(req.request.params.get('student')).toBe(ID);
    expect(req.request.params.get('sort')).toBe('startDate,desc');
    req.flush({ content: [ENROLLMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();

    expect(text()).toContain('BTS-SIO-1-A');
    expect(text()).toContain('2026-2027');
    expect(text()).toContain("Issue d'un changement de classe");
    expect(text()).toContain('Réorientation');
    http.verify();
  });

  it('shows an empty history message when the student has no enrollment', async () => {
    const { harness, http, text, profileReq, enrollmentsReq } = await setup();
    profileReq().flush(PROFILE);
    harness.detectChanges();
    http.expectOne(IDENTITY_URL).flush({ publicId: 'u-1', email: 'x@y.z', firstName: 'A', lastName: 'B' });
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    harness.detectChanges();

    expect(text()).toContain("Aucune inscription n'est enregistrée");
    http.verify();
  });

  it('renders a not-found panel on a 404 and makes no further calls', async () => {
    const { harness, http, text, profileReq } = await setup();
    profileReq().flush(
      { status: 404, code: 'ENR_STUDENT_PROFILE_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();

    expect(text()).toContain('Aucun profil apprenant ne correspond');
    expect(harness.routeNativeElement?.querySelector('a[href="/students"]')).not.toBeNull();
    http.expectNone(() => true);
    http.verify();
  });

  it('renders an access-denied panel on a 403', async () => {
    const { harness, http, text, profileReq } = await setup();
    profileReq().flush(
      { status: 403, code: 'ACCESS_DENIED', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    harness.detectChanges();

    expect(text()).toContain("Vous n'êtes pas autorisé à consulter cette fiche apprenant");
    http.verify();
  });

  it('lets the user retry the history section after a failure', async () => {
    const { harness, http, text, profileReq, enrollmentsReq } = await setup();
    profileReq().flush(PROFILE);
    harness.detectChanges();
    http.expectOne(IDENTITY_URL).flush({ publicId: 'u-1', email: 'x@y.z', firstName: 'A', lastName: 'B' });
    enrollmentsReq().flush(null, { status: 500, statusText: 'Server Error' });
    harness.detectChanges();

    expect(text()).toContain('Une erreur est survenue');
    const retry = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find(
      (b) => b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    enrollmentsReq().flush({ content: [ENROLLMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();

    expect(text()).toContain('BTS-SIO-1-A');
    http.verify();
  });

  it('writes nothing to browser storage', async () => {
    const { harness, http, profileReq, enrollmentsReq } = await setup();
    profileReq().flush(PROFILE);
    harness.detectChanges();
    http.expectOne(IDENTITY_URL).flush({ publicId: 'u-1', email: 'x@y.z', firstName: 'A', lastName: 'B' });
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
});
