import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';

import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { Role } from '../../core/models/role';
import { RoleContextService } from '../../core/auth/role-context.service';
import { AttendanceCheckIn } from './attendance-check-in';

const URL = '/api/v1/attendance/validate';
const ROOM_URL = '/api/v1/attendance/room-qr';

interface CheckInInternals {
  form: FormGroup;
  roomForm: FormGroup;
  submit: () => void;
  submitRoomQr: () => void;
  reset: () => void;
  onScanned: (raw: string) => void;
  openScanner: () => void;
  scannerOpen: () => boolean;
  prefilledFromLink: () => boolean;
}

function setup(roles: Role[] = ['STUDENT'], queryParams: Record<string, string> = {}) {
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
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
      },
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
    // `remote` non coché : transmis comme absent, jamais comme `false`
    // bavard — le serveur traite l'absence comme « en présentiel ».
    expect(req.request.body).toEqual({ shortCode: 'ABCD2345', remote: null });
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

  // -------------------------------------------------------------------
  // EF-ENR-004 / EF-ATT-010 — suivi à distance et QR fixe de salle
  // -------------------------------------------------------------------

  it('transmet la déclaration de suivi à distance quand elle est cochée', () => {
    ({ fixture, http, internals } = setup());
    internals.form.controls['shortCode'].setValue('ABCD2345');
    internals.form.controls['remote'].setValue(true);
    internals.submit();

    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ shortCode: 'ABCD2345', remote: true });
    req.flush(RECORD);
  });

  it('émarge par le QR de salle sur une route distincte', () => {
    ({ fixture, http, internals } = setup());
    internals.roomForm.controls['roomReference'].setValue('  jeton-affiche  ');
    internals.submitRoomQr();

    const req = http.expectOne('/api/v1/attendance/room-qr');
    // Le corps ne porte QUE le jeton : ni séance, ni point de contrôle —
    // le serveur déduit tout le reste (docs/02 §16.6).
    expect(req.request.body).toEqual({ roomReference: 'jeton-affiche' });
    req.flush({ ...RECORD, source: 'ROOM_STATIC_QR' });
    fixture.detectChanges();

    expect(internals.roomForm.getRawValue().roomReference).toBe('');
  });

  it("n'envoie rien tant que le code de salle est vide", () => {
    ({ fixture, http, internals } = setup());
    internals.submitRoomQr();

    http.expectNone('/api/v1/attendance/room-qr');
  });

  it('affiche le refus du serveur quand le QR de salle est hors réseau', () => {
    ({ fixture, http, internals } = setup());
    internals.roomForm.controls['roomReference'].setValue('jeton-affiche');
    internals.submitRoomQr();

    http.expectOne('/api/v1/attendance/room-qr').flush(
      {
        code: 'ATT_ROOM_QR_OUT_OF_NETWORK',
        message: "Ce QR de salle ne peut être utilisé que depuis le réseau de l'établissement.",
      },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();

    expect(text()).toContain('réseau de l');
  });

  // -------------------------------------------------------------------
  // Scan caméra (EF-ATT-001/002/009/010) — dispatch local, autorité serveur
  // -------------------------------------------------------------------

  it('ne monte jamais le scanner avant un clic explicite', () => {
    ({ fixture, http, internals } = setup());
    expect((fixture.nativeElement as HTMLElement).querySelector('app-qr-scanner')).toBeNull();
    expect(internals.scannerOpen()).toBe(false);
  });

  it('monte le scanner sur clic « Scanner un QR code »', () => {
    // La création du scanner déclenche getUserMedia : on le neutralise.
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: () => new Promise(() => undefined) },
    });
    ({ fixture, http, internals } = setup());
    const btn = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((b) =>
      (b.textContent ?? '').includes('Scanner un QR code'),
    ) as HTMLButtonElement;
    btn.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-qr-scanner')).not.toBeNull();
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: undefined });
  });

  it('un QR à jeton opaque nu est envoyé à /attendance/validate dans le champ token', () => {
    ({ fixture, http, internals } = setup());
    internals.onScanned('q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy');
    const req = http.expectOne(URL);
    expect(req.request.body).toEqual({ token: 'q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy' });
    req.flush(RECORD);
    fixture.detectChanges();
    expect(text()).toContain('Présence enregistrée');
  });

  it('une URL interne /attendance?ref= est envoyée à /attendance/room-qr', () => {
    ({ fixture, http, internals } = setup());
    const ref = 'q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy';
    internals.onScanned(`${window.location.origin}/attendance?ref=${ref}`);
    const req = http.expectOne(ROOM_URL);
    expect(req.request.body).toEqual({ roomReference: ref });
    req.flush({ ...RECORD, source: 'ROOM_STATIC_QR' });
  });

  it('un QR externe / inconnu est refusé sans aucun appel réseau', () => {
    ({ fixture, http, internals } = setup());
    internals.onScanned('https://evil.example/attendance?ref=q1w2e3r4t5y6u7i8o9p0AsDfGhJkLz');
    http.expectNone(URL);
    http.expectNone(ROOM_URL);
    fixture.detectChanges();
    expect(text()).toContain("n'est pas un code d'émargement ESIC Connect");
  });

  it('ignore un scan hors du contexte STUDENT', () => {
    ({ fixture, http, internals } = setup(['TEACHER']));
    internals.onScanned('q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy');
    http.expectNone(URL);
    http.expectNone(ROOM_URL);
  });

  it('pré-remplit le champ « QR de salle » depuis un lien profond ?ref= sans rien envoyer', () => {
    const ref = 'q1w2e3r4t5y6u7i8o9p0AsDfGhJkLzXcVbNm-_QwErTy';
    ({ fixture, http, internals } = setup(['STUDENT'], { ref }));
    fixture.detectChanges();
    expect(internals.prefilledFromLink()).toBe(true);
    expect(internals.roomForm.getRawValue().roomReference).toBe(ref);
    http.expectNone(ROOM_URL);
    expect(text()).toContain("rien n'est envoyé");
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
