import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { Role } from './core/models/role';
import { Session } from './core/models/session';

/**
 * Vérifie le câblage réel des gardes sur les routes de fonctionnalités :
 * un rôle non autorisé ne peut pas atteindre une route protégée.
 */
describe('application routes (guard wiring)', () => {
  const roles = signal<Role[]>([]);
  const session = signal<Session | null>(null);
  const auth = {
    session,
    roles,
    currentUserEmail: signal<string | null>(null),
    isAuthenticated: () => session() !== null,
    hasAnyRole: (required: readonly Role[]) => required.some((r) => roles().includes(r)),
    logout: vi.fn(),
  };

  let router: Router;
  let location: Location;

  function signIn(held: Role[]): void {
    roles.set(held);
    auth.currentUserEmail.set('user@esic.test');
    session.set({
      accessToken: 't',
      subject: 's',
      roles: held,
      email: 'user@esic.test',
      expiresAt: Date.now() + 600_000,
    });
  }

  beforeEach(async () => {
    roles.set([]);
    session.set(null);
    auth.currentUserEmail.set(null);

    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
  });

  it('still declares the guarded placeholder routes so they stay directly addressable', () => {
    const shell = routes.find((r) => r.path === '' && Array.isArray(r.children));
    const childPaths = (shell?.children ?? []).map((c) => c.path);
    expect(childPaths).toContain('administration');
    expect(childPaths).toContain('students');
    for (const path of ['administration', 'students']) {
      const route = shell?.children?.find((c) => c.path === path);
      expect(route?.canActivate?.length).toBeGreaterThan(0);
    }
  });

  it('redirects an anonymous user from a protected route to /login', async () => {
    await router.navigateByUrl('/administration');
    expect(location.path()).toContain('/login');
  });

  it('routes an authenticated TEACHER to /forbidden on an ADMIN-only route', async () => {
    signIn(['TEACHER']);
    await router.navigateByUrl('/administration');
    expect(location.path()).toBe('/forbidden');
  });

  it('lets an ADMIN reach the administration route', async () => {
    signIn(['ADMIN']);
    await router.navigateByUrl('/administration');
    expect(location.path()).toBe('/administration');
  });

  it('lets SCHOOL_ADMINISTRATION reach /students but not /administration', async () => {
    signIn(['SCHOOL_ADMINISTRATION']);

    await router.navigateByUrl('/students');
    expect(location.path()).toBe('/students');

    await router.navigateByUrl('/administration');
    expect(location.path()).toBe('/forbidden');
  });

  it('keeps an authenticated user away from the guest-only login route', async () => {
    signIn(['STUDENT']);
    await router.navigateByUrl('/login');
    expect(location.path()).toBe('/dashboard');
  });
});
