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

  it('confirms the active session and shows the account identity', () => {
    expect(text()).toContain("l'appel d'API authentifié fonctionne");
    expect(text()).toContain('admin@esic.test');
    expect(text()).toContain('public-77');
  });

  it('lists the held roles as chips', () => {
    const chips = fixture.nativeElement.querySelectorAll('.dashboard__role-chip');
    expect(chips.length).toBe(1);
    expect(chips[0].textContent).toContain('Administrateur');
  });

  it('shows the empty state when the account carries no role', () => {
    roles.set([]);
    fixture.detectChanges();
    expect(text()).toContain("Aucun rôle actif n'est associé à votre compte");
  });

  it('filters quick links by role (SCHOOL_ADMINISTRATION sees Apprenants, not Administration)', () => {
    roles.set(['SCHOOL_ADMINISTRATION']);
    fixture.detectChanges();
    const links = fixture.nativeElement as HTMLElement;
    expect(links.querySelector('a[href="/students"]')).not.toBeNull();
    expect(links.querySelector('a[href="/administration"]')).toBeNull();
  });
});
