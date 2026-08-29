import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Session } from '../../../core/models/session';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let loginResult: Subject<Session>;
  const auth = { login: vi.fn() };
  const router = { navigateByUrl: vi.fn() };

  beforeEach(async () => {
    loginResult = new Subject<Session>();
    auth.login.mockReset().mockReturnValue(loginResult.asObservable());
    router.navigateByUrl.mockReset();

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  });

  const el = <T extends HTMLElement>(selector: string) =>
    fixture.nativeElement.querySelector(selector) as T;

  function fillForm(email: string, password: string): void {
    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: { email: string; password: string }) => void };
    };
    component.form.setValue({ email, password });
    fixture.detectChanges();
  }

  it('renders an accessible email + password form', () => {
    expect(el('input[type="email"]')).not.toBeNull();
    expect(el('input[type="password"]')).not.toBeNull();
    expect(el<HTMLButtonElement>('button[type="submit"]').textContent).toContain('Se connecter');
  });

  it('does not call the auth service while the form is invalid', () => {
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('disables the submit button and shows progress while the request is pending', () => {
    fillForm('manager@esic.test', 'secret');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(auth.login).toHaveBeenCalledWith('manager@esic.test', 'secret');
    expect(el<HTMLButtonElement>('button[type="submit"]').disabled).toBe(true);
    expect(el('mat-progress-bar')).not.toBeNull();
  });

  it('navigates to the dashboard on success', () => {
    fillForm('manager@esic.test', 'secret');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    loginResult.next({ accessToken: 't', subject: 's', roles: [], email: 'manager@esic.test', expiresAt: 0 });
    fixture.detectChanges();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('shows a single generic message on invalid credentials (no account enumeration)', () => {
    fillForm('manager@esic.test', 'wrong');
    el<HTMLFormElement>('form').dispatchEvent(new Event('submit'));
    loginResult.error(new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }));
    fixture.detectChanges();

    const alert = el('[role="alert"]');
    expect(alert.textContent).toContain('Adresse électronique ou mot de passe incorrect.');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(el<HTMLButtonElement>('button[type="submit"]').disabled).toBe(false);
  });
});
