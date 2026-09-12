import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { StudentCreate } from './student-create';

const CLASSES_URL = '/api/v1/class-groups';
const USERS_URL = '/api/v1/users';
const ENROLLMENTS_URL = '/api/v1/enrollments';

interface Internals {
  form: {
    setValue: (v: {
      firstName: string;
      lastName: string;
      email: string;
      studentNumber: string;
      classGroupPublicId: string;
      birthDate: string;
      workStudy: boolean;
      companyName: string;
    }) => void;
  };
  submit: () => void;
  submitError: () => string | null;
  partialNotice: () => string | null;
}

function fillValid(internals: Internals): void {
  internals.form.setValue({
    firstName: 'Jane',
    lastName: 'Doe',
    email: 'Jane.Doe@Example.test',
    studentNumber: 'ESIC-2026-0999',
    classGroupPublicId: 'class-1',
    birthDate: '',
    workStudy: false,
    companyName: '',
  });
}

function flushClasses(http: HttpTestingController): void {
  http.expectOne((r) => r.url === CLASSES_URL).flush({
    content: [{ publicId: 'class-1', code: 'BTS1-A' }],
    page: 0,
    size: 200,
    totalElements: 1,
    totalPages: 1,
  });
}

describe('StudentCreate (Lot H)', () => {
  let fixture: ComponentFixture<StudentCreate>;
  let http: HttpTestingController;
  let internals: Internals;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      ],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(StudentCreate);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as Internals;
    fixture.detectChanges();
    flushClasses(http);
  });

  afterEach(() => http.verify());

  it('does nothing and issues no request while the form is invalid', () => {
    internals.submit();
    http.expectNone(USERS_URL);
  });

  it('chains user → enrollment in order and navigates to the new account (refonte 2026-09: no separate profile step)', () => {
    fillValid(internals);
    internals.submit();

    const userReq = http.expectOne((r) => r.url === USERS_URL && r.method === 'POST');
    expect(userReq.request.body).toEqual({
      email: 'jane.doe@example.test',
      firstName: 'Jane',
      lastName: 'Doe',
      role: 'STUDENT',
      studentNumber: 'ESIC-2026-0999',
      birthDate: null,
    });
    userReq.flush({ publicId: 'user-1' });

    const enrollReq = http.expectOne((r) => r.url === ENROLLMENTS_URL && r.method === 'POST');
    expect(enrollReq.request.body).toEqual({
      studentUserPublicId: 'user-1',
      classGroupPublicId: 'class-1',
      startDate: null,
      workStudy: false,
      companyName: null,
    });
    enrollReq.flush({ publicId: 'enr-1' });

    expect(navigate).toHaveBeenCalledWith(['/students', 'user-1']);
  });

  it('surfaces a duplicate email at step 1 and stops (no account created)', () => {
    fillValid(internals);
    internals.submit();

    http.expectOne(USERS_URL).flush(
      { timestamp: 't', status: 409, code: 'USER_EMAIL_TAKEN', message: 'déjà utilisée', path: USERS_URL, correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );

    http.expectNone(ENROLLMENTS_URL);
    expect(internals.submitError()).toContain('déjà');
  });

  it('surfaces a duplicate student number at step 1 and stops (no account created)', () => {
    // Refonte 2026-09 : le numéro étudiant est porté par user_account et
    // soumis dans le même appel que la création du compte — il n'existe
    // plus d'étape « profil » séparée où cette collision pouvait survenir.
    fillValid(internals);
    internals.submit();

    http.expectOne(USERS_URL).flush(
      { timestamp: 't', status: 409, code: 'USER_DUPLICATE_STUDENT_NUMBER', message: 'numéro pris', path: USERS_URL, correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );

    http.expectNone(ENROLLMENTS_URL);
    expect(internals.submitError()).toContain('numéro pris');

    // Rien n'a été créé : la reprise rejoue bien POST /users.
    internals.form.setValue({
      firstName: 'Jane',
      lastName: 'Doe',
      email: 'Jane.Doe@Example.test',
      studentNumber: 'ESIC-2026-1000',
      classGroupPublicId: 'class-1',
      birthDate: '',
      workStudy: false,
      companyName: '',
    });
    internals.submit();
    const retry = http.expectOne((r) => r.url === USERS_URL && r.method === 'POST');
    expect(retry.request.body).toMatchObject({ studentNumber: 'ESIC-2026-1000' });
    retry.flush({ publicId: 'user-1' });
    http.expectOne(ENROLLMENTS_URL).flush({ publicId: 'enr-1' });
    expect(navigate).toHaveBeenCalledWith(['/students', 'user-1']);
  });

  it('explains a step-2 (enrollment) failure without losing the account, and does not recreate it on retry', () => {
    fillValid(internals);
    internals.submit();

    http.expectOne(USERS_URL).flush({ publicId: 'user-1' });
    http.expectOne(ENROLLMENTS_URL).flush(
      { timestamp: 't', status: 422, code: 'ENR_CLASS_FULL', message: 'classe pleine', path: ENROLLMENTS_URL, correlationId: null, details: [] },
      { status: 422, statusText: 'Unprocessable Entity' },
    );

    expect(internals.partialNotice()).toContain("L'inscription en classe a échoué");

    // Reprise : le compte n'est pas recréé.
    internals.submit();
    http.expectNone(USERS_URL);
    http.expectOne(ENROLLMENTS_URL).flush({ publicId: 'enr-1' });
    expect(navigate).toHaveBeenCalledWith(['/students', 'user-1']);
  });
});
