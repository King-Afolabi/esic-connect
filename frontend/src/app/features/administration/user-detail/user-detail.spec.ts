import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { AuthService } from '../../../core/auth/auth.service';
import { RoleContextService } from '../../../core/auth/role-context.service';
import { Role } from '../../../core/models/role';
import { Session } from '../../../core/models/session';
import { NotificationService } from '../../../core/notifications/notification.service';
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
  status: 'ACTIVE',
  emailVerifiedAt: '2026-07-01T09:00:00Z',
  lastLoginAt: '2026-08-10T07:30:00Z',
  suspendedAt: null,
  suspensionReason: null,
  archivedAt: null,
  createdAt: '2026-06-01T10:00:00Z',
  updatedAt: '2026-08-12T12:00:00Z',
  roleAssignments: [
    { role: 'TEACHER', active: true, validFrom: '2026-06-01T10:00:00Z', validUntil: null },
    { role: 'PEDAGOGICAL_MANAGER', active: true, validFrom: '2026-06-01T10:00:00Z', validUntil: null },
    {
      role: 'STUDENT',
      active: false,
      validFrom: '2025-09-01T10:00:00Z',
      validUntil: '2026-06-01T10:00:00Z',
    },
  ],
};

interface Internals {
  startAction: (k: 'suspend' | 'restore' | 'archive') => void;
  startAssign: () => void;
  startRevoke: (role: string) => void;
  cancelAction: () => void;
  confirm: () => void;
  reasonForm: { setValue: (v: { reason: string }) => void };
  assignForm: { setValue: (v: { role: string; reason: string }) => void };
  assignableRoleOptions: () => Role[];
  pending: () => unknown;
}

const notifications = { info: vi.fn(), error: vi.fn() };

async function setup(opts?: { user?: UserDetailResponse; effectiveRoles?: Role[]; subject?: string | null }) {
  // Allow more than one setup() per test (permission matrices compare roles).
  TestBed.resetTestingModule();
  localStorage.clear();
  sessionStorage.clear();
  notifications.info.mockReset();
  notifications.error.mockReset();

  const effectiveRoles = signal<Role[]>(opts?.effectiveRoles ?? []);
  const session = signal<Session | null>({
    accessToken: 't',
    subject: opts?.subject ?? 'caller-000',
    roles: [],
    email: 'caller@esic.test',
    expiresAt: Date.now() + 1_000_000,
  });

  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'administration', component: ListStub },
        { path: 'administration/:publicId', component: UserDetail },
        { path: 'dashboard', component: DashStub },
      ]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { session } },
      { provide: RoleContextService, useValue: { effectiveRoles } },
      { provide: NotificationService, useValue: notifications },
    ],
  });

  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(`/administration/${ID}`, UserDetail);
  harness.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  const flushUser = (user: UserDetailResponse = opts?.user ?? USER) => {
    http.expectOne(USER_URL).flush(user);
    harness.detectChanges();
  };

  return {
    harness,
    http,
    effectiveRoles,
    session,
    flushUser,
    text: () => harness.routeNativeElement?.textContent ?? '',
    el: () => harness.routeNativeElement as HTMLElement,
    button: (label: string) =>
      [...(harness.routeNativeElement?.querySelectorAll('button') ?? [])].find((b) =>
        b.textContent?.includes(label),
      ) as HTMLButtonElement | undefined,
    userReq: (): TestRequest => http.expectOne(USER_URL),
    internals: () => harness.routeDebugElement?.componentInstance as unknown as Internals,
  };
}

describe('UserDetail — read view (non-regression)', () => {
  it('shows a loading state, then the account facts once loaded', async () => {
    const { harness, http, text, userReq } = await setup();
    expect(text()).toContain('Chargement de la fiche');

    userReq().flush(USER);
    harness.detectChanges();

    expect(text()).toContain('Bruno Leroy');
    expect(text()).toContain('bruno.leroy@esic.test');
    expect(text()).toContain('Actif');
    http.verify();
  });

  it('renders the full role history, active and closed', async () => {
    const { flushUser, text, http } = await setup();
    flushUser();
    expect(text()).toContain('Historique des rôles');
    expect(text()).toContain('Formateur');
    expect(text()).toContain('Apprenant');
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
    http.expectNone(() => true);
    http.verify();
  });

  it('renders an access-denied panel on a 403', async () => {
    const { harness, http, text, userReq } = await setup();
    userReq().flush(
      { status: 403, code: 'USER_OPERATION_FORBIDDEN', message: 'x', path: '', correlationId: null, details: [] },
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

  it('issues no write request on load and writes nothing to browser storage', async () => {
    const { flushUser, http } = await setup({ effectiveRoles: ['ADMIN'] });
    flushUser();

    http.expectNone((r) => r.method === 'POST');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
});

describe('UserDetail — lifecycle actions', () => {
  it('offers Suspendre for an ACTIVE target and not Réactiver', async () => {
    const { flushUser, text } = await setup({ effectiveRoles: ['ADMIN'] });
    flushUser();
    expect(text()).toContain('Suspendre le compte');
    expect(text()).not.toContain('Réactiver le compte');
  });

  it('offers Réactiver for a SUSPENDED target', async () => {
    const { flushUser, text } = await setup({ effectiveRoles: ['ADMIN'] });
    flushUser({ ...USER, status: 'SUSPENDED' });
    expect(text()).toContain('Réactiver le compte');
    expect(text()).not.toContain('Suspendre le compte');
  });

  it('shows archive + role assignment only for ADMIN/SUPER_ADMIN', async () => {
    const admin = await setup({ effectiveRoles: ['ADMIN'] });
    admin.flushUser();
    expect(admin.text()).toContain('Archiver le compte');
    expect(admin.text()).toContain('Attribuer un rôle');

    const school = await setup({ effectiveRoles: ['SCHOOL_ADMINISTRATION'] });
    school.flushUser();
    expect(school.text()).toContain('Suspendre le compte');
    expect(school.text()).not.toContain('Archiver le compte');
    expect(school.text()).not.toContain('Attribuer un rôle');
  });

  it('shows no mutation form for an ARCHIVED target, only a terminal-state note', async () => {
    const { flushUser, text, el } = await setup({ effectiveRoles: ['SUPER_ADMIN'] });
    flushUser({ ...USER, status: 'ARCHIVED', archivedAt: '2026-08-20T09:00:00Z' });
    expect(text()).toContain('archivé');
    expect(text()).toContain('état terminal');
    expect(text()).not.toContain('Actions sur le compte');
    expect(el().querySelector('form')).toBeNull();
  });

  it('requires a reason (max 500) and posts the exact body, then reloads', async () => {
    const { harness, http, internals, userReq, button } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startAction('suspend');
    harness.detectChanges();

    const textarea = harness.routeNativeElement?.querySelector('textarea') as HTMLTextAreaElement;
    expect(textarea.getAttribute('maxlength')).toBe('500');

    // Empty reason: submit is a no-op.
    internals().confirm();
    http.expectNone((r) => r.method === 'POST');

    internals().reasonForm.setValue({ reason: '  compte inactif  ' });
    internals().confirm();

    const req = http.expectOne(`${USER_URL}/suspend`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'compte inactif' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    userReq().flush({ ...USER, status: 'SUSPENDED' });
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalledWith('Compte suspendu.');
    expect(button('Réactiver le compte')).toBeDefined();
    http.verify();
  });

  it('warns that archiving closes active roles and prevents a double submit', async () => {
    const { harness, http, internals, userReq } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startAction('archive');
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain('clôture immédiatement tous les rôles actifs');

    internals().reasonForm.setValue({ reason: 'fin de parcours' });
    internals().confirm();
    internals().confirm(); // ignored while submitting

    const req = http.expectOne(`${USER_URL}/archive`);
    expect(req.request.body).toEqual({ reason: 'fin de parcours' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    userReq().flush({ ...USER, status: 'ARCHIVED', archivedAt: '2026-08-30T00:00:00Z' });
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalledTimes(1);
    http.verify();
  });

  it('shows the failure inline without a false success and without reloading (USER_INVALID_STATE)', async () => {
    const { harness, http, internals, userReq } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startAction('suspend');
    harness.detectChanges();
    internals().reasonForm.setValue({ reason: 'motif' });
    internals().confirm();

    http.expectOne(`${USER_URL}/suspend`).flush(
      {
        status: 409,
        code: 'USER_INVALID_STATE',
        message: "L'état actuel du compte ne permet pas cette opération.",
        path: '',
        correlationId: null,
        details: [],
      },
      { status: 409, statusText: 'Conflict' },
    );
    harness.detectChanges();

    expect(harness.routeNativeElement?.textContent).toContain("L'état actuel du compte ne permet pas");
    expect(notifications.info).not.toHaveBeenCalled();
    expect(internals().pending()).not.toBeNull();
    http.expectNone((r) => r.method === 'GET'); // no reload
    http.verify();
  });

  it('surfaces the controlled message for a 409 self-action and a 403 super-admin protection', async () => {
    const self = await setup({ effectiveRoles: ['ADMIN'], subject: ID });
    self.userReq().flush(USER);
    self.harness.detectChanges();
    // Self target: lifecycle buttons are hidden; drive the guarded path directly.
    self.internals().startAction('archive');
    self.harness.detectChanges();
    await self.harness.fixture.whenStable();
    // Effect closes it because the action is no longer offered for self.
    expect(self.internals().pending()).toBeNull();
    self.http.verify();

    const other = await setup({ effectiveRoles: ['ADMIN'] });
    other.userReq().flush(USER);
    other.harness.detectChanges();
    other.internals().startAction('suspend');
    other.harness.detectChanges();
    other.internals().reasonForm.setValue({ reason: 'x' });
    other.internals().confirm();
    other.http.expectOne(`${USER_URL}/suspend`).flush(
      { status: 403, code: 'USER_SUPER_ADMIN_PROTECTED', message: 'Cette opération requiert le rôle super administrateur.', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    other.harness.detectChanges();
    expect(other.harness.routeNativeElement?.textContent).toContain('super administrateur');
    other.http.verify();
  });
});

describe('UserDetail — role assignment', () => {
  it('offers only the contractual roles, excluding already-active ones, SUPER_ADMIN gated by context', async () => {
    const asAdmin = await setup({ effectiveRoles: ['ADMIN'] });
    asAdmin.userReq().flush(USER);
    asAdmin.harness.detectChanges();
    asAdmin.internals().startAssign();
    asAdmin.harness.detectChanges();
    // Active on USER: TEACHER, PEDAGOGICAL_MANAGER → excluded.
    expect(asAdmin.internals().assignableRoleOptions()).toEqual([
      'ADMIN',
      'SCHOOL_ADMINISTRATION',
      'STUDENT',
    ]);
    asAdmin.http.verify();

    const asSuper = await setup({ effectiveRoles: ['SUPER_ADMIN'] });
    asSuper.userReq().flush(USER);
    asSuper.harness.detectChanges();
    asSuper.internals().startAssign();
    asSuper.harness.detectChanges();
    expect(asSuper.internals().assignableRoleOptions()).toContain('SUPER_ADMIN');
    asSuper.http.verify();
  });

  it('requires role + reason, posts { role, reason } and reloads on success', async () => {
    const { harness, http, internals, userReq } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startAssign();
    harness.detectChanges();
    internals().confirm(); // invalid (no role, no reason)
    http.expectNone((r) => r.method === 'POST');

    internals().assignForm.setValue({ role: 'SCHOOL_ADMINISTRATION', reason: 'renfort' });
    internals().confirm();
    internals().confirm(); // double submit ignored

    const req = http.expectOne(`${USER_URL}/roles`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ role: 'SCHOOL_ADMINISTRATION', reason: 'renfort' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    userReq().flush(USER);
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalledWith('Rôle attribué.');
    expect(internals().pending()).toBeNull();
    http.verify();
  });

  it('shows USER_ROLE_ALREADY_ASSIGNED globally and USER_ROLE_UNKNOWN near the role field', async () => {
    const { harness, http, internals, userReq, el } = await setup({ effectiveRoles: ['SUPER_ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startAssign();
    harness.detectChanges();
    internals().assignForm.setValue({ role: 'STUDENT', reason: 'x' });
    internals().confirm();
    http.expectOne(`${USER_URL}/roles`).flush(
      { status: 400, code: 'USER_ROLE_UNKNOWN', message: 'Code de rôle inconnu.', path: '', correlationId: null, details: [] },
      { status: 400, statusText: 'Bad Request' },
    );
    harness.detectChanges();
    // USER_ROLE_UNKNOWN is attached to the role field, not shown as a global error.
    expect(el().textContent).toContain('Code de rôle inconnu.');
    const comp = internals() as unknown as {
      roleFieldError: () => string | null;
      actionError: () => string | null;
    };
    expect(comp.roleFieldError()).toBe('Code de rôle inconnu.');
    expect(comp.actionError()).toBeNull();

    internals().confirm();
    http.expectOne(`${USER_URL}/roles`).flush(
      { status: 409, code: 'USER_ROLE_ALREADY_ASSIGNED', message: 'Ce rôle est déjà actif pour ce compte.', path: '', correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    harness.detectChanges();
    expect(el().textContent).toContain('Ce rôle est déjà actif');
    http.verify();
  });

  it('is still offered on the user’s own account (backend does not forbid self-assignment)', async () => {
    const { flushUser, text, internals } = await setup({ effectiveRoles: ['ADMIN'], subject: ID });
    flushUser();
    expect(text()).toContain('Attribuer un rôle');
    internals().startAssign();
    expect(internals().pending()).not.toBeNull();
  });
});

describe('UserDetail — role revocation', () => {
  it('shows a Retirer action only on active assignments, for ADMIN/SUPER_ADMIN', async () => {
    const { flushUser, el } = await setup({ effectiveRoles: ['ADMIN'] });
    flushUser();
    const revokeButtons = [...el().querySelectorAll('button')].filter((b) =>
      b.textContent?.trim().startsWith('Retirer'),
    );
    // 2 active roles on USER (TEACHER, PEDAGOGICAL_MANAGER); the closed STUDENT row has none.
    expect(revokeButtons.length).toBe(2);
  });

  it('hides Retirer for a read-only role context', async () => {
    const { flushUser, el } = await setup({ effectiveRoles: ['SCHOOL_ADMINISTRATION'] });
    flushUser();
    expect([...el().querySelectorAll('button')].some((b) => b.textContent?.includes('Retirer'))).toBe(
      false,
    );
  });

  it('posts to /roles/{code}/revoke with a mandatory reason, mentions history is kept, reloads', async () => {
    const { harness, http, internals, userReq, el } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startRevoke('TEACHER');
    harness.detectChanges();
    expect(el().textContent).toContain('historique est conservé');

    internals().confirm(); // no reason → no-op
    http.expectNone((r) => r.method === 'POST');

    internals().reasonForm.setValue({ reason: 'fin de contrat' });
    internals().confirm();
    const req = http.expectOne(`${USER_URL}/roles/TEACHER/revoke`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'fin de contrat' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    userReq().flush(USER);
    harness.detectChanges();
    expect(notifications.info).toHaveBeenCalledWith('Rôle retiré.');
    http.verify();
  });

  it('surfaces USER_LAST_ACTIVE_ROLE without a false success', async () => {
    const { harness, http, internals, userReq, el } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();
    internals().startRevoke('PEDAGOGICAL_MANAGER');
    harness.detectChanges();
    internals().reasonForm.setValue({ reason: 'x' });
    internals().confirm();
    http.expectOne(`${USER_URL}/roles/PEDAGOGICAL_MANAGER/revoke`).flush(
      { status: 409, code: 'USER_LAST_ACTIVE_ROLE', message: "Impossible de retirer le dernier rôle actif d'un compte.", path: '', correlationId: null, details: [] },
      { status: 409, statusText: 'Conflict' },
    );
    harness.detectChanges();
    expect(el().textContent).toContain('dernier rôle actif');
    expect(notifications.info).not.toHaveBeenCalled();
    http.verify();
  });

  it('does not offer to revoke SUPER_ADMIN unless the context is SUPER_ADMIN', async () => {
    const withSuper: UserDetailResponse = {
      ...USER,
      roleAssignments: [
        { role: 'SUPER_ADMIN', active: true, validFrom: '2026-01-01T00:00:00Z', validUntil: null },
        { role: 'ADMIN', active: true, validFrom: '2026-01-01T00:00:00Z', validUntil: null },
      ],
    };

    const asAdmin = await setup({ effectiveRoles: ['ADMIN'] });
    asAdmin.flushUser(withSuper);
    const adminButtons = [...asAdmin.el().querySelectorAll('button')].filter((b) =>
      b.textContent?.trim().startsWith('Retirer'),
    );
    // Only the ADMIN row is revocable, not SUPER_ADMIN.
    expect(adminButtons.length).toBe(1);

    const asSuper = await setup({ effectiveRoles: ['SUPER_ADMIN'] });
    asSuper.flushUser(withSuper);
    const superButtons = [...asSuper.el().querySelectorAll('button')].filter((b) =>
      b.textContent?.trim().startsWith('Retirer'),
    );
    expect(superButtons.length).toBe(2);
  });
});

describe('UserDetail — session, permissions and context', () => {
  it('hides self-actions when the JWT subject matches the target, keeps role assignment', async () => {
    const { flushUser, text } = await setup({ effectiveRoles: ['ADMIN'], subject: ID });
    flushUser();
    expect(text()).not.toContain('Suspendre le compte');
    expect(text()).not.toContain('Archiver le compte');
    expect(text()).not.toContain('Retirer');
    expect(text()).toContain('Attribuer un rôle');
    expect(text()).toContain('votre propre compte');
  });

  it('a non-admin context never unlocks admin-only actions', async () => {
    const { flushUser, text } = await setup({ effectiveRoles: ['TEACHER'] });
    flushUser();
    expect(text()).not.toContain('Actions sur le compte');
    expect(text()).not.toContain('Attribuer un rôle');
  });

  it('closes an open sensitive form when the active role context drops the permission', async () => {
    const { harness, effectiveRoles, internals, userReq, text } = await setup({
      effectiveRoles: ['ADMIN'],
    });
    userReq().flush(USER);
    harness.detectChanges();

    internals().startAction('suspend');
    harness.detectChanges();
    expect(internals().pending()).not.toBeNull();

    effectiveRoles.set(['STUDENT']);
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(internals().pending()).toBeNull();
    expect(text()).not.toContain('Actions sur le compte');
  });

  it('still handles a 403 even when the button was initially visible; nothing in storage', async () => {
    const { harness, http, internals, userReq, el } = await setup({ effectiveRoles: ['ADMIN'] });
    userReq().flush(USER);
    harness.detectChanges();
    internals().startAction('archive');
    harness.detectChanges();
    internals().reasonForm.setValue({ reason: 'x' });
    internals().confirm();
    http.expectOne(`${USER_URL}/archive`).flush(
      { status: 403, code: 'USER_OPERATION_FORBIDDEN', message: 'Accès refusé.', path: '', correlationId: null, details: [] },
      { status: 403, statusText: 'Forbidden' },
    );
    harness.detectChanges();
    expect(el().textContent).toContain('Accès refusé.');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    http.verify();
  });
});
