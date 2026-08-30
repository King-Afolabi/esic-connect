import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';

import { Role } from '../../core/models/role';
import { RoleContextService } from '../../core/auth/role-context.service';
import { AttendanceCheckIn } from './attendance-check-in';

const URL = '/api/v1/attendance/validate';

interface CheckInInternals {
  form: FormGroup;
  submit: () => void;
  reset: () => void;
}

function setup(roles: Role[] = ['STUDENT']) {
  localStorage.clear();
  sessionStorage.clear();
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  });
  const fixture = TestBed.createComponent(AttendanceCheckIn);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as CheckInInternals;
  fixture.detectChanges();
  return { fixture, http, internals, effectiveRoles };
}

const RECORD = {
  attendancePublicId: 'a-1',
  sessionPublicId: 's-1',
  sessionTitle: 'Rattrapage',
  recordedAt: '2026-09-10T06:01:00Z',
  source: 'SHORT_CODE',
};

function apiError(status: number, code: string, message: string) {
  return [
    { timestamp: 't', status, code, message, path: '/', correlationId: null, details: [] },
    { status, statusText: 'x' },
  ] as const;
}

describe('AttendanceCheckIn', () => {
  let fixture: ComponentFixture<AttendanceCheckIn>;
  let http: HttpTestingController;
  let internals: CheckInInternals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => http.verify());

  it('does not call the API when the code is empty', () => {
    ({ fixture, http, internals } = setup());
    internals.submit();
    http.expectNone(URL);
  });

  it('normalizes the code (upper-case, no separators) before sending it', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('  abcd-23 45 ');
    internals.submit();
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ shortCode: 'ABCD2345' });
    req.flush(RECORD);
  });

  it('shows an accessible confirmation with the recorded time and clears the field', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush(RECORD);
    fixture.detectChanges();
    expect(text()).toContain('Présence enregistrée');
    expect(text()).toContain('Rattrapage');
    expect(internals.form.getRawValue().shortCode).toBe('');
  });

  it('prevents a double submission', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    internals.submit();
    const reqs = http.match(URL);
    expect(reqs.length).toBe(1);
    reqs[0].flush(RECORD);
  });

  it.each([
    ['ATT_TOKEN_INVALID', 409, 'invalide ou a expiré'],
    ['ATT_SESSION_CLOSED', 409, "n'est pas ouverte à l'émargement"],
    ['ATT_NOT_ENROLLED', 409, "pas inscrit à une classe"],
    ['ATT_ALREADY_RECORDED', 409, 'déjà été enregistrée'],
    ['ATT_TOKEN_BACKEND_UNAVAILABLE', 503, 'momentanément indisponible'],
  ])('shows the controlled message for %s', (code, status, fragment) => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush(...apiError(status, code, serverMessageFor(code)));
    fixture.detectChanges();
    expect(text()).toContain(fragment);
  });

  it('never echoes the submitted code back in an error message', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('ZZZZ9999');
    internals.submit();
    http.expectOne(URL).flush(...apiError(409, 'ATT_TOKEN_INVALID', 'Ce code d’émargement est invalide ou a expiré.'));
    fixture.detectChanges();
    expect(text()).not.toContain('ZZZZ9999');
  });

  it('falls back to a generic message for an unknown code and for a 5xx (no raw server text)', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush(...apiError(400, 'ATT_FUTURE_CODE', 'message arbitraire du futur'));
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');
    expect(text()).not.toContain('arbitraire');

    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush('stacktrace at line 42', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Une erreur est survenue');
    expect(text()).not.toContain('stacktrace');
  });

  it('stays usable after an error: a new submission is accepted', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('BADCODE1');
    internals.submit();
    http.expectOne(URL).flush(...apiError(409, 'ATT_TOKEN_INVALID', 'invalide'));
    fixture.detectChanges();

    internals.form.controls['shortCode'].setValue('GOODCODE');
    internals.submit();
    http.expectOne(URL).flush(RECORD);
    fixture.detectChanges();
    expect(text()).toContain('Présence enregistrée');
  });

  it('reads nothing from the URL and writes nothing to storage', () => {
    ({ fixture, http, internals } = setup());
    expect(internals.form.getRawValue().shortCode).toBe('');
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush(RECORD);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('refuses input and submission outside an effective STUDENT context', () => {
    ({ fixture, http, internals } = setup(['TEACHER']));
    fixture.detectChanges();
    expect(text()).toContain('Sélectionnez le contexte « apprenant »');
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();

    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectNone(URL);
  });

  it('clears the code, the receipt and business errors when the STUDENT context is lost', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['STUDENT']));
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush(RECORD);
    fixture.detectChanges();
    expect(text()).toContain('Présence enregistrée');

    effectiveRoles.set(['TEACHER']);
    fixture.detectChanges();
    expect(text()).not.toContain('Présence enregistrée');
    expect(text()).not.toContain('Rattrapage');
    expect(internals.form.getRawValue().shortCode).toBe('');
  });

  it('ignores a validate response that arrives after the STUDENT context was lost', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['STUDENT']));
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    const req = http.expectOne(URL);

    effectiveRoles.set(['TEACHER']);
    fixture.detectChanges();

    req.flush(RECORD);
    fixture.detectChanges();
    expect(text()).not.toContain('Présence enregistrée');
  });

  it('becomes usable again when the STUDENT context is restored, without a reload', () => {
    let effectiveRoles!: WritableSignal<Role[]>;
    ({ fixture, http, internals, effectiveRoles } = setup(['TEACHER']));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();

    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('form')).not.toBeNull();

    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.submit();
    http.expectOne(URL).flush(RECORD);
    fixture.detectChanges();
    expect(text()).toContain('Présence enregistrée');
  });
});

function serverMessageFor(code: string): string {
  switch (code) {
    case 'ATT_TOKEN_INVALID':
      return 'Ce code d’émargement est invalide ou a expiré. Demandez un nouveau code.';
    case 'ATT_SESSION_CLOSED':
      return "Cette séance n'est pas ouverte à l'émargement.";
    case 'ATT_NOT_ENROLLED':
      return "Vous n'êtes pas inscrit à une classe de cette séance.";
    case 'ATT_ALREADY_RECORDED':
      return 'Votre présence a déjà été enregistrée pour ce point de contrôle.';
    case 'ATT_TOKEN_BACKEND_UNAVAILABLE':
      return 'Le service d’émargement est momentanément indisponible. Réessayez dans un instant.';
    default:
      return 'Erreur.';
  }
}
