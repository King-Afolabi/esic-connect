import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { MfaChallenge } from './mfa-challenge';
import { PendingChallengeStore } from './pending-challenge.store';

describe('MfaChallenge', () => {
  let fixture: ComponentFixture<MfaChallenge>;
  let store: PendingChallengeStore;
  let router: Router;

  const auth = {
    verifyMfa: vi.fn(),
    startMfaEnrollment: vi.fn(),
    confirmMfaEnrollment: vi.fn(),
  };

  beforeEach(async () => {
    auth.verifyMfa.mockReset();
    auth.startMfaEnrollment.mockReset().mockReturnValue(
      of({ secret: 'JBSWY3DPEHPK3PXP', provisioningUri: 'otpauth://totp/x', periodSeconds: 30 }),
    );
    auth.confirmMfaEnrollment.mockReset();

    await TestBed.configureTestingModule({
      imports: [MfaChallenge],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();

    store = TestBed.inject(PendingChallengeStore);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => {
    store.clear();
    vi.restoreAllMocks();
  });

  const el = <T extends HTMLElement>(selector: string) =>
    fixture.nativeElement.querySelector(selector) as T;

  function startWith(purpose: 'VERIFY' | 'ENROLL'): void {
    store.start({
      challenge: { challengeId: 'defi-1', purpose, expiresInSeconds: 300 },
      email: 'admin@esic-connect.test',
      redirect: '/dashboard',
    });
    fixture = TestBed.createComponent(MfaChallenge);
    fixture.detectChanges();
  }

  function fillCode(code: string): void {
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (value: { code: string }) => void };
    };
    component.form.setValue({ code });
    fixture.detectChanges();
  }

  it('renvoie vers la connexion en l’absence de défi en mémoire', () => {
    fixture = TestBed.createComponent(MfaChallenge);
    fixture.detectChanges();

    // Le défi vaut preuve de la première étape : il n'est ni dans l'URL,
    // ni persisté. Une arrivée directe ne peut donc rien reprendre.
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('vérifie le code saisi et rejoint la destination demandée', () => {
    const result = new Subject<unknown>();
    auth.verifyMfa.mockReturnValue(result.asObservable());
    startWith('VERIFY');

    fillCode('123456');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(auth.verifyMfa).toHaveBeenCalledWith('defi-1', '123456', 'admin@esic-connect.test');

    result.next({});
    result.complete();
    fixture.detectChanges();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('affiche un message neutre lorsque le code est refusé', () => {
    auth.verifyMfa.mockReturnValue({
      subscribe: ({ error }: { error: (e: unknown) => void }) => error({ status: 401 }),
    });
    startWith('VERIFY');

    fillCode('000000');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(el('[role="alert"]').textContent).toContain('Code incorrect');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it("ouvre l'enrôlement et affiche le secret quand le rôle l'impose", () => {
    startWith('ENROLL');

    expect(auth.startMfaEnrollment).toHaveBeenCalledWith('defi-1');
    expect(fixture.nativeElement.textContent).toContain('JBSWY3DPEHPK3PXP');
  });

  it("affiche les codes de récupération avant d'ouvrir la session", () => {
    auth.confirmMfaEnrollment.mockReturnValue(
      of({ recoveryCodes: ['ABCDE-12345', 'FGHIJ-67890'], session: {} }),
    );
    startWith('ENROLL');

    fillCode('123456');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    // La session est ouverte côté service, mais l'écran ne quitte pas
    // avant que la personne ait pu noter ses codes : ils ne seront plus
    // jamais affichés.
    expect(fixture.nativeElement.textContent).toContain('ABCDE-12345');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('ne rejoint la destination qu’après confirmation de la prise en compte des codes', () => {
    auth.confirmMfaEnrollment.mockReturnValue(of({ recoveryCodes: ['ABCDE-12345'], session: {} }));
    startWith('ENROLL');
    fillCode('123456');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    el<HTMLButtonElement>('button[mat-flat-button]').click();
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });
});
