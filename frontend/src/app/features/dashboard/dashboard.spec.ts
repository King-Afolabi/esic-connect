import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { RoleContextService } from '../../core/auth/role-context.service';
import { Role } from '../../core/models/role';
import { Session } from '../../core/models/session';
import { Dashboard } from './dashboard';

const DASH_URL = '/api/v1/me/dashboard';
const EMPTY_ADMIN_DASH = {
  role: 'ADMINISTRATION',
  generatedAt: '2026-09-10T09:00:00Z',
  student: null,
  teacher: null,
  manager: null,
  administration: {
    activeAccounts: 12,
    suspendedAccounts: 1,
    pendingActivation: 3,
    archivedAccounts: 0,
    expiredInvitations: 4,
    pendingJustifications: 2,
    decidedJustifications: 0,
    medianDecisionDelayHours: null,
    periodFrom: '2026-08-11T09:00:00Z',
    periodTo: '2026-09-10T09:00:00Z',
    globalAttendanceRate: 0.8125,
    programRates: [],
    recentImports: [],
    todaySessions: [],
    recentExports: [],
    recentAuditOperations: [],
  },
  notes: [],
};

describe('Dashboard', () => {
  let fixture: ComponentFixture<Dashboard>;
  let http: HttpTestingController;
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
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { session, roles } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Dashboard);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(DASH_URL).flush(EMPTY_ADMIN_DASH);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

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
    // Le changement de contexte recharge le tableau de bord avec ?context=.
    http
      .expectOne((r) => r.url === DASH_URL && r.params.get('context') === 'PEDAGOGICAL_MANAGER')
      .flush(EMPTY_ADMIN_DASH);
    fixture.detectChanges();
    expect(text()).toContain('Contexte actif');
    expect(text()).toContain('Gestion pédagogique');
    expect(text()).toContain('vos autorisations restent inchangées');
  });

  it('sends ?context for a multi-role account and switches it when the context changes', () => {
    roles.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    fixture.detectChanges();
    // Contexte par défaut = rôle le plus prioritaire détenu.
    http
      .expectOne((r) => r.url === DASH_URL && r.params.get('context') === 'PEDAGOGICAL_MANAGER')
      .flush({ ...EMPTY_ADMIN_DASH, role: 'PEDAGOGICAL_MANAGER' });
    fixture.detectChanges();

    // L'utilisateur bascule vers son contexte formateur.
    TestBed.inject(RoleContextService).select('TEACHER');
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === DASH_URL && r.params.get('context') === 'TEACHER')
      .flush({ ...EMPTY_ADMIN_DASH, role: 'TEACHER', teacher: null, administration: null });
    fixture.detectChanges();
    expect(text()).toContain('Mes séances de formateur');
  });

  it('never sends a context a mono-role account does not hold', () => {
    // beforeEach: roles = ['ADMIN'] (mono-rôle) → aucun paramètre context.
    (fixture.componentInstance as unknown as { loadDashboard: () => void }).loadDashboard();
    http.expectOne((r) => r.url === DASH_URL && !r.params.has('context')).flush(EMPTY_ADMIN_DASH);
    fixture.detectChanges();
  });

  it('shows the empty state when the account carries no role', () => {
    roles.set([]);
    fixture.detectChanges();
    expect(text()).toContain("Aucun rôle actif n'est associé à votre compte");
  });

  it('offers Administration as a quick link only for the roles behind UserAccountController READ_ROLES', () => {
    for (const held of [['ADMIN'], ['SUPER_ADMIN'], ['SCHOOL_ADMINISTRATION']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/administration"]'),
      ).not.toBeNull();
    }
    for (const held of [['TEACHER'], ['PEDAGOGICAL_MANAGER'], ['STUDENT']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/administration"]'),
      ).toBeNull();
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

  it('offers Organisation & planning as a quick link only for its sub-section read roles', () => {
    for (const held of [
      ['ADMIN'],
      ['SUPER_ADMIN'],
      ['SCHOOL_ADMINISTRATION'],
      ['PEDAGOGICAL_MANAGER'],
    ] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('a[href="/organisation-planning"]')).not.toBeNull();
      // Les anciennes entrées séparées ne sont plus des raccourcis.
      expect(el.querySelector('a[href="/academic"]')).toBeNull();
      expect(el.querySelector('a[href="/alternation"]')).toBeNull();
    }
    for (const held of [['TEACHER'], ['STUDENT']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/organisation-planning"]'),
      ).toBeNull();
    }
  });

  it('offers Séances as a quick link for the session read roles, including TEACHER', () => {
    for (const held of [
      ['ADMIN'],
      ['SUPER_ADMIN'],
      ['SCHOOL_ADMINISTRATION'],
      ['PEDAGOGICAL_MANAGER'],
      ['TEACHER'],
    ] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions"]'),
      ).not.toBeNull();
    }
    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions"]')).toBeNull();
  });

  it('offers Émargement as a quick link only for a STUDENT', () => {
    roles.set(['STUDENT']);
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/attendance"]'),
    ).not.toBeNull();
    for (const held of [['ADMIN'], ['TEACHER'], ['PEDAGOGICAL_MANAGER']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/attendance"]'),
      ).toBeNull();
    }
  });

  it('shows the quick-links empty state when the account holds no known role', () => {
    roles.set([]);
    fixture.detectChanges();
    expect(text()).toContain("Aucun autre écran n'est disponible");
  });

  it('renders the quick links as a labelled grid of shortcut tiles, not a nav list (Lot D)', () => {
    roles.set(['ADMIN']);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    const grid = host.querySelector('nav.dashboard__shortcuts');
    expect(grid).not.toBeNull();
    expect(grid?.getAttribute('aria-label')).toBe('Raccourcis');
    expect(host.querySelectorAll('.dashboard__shortcut').length).toBeGreaterThan(1);
    // Plus de liste de navigation Material (qui dupliquait le rail).
    expect(host.querySelector('mat-nav-list')).toBeNull();
  });

  // --- Tableau de bord par rôle (bloc G1-F) ---------------------

  const reload = (payload: Record<string, unknown>) => {
    (fixture.componentInstance as unknown as { loadDashboard: () => void }).loadDashboard();
    http.expectOne(DASH_URL).flush(payload);
    fixture.detectChanges();
  };

  it('renders the administration counts from the server payload', () => {
    expect(text()).toContain('Comptes actifs');
    expect(text()).toContain('12');
    expect(text()).toContain("En attente d'activation");
  });

  it('renders a STUDENT card without any /sessions link', () => {
    roles.set(['STUDENT']);
    reload({
      role: 'STUDENT',
      generatedAt: '2026-09-10T09:00:00Z',
      teacher: null,
      manager: null,
      administration: null,
      notes: [],
      student: {
        nextSession: null,
        weekSessions: [
          {
            sessionPublicId: 's-1',
            title: 'Atelier',
            status: 'PLANNED',
            startsAt: '2026-09-11T08:00:00Z',
            endsAt: '2026-09-11T10:00:00Z',
            classCodes: ['C1'],
          },
        ],
        present: 4,
        late: 1,
        absent: 2,
        excused: 1,
        pendingJustifications: 1,
        rejectedJustifications: 0,
      },
    });
    expect(text()).toContain('Atelier');
    expect(text()).toContain('Présences');
    expect((fixture.nativeElement as HTMLElement).querySelector('a[href^="/sessions/"]')).toBeNull();
  });

  it('links teacher sessions to /sessions/:id for a TEACHER context', () => {
    roles.set(['TEACHER']);
    reload({
      role: 'TEACHER',
      generatedAt: '2026-09-10T09:00:00Z',
      student: null,
      manager: null,
      administration: null,
      notes: [],
      teacher: {
        nextSession: null,
        upcoming: [
          {
            sessionPublicId: 's-9',
            title: 'TP',
            status: 'PLANNED',
            startsAt: '2026-09-11T08:00:00Z',
            endsAt: '2026-09-11T10:00:00Z',
            classCodes: ['C1'],
          },
        ],
        toOpen: [],
      },
    });
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('a[href="/sessions/s-9"]'),
    ).not.toBeNull();
  });

  it('shows a forbidden state on a 403 and an error state otherwise', () => {
    (fixture.componentInstance as unknown as { loadDashboard: () => void }).loadDashboard();
    http.expectOne(DASH_URL).flush(null, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(text()).toContain("Aucun tableau de bord n'est disponible pour votre compte");

    (fixture.componentInstance as unknown as { loadDashboard: () => void }).loadDashboard();
    http.expectOne(DASH_URL).flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain("Le tableau de bord n'a pas pu être chargé");
  });

  /**
   * EF-REP-008 — « tout graphique dispose […] d'un tableau équivalent »
   * (docs/02 §22.6). L'histogramme et la table lisent la MÊME liste :
   * une personne qui ne distingue pas les couleurs, ou qui navigue au
   * lecteur d'écran, lit exactement les mêmes chiffres.
   */
  it('renders an equivalent table beside the manager attendance chart', () => {
    roles.set(['PEDAGOGICAL_MANAGER']);
    reload({
      role: 'PEDAGOGICAL_MANAGER',
      generatedAt: '2026-09-10T09:00:00Z',
      student: null,
      teacher: null,
      administration: null,
      notes: [],
      manager: {
        classCount: 2,
        upcomingSessions: [],
        classCodes: ['BTS1-A', 'BTS1-B'],
        periodFrom: '2026-08-11T09:00:00Z',
        periodTo: '2026-09-10T09:00:00Z',
        attendanceRate: 0.9125,
        lateCount: 7,
        unjustifiedAbsenceHalfDays: 5,
        pendingJustifications: 3,
        openClaims: 1,
        pendingActivations: 2,
        classRates: [
          {
            label: 'BTS1-A',
            expectedHalfDays: 40,
            presentHalfDays: 38,
            absentHalfDays: 1,
            excusedHalfDays: 1,
            lateCount: 4,
            attendanceRate: 0.95,
          },
        ],
      },
    });

    const root = fixture.nativeElement as HTMLElement;
    const table = root.querySelector('table.dashboard__table');
    expect(table).not.toBeNull();
    expect(table?.querySelector('caption')?.textContent).toContain('Tableau équivalent');
    // Les valeurs du graphique figurent en toutes lettres dans la table.
    expect(table?.textContent).toContain('BTS1-A');
    expect(table?.textContent).toContain('40');
    expect(table?.textContent).toContain('38');
    expect(table?.textContent).toContain('95.00 %');
    // La barre porte aussi sa valeur : la couleur n'est jamais seule.
    expect(root.querySelector('.dashboard__bar-value')?.textContent).toContain('95.00 %');
  });

  it('renders the full manager indicators the specification asks for', () => {
    roles.set(['PEDAGOGICAL_MANAGER']);
    reload({
      role: 'PEDAGOGICAL_MANAGER',
      generatedAt: '2026-09-10T09:00:00Z',
      student: null,
      teacher: null,
      administration: null,
      notes: [],
      manager: {
        classCount: 2,
        upcomingSessions: [],
        classCodes: ['BTS1-A'],
        periodFrom: '2026-08-11T09:00:00Z',
        periodTo: '2026-09-10T09:00:00Z',
        attendanceRate: 0.9125,
        lateCount: 7,
        unjustifiedAbsenceHalfDays: 5,
        pendingJustifications: 3,
        openClaims: 1,
        pendingActivations: 2,
        classRates: [],
      },
    });
    const content = text();
    expect(content).toContain("Taux d'assiduité");
    expect(content).toContain('91.25 %');
    expect(content).toContain('Absences non justifiées');
    expect(content).toContain('Réclamations ouvertes');
    expect(content).toContain('Comptes non activés');
  });

  /**
   * « Aucun dossier traité » et « traité en zéro heure » ne doivent pas
   * s'écrire de la même façon.
   */
  it('says no file was processed rather than showing a zero-hour delay', () => {
    expect(text()).toContain('Aucun dossier traité sur la période');
    expect(text()).not.toContain('0 h');
  });
});
