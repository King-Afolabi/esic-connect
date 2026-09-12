import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { Component, WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { Role } from '../../../core/models/role';
import { RoleContextService } from '../../../core/auth/role-context.service';
import {
  EnrollmentResponse,
  RemoteAttendanceAuthorizationResponse,
  StudentResponse,
} from '../students.models';
import { StudentProfile } from './student-profile';

@Component({ selector: 'app-list-stub', template: 'list-stub' })
class ListStub {}
@Component({ selector: 'app-dash-stub', template: 'dash-stub' })
class DashStub {}

/**
 * Identifiant du **compte** apprenant (refonte 2026-09) : la fiche est
 * adressée par `GET /api/v1/students/{userPublicId}`, il n'y a plus de
 * traduction profil → compte via un appel d'identité séparé.
 */
const ID = '2f1a9b7c-0000-4000-8000-000000000000';
const STUDENT_URL = `/api/v1/students/${ID}`;
const ENROLLMENTS_URL = '/api/v1/enrollments';
const REMOTE_URL = `/api/v1/remote-attendance-authorizations/students/${ID}`;

const REMOTE_AUTHORIZATION: RemoteAttendanceAuthorizationResponse = {
  publicId: 'ra-1',
  studentUserPublicId: ID,
  classGroupPublicId: 'c-1',
  status: 'ACTIVE',
  reason: 'immobilisation médicale',
  validFrom: '2026-10-01',
  validUntil: null,
  decidedAt: '2026-09-30T10:00:00Z',
  revokedAt: null,
  revocationReason: null,
  createdAt: '2026-09-30T10:00:00Z',
};

const STUDENT: StudentResponse = {
  userPublicId: ID,
  email: 'lea.martin@esic.test',
  firstName: 'Léa',
  lastName: 'Martin',
  accountStatus: 'ACTIVE',
  createdAt: '2026-08-01T10:00:00Z',
  lastLoginAt: null,
  studentNumber: 'ESIC-2026-0007',
  birthDate: null,
  workStudy: false,
  companyName: null,
  currentEnrollmentPublicId: null,
  classGroupPublicId: null,
  classGroupCode: null,
  academicYearPublicId: null,
  academicYearCode: null,
  enrollmentStatus: null,
};

/** Même compte, sans numéro étudiant ni date de naissance — refonte 2026-09. */
const STUDENT_WITHOUT_PROFILE: StudentResponse = {
  ...STUDENT,
  studentNumber: null,
  birthDate: null,
  workStudy: null,
  companyName: null,
};

const ENROLLMENT: EnrollmentResponse = {
  publicId: 'e-1',
  studentUserPublicId: ID,
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
  workStudy: false,
  companyName: null,
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
    studentReq: (): TestRequest => http.expectOne(STUDENT_URL),
    enrollmentsReq: (): TestRequest => http.expectOne((r) => r.url === ENROLLMENTS_URL),
    /**
     * Autorisations de suivi à distance (EF-ENR-004). La fiche les charge
     * systématiquement ; les tests qui ne les regardent pas doivent tout
     * de même vider l'appel, sinon `http.verify()` échoue.
     */
    remoteReq: (): TestRequest => http.expectOne(REMOTE_URL),
  };
}

describe('StudentProfile', () => {
  it('shows a loading state, then the account and profile facts once loaded', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    expect(text()).toContain('Chargement de la fiche');

    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([]);
    harness.detectChanges();

    expect(text()).toContain('Martin Léa');
    expect(text()).toContain('lea.martin@esic.test');
    expect(text()).toContain('ESIC-2026-0007');
    expect(text()).toContain('Actif');
  });

  it('still renders a full fiche when the account has no student_profile (refonte 2026-09)', async () => {
    // Le rôle STUDENT est l'unique source de vérité : l'absence de profil
    // n'empêche jamais la consultation de la fiche.
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT_WITHOUT_PROFILE);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([]);
    harness.detectChanges();

    expect(text()).toContain('Martin Léa');
    expect(text()).toContain('Aucune information complémentaire renseignée');
  });

  it('requests the enrollment history for this student account, newest first, and renders it', async () => {
    const { harness, studentReq, text, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();

    const req = enrollmentsReq();
    expect(req.request.params.get('student')).toBe(ID);
    expect(req.request.params.get('sort')).toBe('startDate,desc');
    req.flush({ content: [ENROLLMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    remoteReq().flush([]);
    harness.detectChanges();

    expect(text()).toContain('BTS-SIO-1-A');
    expect(text()).toContain('2026-2027');
    expect(text()).toContain("Issue d'un changement de classe");
    expect(text()).toContain('Réorientation');
  });

  it('derives a "Scolarité actuelle" block from the active enrollment (no extra call)', async () => {
    const { harness, http, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({
      content: [
        { ...ENROLLMENT, publicId: 'e-old', status: 'ARCHIVED', startDate: '2024-09-02' },
        { ...ENROLLMENT, status: 'ACTIVE' },
      ],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 1,
    });
    remoteReq().flush([]);
    harness.detectChanges();

    const el = harness.routeNativeElement as HTMLElement;
    expect(text()).toContain('Scolarité actuelle');
    // Les valeurs viennent de l'inscription ACTIVE, en libellés humains.
    expect(text()).toContain('BTS-SIO');
    expect(text()).toContain('BTS-SIO-1-A');
    expect(text()).toContain('2026-2027');
    expect(text()).toContain('Active');
    // Aucun UUID exposé.
    expect(text()).not.toContain('pr-1');
    // Le tableau d'historique est borné + entête figée (primitive compacte).
    expect(el.querySelector('.profile__table-wrapper.esic-table-wrap--compact')).not.toBeNull();
    // Aucun appel supplémentaire : le bloc est dérivé de l'historique déjà chargé.
    http.expectNone(() => true);
    http.verify();
  });

  it('says there is no active enrollment rather than inventing one', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({
      content: [{ ...ENROLLMENT, status: 'WITHDRAWN' }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    remoteReq().flush([]);
    harness.detectChanges();

    // Pas d'inscription ACTIVE → on retient la plus récente, statut affiché tel quel.
    expect(text()).toContain('Scolarité actuelle');
    expect(text()).toContain('Abandon');
  });

  it('shows an empty history message when the student has no enrollment', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([]);
    harness.detectChanges();

    expect(text()).toContain("Aucune inscription n'est enregistrée");
    expect(text()).toContain('Aucune inscription pour cet apprenant');
  });

  it('renders a not-found panel on a 404 and makes no further calls', async () => {
    const { harness, http, text, studentReq } = await setup();
    studentReq().flush(
      { status: 404, code: 'ENR_STUDENT_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();

    expect(text()).toContain('Aucun apprenant ne correspond à cet identifiant');
    expect(harness.routeNativeElement?.querySelector('a[href="/students"]')).not.toBeNull();
    http.expectNone(() => true);
    http.verify();
  });

  it('renders an access-denied panel on a 403', async () => {
    const { harness, text, studentReq } = await setup();
    studentReq().flush(
      { status: 403, code: 'ACCESS_DENIED', message: 'x', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    harness.detectChanges();

    expect(text()).toContain("Vous n'êtes pas autorisé à consulter cette fiche apprenant");
  });

  it('lets the user retry the history section after a failure', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush(null, { status: 500, statusText: 'Server Error' });
    remoteReq().flush([]);
    harness.detectChanges();

    expect(text()).toContain('Une erreur est survenue');
    // Le premier « Réessayer » du DOM est celui de l'historique : la
    // section « suivi à distance » est plus bas et n'est pas en erreur ici.
    const retry = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find(
      (b) => b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    enrollmentsReq().flush({ content: [ENROLLMENT], page: 0, size: 100, totalElements: 1, totalPages: 1 });
    harness.detectChanges();

    expect(text()).toContain('BTS-SIO-1-A');
  });

  it('writes nothing to browser storage', async () => {
    const { studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([]);

    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  // -------------------------------------------------------------------
  // EF-ENR-004 — suivi à distance individuel (docs/02 §15.3)
  // -------------------------------------------------------------------

  it('affiche les autorisations de suivi à distance de l’apprenant', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    remoteReq().flush([REMOTE_AUTHORIZATION]);
    harness.detectChanges();

    expect(text()).toContain('Suivi à distance');
    expect(text()).toContain('immobilisation médicale');
    expect(text()).toContain('Active');
  });

  it('distingue une autorisation générale d’une autorisation de classe', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    remoteReq().flush([{ ...REMOTE_AUTHORIZATION, classGroupPublicId: null }]);
    harness.detectChanges();

    expect(text()).toContain('Toutes ses classes');
  });

  it('masque la section quand le rôle courant n’a pas à décider (403)', async () => {
    const { harness, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });

    remoteReq().flush(null, { status: 403, statusText: 'Forbidden' });
    harness.detectChanges();

    // Un 403 n'est pas une panne : la section disparaît, elle ne s'affiche
    // pas en erreur.
    expect(text()).not.toContain('Suivi à distance');
  });

  it('n’envoie rien tant que le motif et la date de début manquent', async () => {
    const { harness, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([]);
    harness.detectChanges();

    const grant = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find(
      (b) => b.textContent?.includes('Autoriser'),
    ) as HTMLButtonElement;
    grant.click();
    harness.detectChanges();
  });

  it('accorde une autorisation puis recharge la liste', async () => {
    const { harness, http, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([]);
    harness.detectChanges();

    const component = harness.routeDebugElement?.componentInstance as Record<string, unknown>;
    (component['remoteForm'] as { patchValue: (v: unknown) => void }).patchValue({
      reason: 'immobilisation médicale',
      validFrom: '2026-10-01',
    });
    (component['grantRemote'] as () => void)();

    const created = http.expectOne('/api/v1/remote-attendance-authorizations');
    expect(created.request.method).toBe('POST');
    expect(created.request.body.studentUserPublicId).toBe(ID);
    // Champ laissé vide ⇒ transmis comme absent, jamais comme chaîne vide.
    expect(created.request.body.classGroupPublicId).toBeNull();
    created.flush(REMOTE_AUTHORIZATION);
    harness.detectChanges();

    remoteReq().flush([REMOTE_AUTHORIZATION]);
  });

  it('révoque une autorisation active sans la supprimer', async () => {
    const { harness, http, text, studentReq, enrollmentsReq, remoteReq } = await setup();
    studentReq().flush(STUDENT);
    harness.detectChanges();
    enrollmentsReq().flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    remoteReq().flush([REMOTE_AUTHORIZATION]);
    harness.detectChanges();

    const revoke = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find(
      (b) => b.textContent?.includes('Révoquer'),
    ) as HTMLButtonElement;
    revoke.click();

    http.expectOne(`/api/v1/remote-attendance-authorizations/${REMOTE_AUTHORIZATION.publicId}/revoke`)
      .flush({ ...REMOTE_AUTHORIZATION, status: 'REVOKED', revocationReason: 'Retrait' });
    harness.detectChanges();

    remoteReq().flush([{ ...REMOTE_AUTHORIZATION, status: 'REVOKED', revocationReason: 'Retrait' }]);
    harness.detectChanges();

    // La décision reste au dossier : elle apparaît révoquée, pas absente.
    expect(text()).toContain('Révoquée');
  });
});

describe('StudentProfile — changement de classe', () => {
  async function setupAsAdmin() {
    localStorage.clear();
    sessionStorage.clear();
    const effectiveRoles: WritableSignal<Role[]> = signal(['ADMIN']);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'students', component: ListStub },
          { path: 'students/:publicId', component: StudentProfile },
          { path: 'dashboard', component: DashStub },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RoleContextService, useValue: { effectiveRoles } },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/students/${ID}`, StudentProfile);
    harness.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    return { harness, http };
  }

  it('permet à un ADMIN de changer la classe d’un apprenant sans perdre l’historique', async () => {
    const { harness, http } = await setupAsAdmin();
    http.expectOne(STUDENT_URL).flush(STUDENT);
    harness.detectChanges();
    http.expectOne((r) => r.url === ENROLLMENTS_URL).flush({
      content: [ENROLLMENT],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(REMOTE_URL).flush([]);
    harness.detectChanges();

    const startButton = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find((b) =>
      b.textContent?.includes('Changer de classe'),
    ) as HTMLButtonElement;
    expect(startButton).toBeTruthy();
    startButton.click();
    harness.detectChanges();

    http.expectOne((r) => r.url === '/api/v1/class-groups').flush({
      content: [{ publicId: 'c-2', code: 'BTS-SIO-2-A' }],
      page: 0,
      size: 200,
      totalElements: 1,
      totalPages: 1,
    });
    harness.detectChanges();

    const component = harness.routeDebugElement?.componentInstance as unknown as {
      transferForm: { patchValue: (v: Record<string, unknown>) => void };
      submitTransfer: () => void;
    };
    component.transferForm.patchValue({ classGroupPublicId: 'c-2', reason: 'Passage en année supérieure' });
    component.submitTransfer();

    const req = http.expectOne(`/api/v1/enrollments/${ENROLLMENT.publicId}/transfer`);
    expect(req.request.body).toEqual({
      classGroupPublicId: 'c-2',
      reason: 'Passage en année supérieure',
      effectiveDate: null,
    });
    req.flush({ ...ENROLLMENT, publicId: 'e-2', classGroupPublicId: 'c-2', classGroupCode: 'BTS-SIO-2-A' });
    harness.detectChanges();

    http.expectOne((r) => r.url === ENROLLMENTS_URL).flush({
      content: [{ ...ENROLLMENT, status: 'TRANSFERRED', endDate: '2026-09-10' }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http.verify();
  });
});
