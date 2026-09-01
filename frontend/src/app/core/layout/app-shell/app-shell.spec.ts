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

  it('renders the dashboard and the delivered Administration / Apprenants / Import / Référentiels / Organisation / Planning / Alternance / Séances screens for an ADMIN', () => {
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual([
      '/dashboard',
      '/administration',
      '/students',
      '/students/import',
      '/academic',
      '/organization',
      '/planning',
      '/alternation',
      '/sessions',
      '/attendance-management',
    ]);
    expect(text()).toContain('Tableau de bord');
    expect(text()).toContain('Administration');
    expect(text()).toContain('Apprenants');
    expect(text()).toContain('Import apprenants');
    expect(text()).toContain('Référentiels');
    expect(text()).toContain('Organisation');
    expect(text()).toContain('Planning');
    expect(text()).toContain('Alternance');
    expect(text()).toContain('Séances');
  });

  it('shows Administration for SCHOOL_ADMINISTRATION but hides it from a STUDENT', () => {
    roles.set(['SCHOOL_ADMINISTRATION']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).toContain('/administration');

    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).not.toContain('/administration');
  });

  it('shows a TEACHER only the dashboard and Séances (their own sessions server-side)', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual(['/dashboard', '/sessions']);
  });

  it('shows a STUDENT only the dashboard and Émargement', () => {
    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual([
      '/dashboard',
      '/attendance',
      '/my-attendance',
    ]);
  });

  it('hides Apprenants but shows Import apprenants, Référentiels and Alternance for a PEDAGOGICAL_MANAGER', () => {
    roles.set(['PEDAGOGICAL_MANAGER']);
    fixture.detectChanges();
    const hrefs = navLinks().map((a) => a.getAttribute('href'));
    expect(hrefs).not.toContain('/students');
    expect(hrefs).toContain('/students/import');
    expect(hrefs).toContain('/academic');
    expect(hrefs).toContain('/alternation');
  });

  it('hides Alternance for a role outside the alternation read roles', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).not.toContain('/alternation');
  });

  it('hides Référentiels for a role outside AcademicWeb.READ_ROLES', () => {
    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).not.toContain('/academic');
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
