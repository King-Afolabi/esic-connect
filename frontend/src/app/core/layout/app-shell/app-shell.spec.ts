import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { Role } from '../../models/role';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  const roles = signal<Role[]>(['ADMIN']);
  const currentUserEmail = signal<string | null>('admin@esic.test');
  const auth = { roles, currentUserEmail, logout: vi.fn() };

  beforeEach(async () => {
    roles.set(['ADMIN']);
    currentUserEmail.set('admin@esic.test');
    auth.logout.mockReset();

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        {
          provide: BreakpointObserver,
          useValue: { observe: () => of({ matches: false, breakpoints: {} }) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const navLinks = () =>
    Array.from(fixture.nativeElement.querySelectorAll('nav a')) as HTMLAnchorElement[];

  it('shows the brand, the current user email and a logout control', () => {
    expect(text()).toContain('ESIC Connect');
    expect(text()).toContain('admin@esic.test');
    expect(text()).toContain('Se déconnecter');
  });

  it('renders only usable screens in the main navigation (currently the dashboard)', () => {
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual(['/dashboard']);
    expect(text()).toContain('Tableau de bord');
  });

  it('does not show the guarded placeholder routes in the navigation, even for an ADMIN', () => {
    const hrefs = navLinks().map((a) => a.getAttribute('href'));
    expect(hrefs).not.toContain('/administration');
    expect(hrefs).not.toContain('/students');
    expect(text()).not.toContain('Administration');
    expect(text()).not.toContain('Apprenants');
  });

  it('keeps the navigation limited to the dashboard for a role with no extra screens', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual(['/dashboard']);
  });

  it('offers no usage-context switch for a single-role account', () => {
    // roles = ['ADMIN'] (beforeEach) → un seul rôle, pas de choix (docs/02 §6.1).
    expect(text()).not.toContain('Contexte :');
  });

  it('shows the usage-context switch when the account carries several roles', () => {
    roles.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    fixture.detectChanges();
    expect(text()).toContain('Contexte :');
    expect(text()).toContain('Gestion pédagogique');
  });

  it('calls AuthService.logout when the logout control is used', () => {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const logoutButton = buttons.find((b) => b.textContent?.includes('Se déconnecter'));
    logoutButton?.click();
    expect(auth.logout).toHaveBeenCalledOnce();
  });
});
