import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { Role } from '../../models/role';
import { NotificationsBadgeService } from '../../../features/notifications/notifications-badge.service';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  const roles = signal<Role[]>(['ADMIN']);
  const currentUserEmail = signal<string | null>('admin@esic.test');
  // `session` / `refreshSession` / `expireSession` : requis par
  // SessionActivityService, armé par la coquille (Lot A).
  const auth = {
    roles,
    currentUserEmail,
    logout: vi.fn(),
    session: () => null,
    refreshSession: vi.fn(),
    expireSession: vi.fn(),
  };

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
          provide: NotificationsBadgeService,
          useValue: { unread: signal(0), refresh: vi.fn() },
        },
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

  it('shows the brand, a compact profile control and a logout control', () => {
    expect(text()).toContain('ESIC Connect');
    // L'adresse complète est passée dans le panneau « Profil » (Lot §3) ;
    // l'en-tête ne garde qu'un identifiant court + l'adresse en aria-label.
    expect(text()).toContain('admin');
    const trigger = (fixture.nativeElement as HTMLElement).querySelector(
      'app-profile-menu button[aria-haspopup="menu"]',
    );
    expect(trigger?.getAttribute('aria-label')).toContain('admin@esic.test');
    expect(text()).toContain('Se déconnecter');
  });

  it('renders the dashboard and every delivered screen for an ADMIN', () => {
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual([
      '/dashboard',
      '/administration',
      '/students',
      // ANO-NAV-001 : « Import apprenants » n'est plus une entrée racine
      // pour les rôles d'administration — l'import est atteint depuis
      // l'en-tête de la liste des apprenants ; l'entrée racine ne subsiste
      // que pour le PEDAGOGICAL_MANAGER (voir son test dédié).
      // Regroupement (Lot §4) : une seule entrée pour référentiels,
      // organisation, planning et alternance.
      '/organisation-planning',
      '/sessions',
      // Réclamations livrées au sprint 9 (EF-CLAIM-001..004) : visibles
      // de tous les rôles, chacun n'y voyant que son propre périmètre.
      '/claims',
      '/attendance-management',
      '/notifications',
      // Recherche globale, attestations, abonnement calendrier et piste
      // d'audit : livrés au sprint 11 (EF-USER-009, EF-REP-006,
      // EF-INT-001, EF-AUD-002). « Invitations non activées » (EF-REP-010)
      // n'est plus une entrée racine : c'est une vue de « Invitations »
      // (ANO-NAV-001).
      '/recherche',
      '/attestations',
      '/mon-compte/calendrier',
      '/exploitation/audit',
      '/pedagogical-assignments',
      // File d'échec des effets de bord (sprint 10, EF-OPS-005) :
      // réservée à `ADMIN` / `SUPER_ADMIN`, comme le contrôleur.
      '/exploitation/effets-de-bord',
      '/subjects',
      '/invitations',
      '/mon-compte/securite',
    ]);
    expect(text()).toContain('Tableau de bord');
    expect(text()).toContain('Administration');
    expect(text()).toContain('Apprenants');
    expect(text()).toContain('Organisation & planning');
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

  it('shows a TEACHER the dashboard, their scoped Apprenants, Séances (their own sessions server-side) and Notifications', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual([
      '/dashboard',
      // Consultation des apprenants de ses classes (EnrollmentWeb.READ_ROLES,
      // périmètre restreint côté serveur par RosterScopeResolver).
      '/students',
      '/sessions',
      '/claims',
      '/notifications',
      // « Mon planning » (CDC §5.6/§5.7) : consultation en application,
      // réservée à TEACHER/STUDENT.
      '/mon-planning',
      // Abonnement iCalendar (sprint 11, EF-INT-001) : propre à chaque
      // personne, donc visible quel que soit le rôle. Le formateur y
      // trouve ses séances, l'apprenant les siennes.
      '/mon-compte/calendrier',
      // Le catalogue des matières est ouvert en lecture au formateur :
      // il en a besoin pour qualifier une séance (EF-ACA-006).
      '/subjects',
      '/mon-compte/securite',
    ]);
  });

  it('shows a STUDENT the dashboard, Émargement, Mes présences and Notifications', () => {
    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).toEqual([
      '/dashboard',
      '/attendance',
      '/my-attendance',
      // Livrés au sprint 9 : journal de transparence (EF-ATT-014) et
      // départ anticipé (EF-ATT-013), réservés à l'apprenant.
      '/my-attendance/transparency',
      '/my-attendance/early-departures',
      '/claims',
      '/notifications',
      // « Mon planning » (CDC §5.6/§5.7) : consultation en application,
      // réservée à TEACHER/STUDENT.
      '/mon-planning',
      // Abonnement iCalendar (sprint 11, EF-INT-001).
      '/mon-compte/calendrier',
      '/mon-compte/securite',
    ]);
  });

  it('shows a PEDAGOGICAL_MANAGER a scoped Apprenants entry (no import root entry) and Organisation & planning', () => {
    roles.set(['PEDAGOGICAL_MANAGER']);
    fixture.detectChanges();
    const hrefs = navLinks().map((a) => a.getAttribute('href'));
    // Il voit « Apprenants » — restreint à ses formations côté serveur
    // (RosterScopeResolver). L'import n'a plus d'entrée racine : il est
    // atteint depuis l'en-tête de la liste.
    expect(hrefs).toContain('/students');
    expect(hrefs).not.toContain('/students/import');
    expect(text()).toContain('Apprenants');
    expect(hrefs).toContain('/organisation-planning');
  });

  it('hides Organisation & planning for a role outside its sub-section read roles', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).not.toContain('/organisation-planning');

    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect(navLinks().map((a) => a.getAttribute('href'))).not.toContain('/organisation-planning');
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
