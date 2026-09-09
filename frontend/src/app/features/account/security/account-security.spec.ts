import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';
import { AccountSecurity } from './account-security';

describe('AccountSecurity', () => {
  let fixture: ComponentFixture<AccountSecurity>;

  const auth = {
    mfaStatus: vi.fn(),
    trustedDevices: vi.fn(),
    passkeys: vi.fn(),
    startMfaEnrollment: vi.fn(),
    confirmMfaEnrollment: vi.fn(),
    registerPasskey: vi.fn(),
    revokePasskey: vi.fn(),
    revokeTrustedDevice: vi.fn(),
    changePassword: vi.fn(),
    completePasswordChange: vi.fn(),
  };

  beforeEach(async () => {
    auth.mfaStatus.mockReset().mockReturnValue(
      of({
        enabled: true,
        enrollmentPending: false,
        requiredByRole: true,
        confirmedAt: '2026-09-01T08:00:00Z',
        activeRecoveryCodes: 10,
      }),
    );
    auth.trustedDevices.mockReset().mockReturnValue(
      of([
        {
          id: 'device-1',
          label: 'Appareil reconnu le 2026-09-01',
          firstSeenAt: '2026-09-01T08:00:00Z',
          lastSeenAt: '2026-09-02T08:00:00Z',
          expiresAt: '2026-10-01T08:00:00Z',
          usable: true,
        },
      ]),
    );
    auth.passkeys.mockReset().mockReturnValue(of([]));
    auth.startMfaEnrollment.mockReset();
    auth.confirmMfaEnrollment.mockReset();
    auth.registerPasskey.mockReset();
    auth.revokePasskey.mockReset();
    auth.revokeTrustedDevice.mockReset().mockReturnValue(of(undefined));
    auth.changePassword.mockReset().mockReturnValue(of(undefined));
    auth.completePasswordChange.mockReset();

    await TestBed.configureTestingModule({
      imports: [AccountSecurity],
      providers: [{ provide: AuthService, useValue: auth }],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountSecurity);
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  it("affiche l'état du second facteur sans jamais montrer le secret", () => {
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('10 code(s) de récupération');
    // Le secret partagé n'est renvoyé qu'à l'enrôlement : l'écran d'état
    // ne doit ni le demander ni pouvoir l'afficher.
    expect(auth.mfaStatus).toHaveBeenCalledOnce();
    expect(text).not.toContain('secret');
  });

  it("indique qu'un facteur imposé par le rôle ne peut pas être retiré", () => {
    expect(fixture.nativeElement.textContent).toContain('ne peut pas être retiré');
    // Aucun bouton de retrait n'existe : le serveur le refuserait de
    // toute façon (RG-007), l'interface ne le propose donc pas.
    expect(fixture.nativeElement.textContent).not.toContain('Désactiver');
  });

  it('liste les appareils reconnus et permet de les révoquer', () => {
    expect(fixture.nativeElement.textContent).toContain('Appareil reconnu le');

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const revoke = buttons.find((button) => button.textContent?.includes('Révoquer'));
    revoke!.click();
    fixture.detectChanges();

    expect(auth.revokeTrustedDevice).toHaveBeenCalledWith('device-1');
  });

  it("n'expose jamais l'empreinte d'appareil", () => {
    expect(fixture.nativeElement.textContent).not.toContain('deviceHash');
  });

  // -- Changement de mot de passe (EF-AUTH, docs/02 §17.1) --------------

  function setPasswordForm(current: string, next: string, confirm: string): void {
    const component = fixture.componentInstance as unknown as {
      passwordForm: {
        setValue(v: { currentPassword: string; newPassword: string; confirmPassword: string }): void;
      };
    };
    component.passwordForm.setValue({
      currentPassword: current,
      newPassword: next,
      confirmPassword: confirm,
    });
    fixture.detectChanges();
  }

  function submitPassword(): void {
    const form = fixture.nativeElement.querySelector(
      'form.security__password-form',
    ) as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  it('propose un formulaire de changement de mot de passe à trois champs', () => {
    const form = fixture.nativeElement.querySelector('form.security__password-form');
    expect(form).toBeTruthy();
    expect(form.querySelector('input[formcontrolname="currentPassword"]')).toBeTruthy();
    expect(form.querySelector('input[formcontrolname="newPassword"]')).toBeTruthy();
    expect(form.querySelector('input[formcontrolname="confirmPassword"]')).toBeTruthy();
  });

  it('bloque la soumission quand la confirmation diffère, sans appeler le serveur', () => {
    setPasswordForm('ancien mot de passe', 'nouveau mot de passe long', 'pas-le-meme');
    submitPassword();
    expect(auth.changePassword).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('ne correspondent pas');
  });

  it('bloque un nouveau mot de passe trop court, sans appeler le serveur', () => {
    setPasswordForm('ancien mot de passe', 'court', 'court');
    submitPassword();
    expect(auth.changePassword).not.toHaveBeenCalled();
  });

  it('envoie le changement puis délègue la fin de session à AuthService', () => {
    setPasswordForm('ancien mot de passe', 'nouveau mot de passe long', 'nouveau mot de passe long');
    submitPassword();
    expect(auth.changePassword).toHaveBeenCalledWith(
      'ancien mot de passe',
      'nouveau mot de passe long',
    );
    expect(auth.completePasswordChange).toHaveBeenCalledOnce();
  });

  it('affiche le message du serveur si le mot de passe actuel est refusé', () => {
    auth.changePassword.mockReturnValueOnce(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            error: {
              status: 401,
              code: 'AUTH_CURRENT_PASSWORD_INVALID',
              message: 'Le mot de passe actuel est incorrect.',
              details: [],
            },
          }),
      ),
    );
    setPasswordForm('mauvais', 'nouveau mot de passe long', 'nouveau mot de passe long');
    submitPassword();
    expect(auth.completePasswordChange).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Le mot de passe actuel est incorrect.');
  });
});
