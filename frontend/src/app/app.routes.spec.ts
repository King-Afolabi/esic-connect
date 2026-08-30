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

  it('declares /administration as a guarded parent with list and detail children', () => {
    const shell = routes.find((r) => r.path === '' && Array.isArray(r.children));
    const admin = shell?.children?.find((c) => c.path === 'administration');
    expect(admin).toBeDefined();
    expect(admin?.canActivate?.length).toBeGreaterThan(0);
    expect(admin?.canActivateChild?.length).toBeGreaterThan(0);
    const childPaths = (admin?.children ?? []).map((c) => c.path);
    expect(childPaths).toContain('');
    expect(childPaths).toContain(':publicId');
  });

  it('declares /students as a guarded parent with list and detail children', () => {
    const shell = routes.find((r) => r.path === '' && Array.isArray(r.children));
    const students = shell?.children?.find((c) => c.path === 'students');
    expect(students).toBeDefined();
    expect(students?.canActivate?.length).toBeGreaterThan(0);
    expect(students?.canActivateChild?.length).toBeGreaterThan(0);
    const childPaths = (students?.children ?? []).map((c) => c.path);
    expect(childPaths).toContain('');
    expect(childPaths).toContain(':publicId');
  });

  it('redirects an anonymous user from a protected route to /login', async () => {
    await router.navigateByUrl('/administration');
    expect(location.path()).toContain('/login');
  });

  it('routes an authenticated TEACHER to /forbidden on the guarded /administration route', async () => {
    signIn(['TEACHER']);
    await router.navigateByUrl('/administration');
    expect(location.path()).toBe('/forbidden');
  });

  it('lets an ADMIN reach the administration route', async () => {
    signIn(['ADMIN']);
    await router.navigateByUrl('/administration');
    expect(location.path()).toBe('/administration');
  });

  it('lets SCHOOL_ADMINISTRATION reach /administration and /students', async () => {
    signIn(['SCHOOL_ADMINISTRATION']);

    await router.navigateByUrl('/administration');
    expect(location.path()).toBe('/administration');

    await router.navigateByUrl('/students');
    expect(location.path()).toBe('/students');
  });

  it('lets SCHOOL_ADMINISTRATION open an account detail route (canActivateChild)', async () => {
    signIn(['SCHOOL_ADMINISTRATION']);
    await router.navigateByUrl('/administration/2f1a9b7c-0000-4000-8000-000000000000');
    expect(location.path()).toBe('/administration/2f1a9b7c-0000-4000-8000-000000000000');
  });

  it('redirects a PEDAGOGICAL_MANAGER away from an account detail route (canActivateChild)', async () => {
    signIn(['PEDAGOGICAL_MANAGER']);
    await router.navigateByUrl('/administration/2f1a9b7c-0000-4000-8000-000000000000');
    expect(location.path()).toBe('/forbidden');
  });

  it('lets SCHOOL_ADMINISTRATION open a student detail route', async () => {
    signIn(['SCHOOL_ADMINISTRATION']);
    await router.navigateByUrl('/students/2f1a9b7c-0000-4000-8000-000000000000');
    expect(location.path()).toBe('/students/2f1a9b7c-0000-4000-8000-000000000000');
  });

  it('redirects a TEACHER away from a student detail route (canActivateChild)', async () => {
    signIn(['TEACHER']);
    await router.navigateByUrl('/students/2f1a9b7c-0000-4000-8000-000000000000');
    expect(location.path()).toBe('/forbidden');
  });

  describe('academic reference routes', () => {
    it('declares /academic as a guarded parent redirecting to academic-years', () => {
      const shell = routes.find((r) => r.path === '' && Array.isArray(r.children));
      const academic = shell?.children?.find((c) => c.path === 'academic');
      expect(academic).toBeDefined();
      expect(academic?.canActivate?.length).toBeGreaterThan(0);
      expect(academic?.canActivateChild?.length).toBeGreaterThan(0);
      const index = (academic?.children ?? []).find((c) => c.path === '');
      expect(index?.redirectTo).toBe('academic-years');
      const childPaths = (academic?.children ?? []).map((c) => c.path);
      expect(childPaths).toEqual(
        expect.arrayContaining([
          'academic-years',
          'academic-years/:publicId',
          'programs',
          'programs/:publicId',
          'program-levels/:publicId',
          'promotions',
          'promotions/:publicId',
          'class-groups',
          'class-groups/:publicId',
        ]),
      );
    });

    it('redirects an anonymous user from /academic to /login', async () => {
      await router.navigateByUrl('/academic/programs');
      expect(location.path()).toContain('/login');
    });

    it('routes an authenticated TEACHER to /forbidden on /academic', async () => {
      signIn(['TEACHER']);
      await router.navigateByUrl('/academic');
      expect(location.path()).toBe('/forbidden');
    });

    it('lets a PEDAGOGICAL_MANAGER browse the academic reference but not /students', async () => {
      signIn(['PEDAGOGICAL_MANAGER']);

      await router.navigateByUrl('/academic/programs');
      expect(location.path()).toBe('/academic/programs');

      await router.navigateByUrl('/students');
      expect(location.path()).toBe('/forbidden');
    });

    it('lets SCHOOL_ADMINISTRATION open an academic detail route (canActivateChild)', async () => {
      signIn(['SCHOOL_ADMINISTRATION']);
      await router.navigateByUrl('/academic/academic-years/2f1a9b7c-0000-4000-8000-000000000000');
      expect(location.path()).toBe('/academic/academic-years/2f1a9b7c-0000-4000-8000-000000000000');
    });
  });

  describe('alternation routes', () => {
    it('declares /alternation as a guarded parent redirecting to patterns', () => {
      const shell = routes.find((r) => r.path === '' && Array.isArray(r.children));
      const alternation = shell?.children?.find((c) => c.path === 'alternation');
      expect(alternation).toBeDefined();
      expect(alternation?.canActivate?.length).toBeGreaterThan(0);
      expect(alternation?.canActivateChild?.length).toBeGreaterThan(0);
      const index = (alternation?.children ?? []).find((c) => c.path === '');
      expect(index?.redirectTo).toBe('patterns');
      const childPaths = (alternation?.children ?? []).map((c) => c.path);
      expect(childPaths).toEqual(
        expect.arrayContaining([
          'patterns',
          'patterns/new',
          'patterns/:publicId',
          'patterns/:publicId/edit',
          'classes',
          'classes/:classPublicId',
          'enrollments',
          'enrollments/:enrollmentPublicId',
        ]),
      );
    });

    it('guards the pattern write routes on ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION only', () => {
      const shell = routes.find((r) => r.path === '' && Array.isArray(r.children));
      const alternation = shell?.children?.find((c) => c.path === 'alternation');
      for (const path of ['patterns/new', 'patterns/:publicId/edit']) {
        const route = (alternation?.children ?? []).find((c) => c.path === path);
        expect(route?.canActivate?.length).toBeGreaterThan(0);
      }
    });

    it('routes an authenticated TEACHER to /forbidden on /alternation', async () => {
      signIn(['TEACHER']);
      await router.navigateByUrl('/alternation');
      expect(location.path()).toBe('/forbidden');
    });

    it('lets a PEDAGOGICAL_MANAGER browse alternation but not the pattern write routes', async () => {
      signIn(['PEDAGOGICAL_MANAGER']);

      await router.navigateByUrl('/alternation/patterns');
      expect(location.path()).toBe('/alternation/patterns');

      await router.navigateByUrl('/alternation/classes');
      expect(location.path()).toBe('/alternation/classes');

      await router.navigateByUrl('/alternation/patterns/new');
      expect(location.path()).toBe('/forbidden');
    });

    it('lets SCHOOL_ADMINISTRATION reach the pattern creation route', async () => {
      signIn(['SCHOOL_ADMINISTRATION']);
      await router.navigateByUrl('/alternation/patterns/new');
      expect(location.path()).toBe('/alternation/patterns/new');
    });

    it('redirects an anonymous user from /alternation to /login', async () => {
      await router.navigateByUrl('/alternation/patterns');
      expect(location.path()).toContain('/login');
    });
  });

  it('keeps an authenticated user away from the guest-only login route', async () => {
    signIn(['STUDENT']);
    await router.navigateByUrl('/login');
    expect(location.path()).toBe('/dashboard');
  });

  describe('public /activation route', () => {
    it('is declared without authGuard, roleGuard or guestGuard', () => {
      const activation = routes.find((r) => r.path === 'activation');
      expect(activation).toBeDefined();
      expect(activation?.canActivate).toBeUndefined();
      expect(activation?.canActivateChild).toBeUndefined();
    });

    it('is reachable by an anonymous visitor', async () => {
      await router.navigateByUrl('/activation?token=raw-secret-value-123');
      expect(location.path().split('?')[0]).toBe('/activation');
    });

    it('is reachable by an authenticated user (no guestGuard redirect)', async () => {
      signIn(['ADMIN']);
      await router.navigateByUrl('/activation?token=raw-secret-value-123');
      expect(location.path().split('?')[0]).toBe('/activation');
    });
  });
});
