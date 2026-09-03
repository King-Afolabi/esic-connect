import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { ResetPassword } from './reset-password';

describe('ResetPassword', () => {
  let fixture: ComponentFixture<ResetPassword>;
  let result: Subject<void>;
  const auth = { resetPassword: vi.fn() };
  // Vrai routeur espionné : l'écran contient des `routerLink`, que la
  // directive ne peut pas résoudre avec un routeur factice.
  let router: { navigate: ReturnType<typeof vi.fn> };

  async function createComponent(token: string | undefined): Promise<void> {
    result = new Subject<void>();
    auth.resetPassword.mockReset().mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [{ provide: AuthService, useValue: auth }, provideRouter([])],
    }).compileComponents();

    const realRouter = TestBed.inject(Router);
    vi.spyOn(realRouter, 'navigate').mockResolvedValue(true);
    router = realRouter as unknown as typeof router;

    fixture = TestBed.createComponent(ResetPassword);
    fixture.componentRef.setInput('token', token);
    fixture.detectChanges();
  }

  const el = <T extends HTMLElement>(selector: string) =>
    fixture.nativeElement.querySelector(selector) as T | null;

  function fill(newPassword: string, confirmation: string): void {
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: { newPassword: string; confirmation: string }) => void };
    };
    component.form.setValue({ newPassword, confirmation });
    fixture.detectChanges();
  }

  function submit(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    fixture.detectChanges();
  }

  it('offers no form and points to a new request when the token is missing', async () => {
    await createComponent(undefined);

    expect(el('form')).toBeNull();
    expect(el('[role="alert"]')?.textContent).toContain('lien est incomplet');
    expect(el('a[href="/mot-de-passe-oublie"]')).not.toBeNull();
  });

  it('refuses to submit a password shorter than the server minimum', async () => {
    await createComponent('un-jeton');
    fill('court', 'court');
    submit();

    expect(auth.resetPassword).not.toHaveBeenCalled();
  });

  it('refuses to submit when the confirmation differs', async () => {
    await createComponent('un-jeton');
    fill('cheval batterie agrafe', 'cheval batterie agrafes');
    submit();

    expect(auth.resetPassword).not.toHaveBeenCalled();
    expect(el('[role="alert"]')?.textContent).toContain('ne correspondent pas');
  });

  it('sends the token and the new password, then returns to the login screen', async () => {
    await createComponent('un-jeton');
    fill('cheval batterie agrafe', 'cheval batterie agrafe');
    submit();

    expect(auth.resetPassword).toHaveBeenCalledWith('un-jeton', 'cheval batterie agrafe');

    result.next();
    result.complete();
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { reason: 'password-changed' },
    });
  });

  it('shows the server rejection and its details', async () => {
    await createComponent('un-jeton');
    fill('motdepasse123456', 'motdepasse123456');
    submit();

    result.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          code: 'AUTH_PASSWORD_TOO_WEAK',
          message: 'Le mot de passe choisi ne respecte pas la politique de sécurité.',
          details: ['Ce mot de passe est trop courant. Choisissez-en un autre.'],
          path: '/api/v1/auth/reset-password',
          correlationId: null,
          timestamp: '2026-09-03T12:00:00Z',
        },
      }),
    );
    fixture.detectChanges();

    const alert = el('[role="alert"][aria-live="assertive"]')?.textContent ?? '';
    expect(alert).toContain('politique de sécurité');
    expect(alert).toContain('trop courant');
  });

  it('does not navigate away when the token is refused', async () => {
    await createComponent('jeton-perime');
    fill('cheval batterie agrafe', 'cheval batterie agrafe');
    submit();

    result.error(
      new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          code: 'AUTH_RESET_TOKEN_INVALID',
          message: "Ce lien de réinitialisation n'est plus valable. Demandez-en un nouveau.",
          details: [],
          path: '/api/v1/auth/reset-password',
          correlationId: null,
          timestamp: '2026-09-03T12:00:00Z',
        },
      }),
    );
    fixture.detectChanges();

    expect(router.navigate).not.toHaveBeenCalled();
    expect(el('[role="alert"][aria-live="assertive"]')?.textContent).toContain(
      "n'est plus valable",
    );
  });
});
