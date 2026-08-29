import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AccountActivation } from './account-activation';

@Component({ template: 'login-stub' })
class LoginStub {}

interface ActivationInternals {
  form: FormGroup<{ password: FormControl<string> }>;
  submit: () => void;
  retryValidation: () => void;
  submitting: () => boolean;
}

const VALIDATE_URL = '/api/v1/account-invitations/validate';
const ACTIVATE_URL = '/api/v1/account-invitations/activate';
const RAW_TOKEN = 'raw-secret-token-abc123';
const VALID_PASSWORD = 'a-long-enough-passphrase';

async function setup(url: string) {
  localStorage.clear();
  sessionStorage.clear();

  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'activation', component: AccountActivation },
        { path: 'login', component: LoginStub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });

  const harness = await RouterTestingHarness.create();
  const component = (await harness.navigateByUrl(url, AccountActivation)) as AccountActivation;
  harness.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  const location = TestBed.inject(Location);
  const internals = component as unknown as ActivationInternals;

  return {
    harness,
    component,
    internals,
    http,
    location,
    text: () => harness.routeNativeElement?.textContent ?? '',
    html: () => harness.routeNativeElement?.innerHTML ?? '',
    expectValidate: (): TestRequest => http.expectOne((r) => r.url === VALIDATE_URL),
  };
}

describe('AccountActivation', () => {
  it('shows the terminal invalid-link state and makes no request when the token is missing', async () => {
    const { harness, http, text } = await setup('/activation');
    http.expectNone(() => true);

    expect(text()).toContain("Ce lien d'activation n'est pas valide");
    expect(harness.routeNativeElement?.querySelector('a[href="/login"]')).not.toBeNull();
    http.verify();
  });

  it('reads the token from the query string, validates with it, and clears it from the URL', async () => {
    const { harness, location, html, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);

    const req = expectValidate();
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('token')).toBe(RAW_TOKEN);

    expect(harness.routeNativeElement?.textContent).toContain('Vérification');
    expect(html()).not.toContain(RAW_TOKEN);

    expect(location.path()).toBe('/activation');
    expect(location.path()).not.toContain('token');
    expect(location.path()).not.toContain(RAW_TOKEN);

    req.flush({ valid: true });
    TestBed.inject(HttpTestingController).verify();
  });

  it('shows the password form for a valid invitation, with autocomplete="new-password"', async () => {
    const { harness, http, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);
    expectValidate().flush({ valid: true });
    harness.detectChanges();

    const input = harness.routeNativeElement?.querySelector(
      'input[formcontrolname="password"]',
    ) as HTMLInputElement;
    expect(input).not.toBeNull();
    expect(input.getAttribute('autocomplete')).toBe('new-password');
    http.verify();
  });

  it('maps { valid: false } to the terminal invalid-link state', async () => {
    const { harness, http, text, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);
    expectValidate().flush({ valid: false });
    harness.detectChanges();

    expect(text()).toContain("Ce lien d'activation n'est pas valide");
    expect(harness.routeNativeElement?.querySelector('input[formcontrolname="password"]')).toBeNull();
    http.verify();
  });

  it('offers a retry when validation fails on a server/network error', async () => {
    const { harness, http, text, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);
    expectValidate().flush(null, { status: 503, statusText: 'Service Unavailable' });
    harness.detectChanges();

    expect(text()).toContain('Impossible de vérifier');
    const retry = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find((b) =>
      b.textContent?.includes('Réessayer'),
    );
    expect(retry).toBeDefined();

    retry!.click();
    harness.detectChanges();
    expectValidate().flush({ valid: true });
    harness.detectChanges();
    expect(
      harness.routeNativeElement?.querySelector('input[formcontrolname="password"]'),
    ).not.toBeNull();
    http.verify();
  });

  describe('with a valid invitation', () => {
    it('validates the password: required, min 12, max 200', async () => {
      const { http, internals, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);
      expectValidate().flush({ valid: true });
      const password = internals.form.controls.password;

      expect(password.hasError('required')).toBe(true);
      password.setValue('short');
      expect(password.hasError('minlength')).toBe(true);
      password.setValue('x'.repeat(12));
      expect(password.valid).toBe(true);
      password.setValue('x'.repeat(201));
      expect(password.hasError('maxlength')).toBe(true);
      http.verify();
    });

    it('keeps the submit button disabled while the form is invalid', async () => {
      const { harness, http, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);
      expectValidate().flush({ valid: true });
      harness.detectChanges();

      const button = harness.routeNativeElement?.querySelector(
        'button[type="submit"]',
      ) as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      http.verify();
    });

    it('sends exactly { token, password } and prevents duplicate submissions', async () => {
      const { http, internals, expectValidate } = await setup(`/activation?token=${RAW_TOKEN}`);
      expectValidate().flush({ valid: true });
      internals.form.controls.password.setValue(VALID_PASSWORD);

      internals.submit();
      internals.submit();

      const requests = http.match(ACTIVATE_URL);
      expect(requests.length).toBe(1);
      expect(requests[0].request.body).toEqual({ token: RAW_TOKEN, password: VALID_PASSWORD });
      requests[0].flush(null, { status: 204, statusText: 'No Content' });
      http.verify();
    });

    it('shows a success state with a login link, clears the password, and never logs in', async () => {
      const { harness, http, internals, text, html, expectValidate } = await setup(
        `/activation?token=${RAW_TOKEN}`,
      );
      expectValidate().flush({ valid: true });
      internals.form.controls.password.setValue(VALID_PASSWORD);
      internals.submit();
      http.expectOne(ACTIVATE_URL).flush(null, { status: 204, statusText: 'No Content' });
      harness.detectChanges();

      expect(text()).toContain('Votre compte est activé');
      expect(harness.routeNativeElement?.querySelector('a[href="/login"]')).not.toBeNull();
      expect(internals.form.controls.password.value).toBe('');
      expect(html()).not.toContain(VALID_PASSWORD);

      http.expectNone((r) => r.url.includes('/auth/'));
      expect(localStorage.length).toBe(0);
      expect(sessionStorage.length).toBe(0);
      http.verify();
    });

    it('treats INVITATION_INVALID at submit time as terminal and clears the password', async () => {
      const { harness, http, internals, text, expectValidate } = await setup(
        `/activation?token=${RAW_TOKEN}`,
      );
      expectValidate().flush({ valid: true });
      internals.form.controls.password.setValue(VALID_PASSWORD);
      internals.submit();
      http.expectOne(ACTIVATE_URL).flush(
        {
          status: 400,
          code: 'INVITATION_INVALID',
          message: "Lien d'activation invalide ou expire.",
          path: '',
          correlationId: null,
          details: [],
        },
        { status: 400, statusText: 'Bad Request' },
      );
      harness.detectChanges();

      expect(text()).toContain("Ce lien d'activation n'est pas valide");
      expect(internals.form.controls.password.value).toBe('');
      http.verify();
    });

    it('shows an inline message and keeps the form on a 400 VALIDATION_ERROR', async () => {
      const { harness, http, internals, text, expectValidate } = await setup(
        `/activation?token=${RAW_TOKEN}`,
      );
      expectValidate().flush({ valid: true });
      internals.form.controls.password.setValue(VALID_PASSWORD);
      internals.submit();
      http.expectOne(ACTIVATE_URL).flush(
        {
          status: 400,
          code: 'VALIDATION_ERROR',
          message: 'La requête contient des champs invalides.',
          path: '',
          correlationId: null,
          details: ['password: size must be between 12 and 200'],
        },
        { status: 400, statusText: 'Bad Request' },
      );
      harness.detectChanges();

      expect(text()).toContain('entre 12 et 200 caractères');
      expect(
        harness.routeNativeElement?.querySelector('input[formcontrolname="password"]'),
      ).not.toBeNull();
      http.verify();
    });

    it('stays on the form and allows another attempt after a network failure', async () => {
      const { harness, http, internals, text, expectValidate } = await setup(
        `/activation?token=${RAW_TOKEN}`,
      );
      expectValidate().flush({ valid: true });
      internals.form.controls.password.setValue(VALID_PASSWORD);
      internals.submit();
      http.expectOne(ACTIVATE_URL).error(new ProgressEvent('error'));
      harness.detectChanges();

      expect(text()).toContain('Impossible de joindre le serveur');
      expect(internals.submitting()).toBe(false);
      expect(harness.routeNativeElement?.querySelector('button[type="submit"]')).not.toBeNull();

      internals.submit();
      http.expectOne(ACTIVATE_URL).flush(null, { status: 204, statusText: 'No Content' });
      harness.detectChanges();
      expect(text()).toContain('Votre compte est activé');
      http.verify();
    });

    it('never keeps the invitation token in the rendered DOM or in browser storage', async () => {
      const { harness, http, internals, html, expectValidate } = await setup(
        `/activation?token=${RAW_TOKEN}`,
      );
      expectValidate().flush({ valid: true });
      harness.detectChanges();
      internals.form.controls.password.setValue(VALID_PASSWORD);
      internals.submit();
      http.expectOne(ACTIVATE_URL).flush(null, { status: 204, statusText: 'No Content' });
      harness.detectChanges();

      expect(html()).not.toContain(RAW_TOKEN);
      expect(JSON.stringify(localStorage)).not.toContain(RAW_TOKEN);
      expect(JSON.stringify(sessionStorage)).not.toContain(RAW_TOKEN);
      expect(localStorage.length).toBe(0);
      expect(sessionStorage.length).toBe(0);
      http.verify();
    });
  });
});
