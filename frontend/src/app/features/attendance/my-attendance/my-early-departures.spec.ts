import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { MyEarlyDepartures } from './my-early-departures';

const LIST = '/api/v1/me/attendance/early-departures';
const DECLARE = '/api/v1/attendance/early-departure';

const OPEN_DOSSIER = {
  publicId: 'ed-1',
  sessionPublicId: 's-1',
  sessionTitle: 'Atelier',
  sessionStartsAt: '2026-09-10T08:00:00Z',
  enrollmentPublicId: 'e-1',
  classCode: 'C1',
  departureAt: '2026-09-10T09:00:00Z',
  reason: 'rendez-vous',
  status: 'REQUESTED',
  effect: 'TO_CONFIRM',
  requestedAt: '2026-09-10T08:30:00Z',
  teacherOpinion: null,
  teacherOpinionComment: null,
  teacherOpinionAt: null,
  decidedByRole: null,
  decidedAt: null,
  decisionComment: null,
};

interface Internals {
  form: { patchValue: (v: Record<string, unknown>) => void };
  declare: () => void;
}

function setup() {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(MyEarlyDepartures);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return { fixture, http, internals: fixture.componentInstance as unknown as Internals };
}

describe('MyEarlyDepartures', () => {
  let fixture: ComponentFixture<MyEarlyDepartures>;
  let http: HttpTestingController;
  let internals: Internals;
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('states that a signalment is not an authorisation', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(LIST).flush([OPEN_DOSSIER]);
    fixture.detectChanges();

    // Le point qui compte : l'apprenant ne doit pas croire que signaler
    // suffit — la conséquence tomberait sur son relevé d'assiduité.
    expect(text()).toContain("Un signalement n'est pas une autorisation");
    expect(text()).toContain('À confirmer');
    expect(text()).toContain('Signalé');
  });

  it('declares a departure with an ISO instant and reloads the list', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(LIST).flush([]);
    fixture.detectChanges();

    internals.form.patchValue({
      sessionPublicId: 's-1',
      departureAt: '2026-09-10T11:00',
      reason: 'rendez-vous médical',
    });
    internals.declare();

    const created = http.expectOne(DECLARE);
    expect(created.request.method).toBe('POST');
    expect(created.request.body.sessionPublicId).toBe('s-1');
    // Le champ `datetime-local` est une heure locale ; le serveur attend
    // un instant.
    expect(created.request.body.departureAt).toMatch(/Z$/);
    created.flush(OPEN_DOSSIER, { status: 201, statusText: 'Created' });

    http.expectOne(LIST).flush([OPEN_DOSSIER]);
    fixture.detectChanges();
    expect(text()).toContain('Atelier');
  });

  it('surfaces the server refusal of a departure outside the session window', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(LIST).flush([]);
    fixture.detectChanges();

    internals.form.patchValue({
      sessionPublicId: 's-1',
      departureAt: '2026-09-10T03:00',
      reason: 'trop tôt',
    });
    internals.declare();
    http.expectOne(DECLARE).flush(
      {
        // Forme réelle de `ApiError` : `normalizeHttpError` exige `code`
        // ET `status` pour accorder sa confiance au corps.
        timestamp: '2026-09-10T08:31:00Z',
        status: 400,
        code: 'ATT_EARLY_DEPARTURE_TIME_OUTSIDE_SESSION',
        message: "L'heure de départ doit être comprise dans les horaires de la séance.",
        path: '/api/v1/attendance/early-departure',
        correlationId: null,
        details: [],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    expect(text()).toContain("L'heure de départ doit être comprise");
  });

  it('does not submit an incomplete form', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(LIST).flush([]);
    fixture.detectChanges();

    internals.form.patchValue({ sessionPublicId: '', departureAt: '', reason: '' });
    internals.declare();
    http.expectNone(DECLARE);
  });

  it('stores nothing in the browser', () => {
    ({ fixture, http } = setup());
    http.expectOne(LIST).flush([OPEN_DOSSIER]);
    fixture.detectChanges();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });
});
