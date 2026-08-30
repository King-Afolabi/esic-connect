import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { Router, provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { SessionForm } from './session-form';

interface FormInternals {
  form: FormGroup;
  submit: () => void;
  retry: () => void;
  teachers: () => { publicId: string }[];
  classes: () => { publicId: string }[];
}

const TEACHERS_URL = '/api/v1/sessions/teachers';
const CLASSES_URL = '/api/v1/class-groups';
const CREATE_URL = '/api/v1/sessions';

function setup() {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const navigate = vi.fn().mockResolvedValue(true);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
    ],
  });
  const router = TestBed.inject(Router);
  router.navigate = navigate as unknown as Router['navigate'];
  const fixture = TestBed.createComponent(SessionForm);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as FormInternals;
  fixture.detectChanges();
  return { fixture, http, internals, navigate };
}

function loadReady(http: HttpTestingController): void {
  http
    .expectOne(TEACHERS_URL)
    .flush([{ publicId: 't-1', firstName: 'Alice', lastName: 'Martin' }]);
  http
    .expectOne((r) => r.url === CLASSES_URL)
    .flush({
      content: [
        { publicId: 'c-1', code: 'C1', name: 'Classe 1', promotionPublicId: 'p', programLevelPublicId: 'l', sitePublicId: null, capacity: null, status: 'ACTIVE', archivedAt: null, archiveReason: null, createdAt: '', updatedAt: '' },
      ],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
}

function fillValid(internals: FormInternals): void {
  internals.form.setValue({
    teacherPublicId: 't-1',
    classPublicIds: ['c-1'],
    date: '2026-09-10',
    startTime: '08:00',
    endTime: '12:00',
    timeZoneId: 'Europe/Paris',
    reason: 'rattrapage',
    title: '',
  });
}

describe('SessionForm', () => {
  let fixture: ComponentFixture<SessionForm>;
  let http: HttpTestingController;
  let internals: FormInternals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('loads eligible teachers and active classes then shows the form', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http);
    fixture.detectChanges();
    expect(text()).toContain('Nouvelle séance exceptionnelle');
    expect(text()).toContain('Créer la séance');
    // Les <mat-option> ne sont rendues qu'à l'ouverture du select : on
    // vérifie les données chargées via l'état du composant.
    expect(internals.teachers().map((t) => t.publicId)).toEqual(['t-1']);
    expect(internals.classes().map((c) => c.publicId)).toEqual(['c-1']);
  });

  it('does not submit when required fields are missing', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http);
    internals.submit();
    http.expectNone(CREATE_URL);
  });

  it('rejects a period whose end is not after the start, without calling the API', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http);
    fillValid(internals);
    internals.form.controls['startTime'].setValue('12:00');
    internals.form.controls['endTime'].setValue('08:00');
    internals.submit();
    http.expectNone(CREATE_URL);
    fixture.detectChanges();
    expect(text()).toContain('postérieure à son début');
  });

  it('posts the exact body and navigates to the session detail on success', () => {
    let navigate!: ReturnType<typeof vi.fn>;
    ({ fixture, http, internals, navigate } = setup());
    loadReady(http);
    fillValid(internals);
    internals.submit();
    const req = http.expectOne(CREATE_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      teacherPublicId: 't-1',
      classPublicIds: ['c-1'],
      startsAt: '2026-09-10T06:00:00.000Z',
      endsAt: '2026-09-10T10:00:00.000Z',
      timeZoneId: 'Europe/Paris',
      reason: 'rattrapage',
      title: null,
    });
    req.flush({ publicId: 's-9' });
    expect(navigate).toHaveBeenCalledWith(['/sessions', 's-9']);
  });

  it('prevents a double submission', () => {
    ({ fixture, http, internals } = setup());
    loadReady(http);
    fillValid(internals);
    internals.submit();
    internals.submit();
    const reqs = http.match(CREATE_URL);
    expect(reqs.length).toBe(1);
    reqs[0].flush({ publicId: 's-9' });
  });

  it('shows a controlled error and does not navigate on a business failure', () => {
    let navigate!: ReturnType<typeof vi.fn>;
    ({ fixture, http, internals, navigate } = setup());
    loadReady(http);
    fillValid(internals);
    internals.submit();
    http.expectOne(CREATE_URL).flush(
      { timestamp: 't', status: 409, code: 'SESSION_TEACHER_NOT_ELIGIBLE', message: "Ce compte n'est pas un formateur actif.", path: '/', correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Ce compte n'est pas un formateur actif");
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows a forbidden panel when the form data cannot be loaded (403)', () => {
    ({ fixture, http, internals } = setup());
    // `forkJoin` propage la première erreur et annule l'autre source :
    // on ne flush que la requête qui échoue.
    http.expectOne((r) => r.url === CLASSES_URL); // consommée puis annulée
    http.expectOne(TEACHERS_URL).flush(
      { timestamp: 't', status: 403, code: 'ACCESS_DENIED', message: 'x', path: '/', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();
    expect(text()).toContain("Vous n'êtes pas autorisé à créer une séance");
  });
});
