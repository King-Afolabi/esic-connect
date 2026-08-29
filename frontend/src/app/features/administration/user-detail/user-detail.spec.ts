import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { UserDetailResponse } from '../administration.models';
import { UserDetail } from './user-detail';

@Component({ selector: 'app-list-stub', template: 'list-stub' })
class ListStub {}
@Component({ selector: 'app-dash-stub', template: 'dash-stub' })
class DashStub {}

const ID = '2f1a9b7c-0000-4000-8000-000000000000';
const USER_URL = `/api/v1/users/${ID}`;

const USER: UserDetailResponse = {
  publicId: ID,
  email: 'bruno.leroy@esic.test',
  firstName: 'Bruno',
  lastName: 'Leroy',
  phone: '+33123456789',
  status: 'SUSPENDED',
  emailVerifiedAt: '2026-07-01T09:00:00Z',
  lastLoginAt: '2026-08-10T07:30:00Z',
  suspendedAt: '2026-08-12T12:00:00Z',
  suspensionReason: 'Absence prolongée',
  archivedAt: null,
  createdAt: '2026-06-01T10:00:00Z',
  updatedAt: '2026-08-12T12:00:00Z',
  roleAssignments: [
    { role: 'TEACHER', active: true, validFrom: '2026-06-01T10:00:00Z', validUntil: null },
    {
      role: 'STUDENT',
      active: false,
      validFrom: '2025-09-01T10:00:00Z',
      validUntil: '2026-06-01T10:00:00Z',
    },
  ],
};

async function setup(id = ID) {
  localStorage.clear();
  sessionStorage.clear();

  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'administration', component: ListStub },
        { path: 'administration/:publicId', component: UserDetail },
        { path: 'dashboard', component: DashStub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
    ],
  });

  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/administration/${id}`, UserDetail);
  harness.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  return {
    harness,
    http,
    text: () => harness.routeNativeElement?.textContent ?? '',
    userReq: (): TestRequest => http.expectOne(USER_URL),
  };
}

describe('UserDetail', () => {
  it('shows a loading state, then the account facts once loaded', async () => {
    const { harness, http, text, userReq } = await setup();
    expect(text()).toContain('Chargement de la fiche');

    userReq().flush(USER);
    harness.detectChanges();

    expect(text()).toContain('Bruno Leroy');
    expect(text()).toContain('bruno.leroy@esic.test');
    expect(text()).toContain('Suspendu');
    expect(text()).toContain('Absence prolongée');
    http.verify();
  });

  it('renders the full role history, active and closed', async () => {
    const { harness, http, text, userReq } = await setup();
    userReq().flush(USER);
    harness.detectChanges();

    expect(text()).toContain('Historique des rôles');
    expect(text()).toContain('Formateur');
    expect(text()).toContain('Apprenant');
    expect(text()).toContain('Actif');
    expect(text()).toContain('Clôturé');
    http.verify();
  });

  it('shows an empty message when the account never had a role', async () => {
    const { harness, http, text, userReq } = await setup();
    userReq().flush({ ...USER, roleAssignments: [] });
    harness.detectChanges();

    expect(text()).toContain("Aucun rôle n'a jamais été attribué");
    expect(harness.routeNativeElement?.querySelector('table')).toBeNull();
    http.verify();
  });

  it('renders a not-found panel on a 404 and makes no further calls', async () => {
    const { harness, http, text, userReq } = await setup();
    userReq().flush(
      { status: 404, code: 'USER_NOT_FOUND', message: 'x', path: '', correlationId: null, details: [] },
      { status: 404, statusText: 'Not Found' },
    );
    harness.detectChanges();

    expect(text()).toContain('Aucun compte ne correspond');
    expect(harness.routeNativeElement?.querySelector('a[href="/administration"]')).not.toBeNull();
    http.expectNone(() => true);
    http.verify();
  });

  it('renders an access-denied panel on a 403', async () => {
    const { harness, http, text, userReq } = await setup();
    userReq().flush(
      {
        status: 403,
        code: 'USER_OPERATION_FORBIDDEN',
        message: 'x',
        path: '',
        correlationId: null,
        details: [],
      },
      { status: 403, statusText: 'Forbidden' },
    );
    harness.detectChanges();

    expect(text()).toContain("Vous n'êtes pas autorisé à consulter cette fiche");
    http.verify();
  });

  it('lets the user retry after a generic error', async () => {
    const { harness, http, text, userReq } = await setup();
    userReq().flush(null, { status: 500, statusText: 'Server Error' });
    harness.detectChanges();

    expect(text()).toContain('Une erreur est survenue');
    const retry = [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find((b) =>
      b.textContent?.includes('Réessayer'),
    ) as HTMLButtonElement;
    retry.click();
    userReq().flush(USER);
    harness.detectChanges();

    expect(text()).toContain('Bruno Leroy');
    http.verify();
  });

  it('never issues a write request and writes nothing to browser storage', async () => {
    const { harness, http, userReq } = await setup();
    userReq().flush(USER);
    harness.detectChanges();

    http.expectNone((r) => r.method === 'POST');
    http.expectNone((r) => r.method === 'PATCH');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
});
