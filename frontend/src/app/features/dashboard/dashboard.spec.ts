import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Role } from '../../core/models/role';
import { Session } from '../../core/models/session';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  const session = signal<Session | null>(null);
  const roles = signal<Role[]>([]);

  beforeEach(async () => {
    session.set({
      accessToken: 't',
      subject: 'public-77',
      roles: ['ADMIN'],
      email: 'admin@esic.test',
      expiresAt: Date.now() + 600_000,
    });
    roles.set(['ADMIN']);

    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { session, roles } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('reports a local active session and shows the account identity', () => {
    expect(text()).toContain('Votre session locale est active');
    expect(text()).toContain('admin@esic.test');
    expect(text()).toContain('public-77');
  });

  it('does not claim that a second authenticated API call was verified', () => {
    expect(text()).not.toContain("appel d'API");
    expect(text()).not.toContain('API authentifié');
    expect(text()).not.toContain('/auth/me');
  });

  it('lists the held roles as chips', () => {
    const chips = fixture.nativeElement.querySelectorAll('.dashboard__role-chip');
    expect(chips.length).toBe(1);
    expect(chips[0].textContent).toContain('Administrateur');
  });

  it('does not mention a usage context for a single-role account', () => {
    // roles = ['ADMIN'] (beforeEach) → aucun choix de contexte.
    expect(text()).not.toContain('Contexte actif');
  });

  it('reports the active usage context when the account carries several roles', () => {
    roles.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    fixture.detectChanges();
    expect(text()).toContain('Contexte actif');
    expect(text()).toContain('Gestion pédagogique');
    expect(text()).toContain('vos autorisations restent inchangées');
  });

  it('shows the empty state when the account carries no role', () => {
    roles.set([]);
    fixture.detectChanges();
    expect(text()).toContain("Aucun rôle actif n'est associé à votre compte");
  });

  it('never exposes the /administration placeholder route as a quick link, whatever the role', () => {
    for (const held of [['ADMIN'], ['SUPER_ADMIN'], ['SCHOOL_ADMINISTRATION'], ['TEACHER']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;
      expect(root.querySelector('a[href="/administration"]')).toBeNull();
    }
  });

  it('offers Apprenants as a quick link only for the roles behind EnrollmentWeb.MANAGE_ROLES', () => {
    for (const held of [['ADMIN'], ['SUPER_ADMIN'], ['SCHOOL_ADMINISTRATION']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/students"]'),
      ).not.toBeNull();
    }
    for (const held of [['TEACHER'], ['PEDAGOGICAL_MANAGER'], ['STUDENT']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/students"]'),
      ).toBeNull();
    }
  });

  it('offers Référentiels as a quick link only for the roles behind AcademicWeb.READ_ROLES', () => {
    for (const held of [
      ['ADMIN'],
      ['SUPER_ADMIN'],
      ['SCHOOL_ADMINISTRATION'],
      ['PEDAGOGICAL_MANAGER'],
    ] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/academic"]'),
      ).not.toBeNull();
    }
    for (const held of [['TEACHER'], ['STUDENT']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/academic"]'),
      ).toBeNull();
    }
  });

  it('shows the quick-links empty state for a role with no delivered secondary screen', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(text()).toContain("Aucun autre écran n'est disponible");
  });
});
