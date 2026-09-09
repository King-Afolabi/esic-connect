import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { ForgotPassword } from './forgot-password';

describe('ForgotPassword', () => {
  let fixture: ComponentFixture<ForgotPassword>;
  let result: Subject<void>;
  const auth = { requestPasswordReset: vi.fn() };

  beforeEach(async () => {
    result = new Subject<void>();
    auth.requestPasswordReset.mockReset().mockReturnValue(result.asObservable());

    await TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [{ provide: AuthService, useValue: auth }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPassword);
    fixture.detectChanges();
  });

  const el = <T extends HTMLElement>(selector: string) =>
    fixture.nativeElement.querySelector(selector) as T | null;

  function fill(email: string): void {
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: { email: string }) => void };
    };
    component.form.setValue({ email });
    fixture.detectChanges();
  }

  function submit(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );
    fixture.detectChanges();
  }

  it('renders an accessible email form', () => {
    expect(el('input[type="email"]')).not.toBeNull();
    expect(el('h1')?.textContent).toContain('Mot de passe oublié');
  });

  it('does not call the API when the address is invalid', () => {
    fill('pas-une-adresse');
    submit();

    expect(auth.requestPasswordReset).not.toHaveBeenCalled();
  });

  it('shows the same neutral confirmation whatever the address', () => {
    fill('inconnu@esic-connect.test');
    submit();
    result.next();
    result.complete();
    fixture.detectChanges();

    const notice = el('[role="status"]')?.textContent ?? '';
    // Le message ne doit jamais affirmer qu'un compte existe : le serveur
    // répond déjà de façon neutre, l'interface ne doit pas trahir ce
    // choix (docs/02 §17.8).
    expect(notice).toContain('Si un compte existe');
    expect(notice).not.toContain('inconnu@esic-connect.test');
    expect(el('form')).toBeNull();
  });

  it('tells the user to wait when the rate limit is reached', () => {
    fill('alice@esic-connect.test');
    submit();
    result.error(new HttpErrorResponse({ status: 429 }));
    fixture.detectChanges();

    const alert = el('[role="alert"]')?.textContent ?? '';
    expect(alert).toContain('Patientez');
    // Un 429 ne doit pas être présenté comme une confirmation d'envoi.
    expect(el('[role="status"]')).toBeNull();
  });

  it('reports a technical failure without blaming the address', () => {
    fill('alice@esic-connect.test');
    submit();
    result.error(new HttpErrorResponse({ status: 500 }));
    fixture.detectChanges();

    const alert = el('[role="alert"]')?.textContent ?? '';
    expect(alert).toContain("n'a pas pu aboutir");
    expect(alert).not.toContain('alice@esic-connect.test');
  });
});
