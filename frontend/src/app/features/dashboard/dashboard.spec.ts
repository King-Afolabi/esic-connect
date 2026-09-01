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
    pendingJustifications: 2,
    recentImports: [],
    todaySessions: [],
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

  it('offers Alternance as a quick link only for the alternation read roles', () => {
    for (const held of [
      ['ADMIN'],
      ['SUPER_ADMIN'],
      ['SCHOOL_ADMINISTRATION'],
      ['PEDAGOGICAL_MANAGER'],
    ] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/alternation"]'),
      ).not.toBeNull();
    }
    for (const held of [['TEACHER'], ['STUDENT']] as Role[][]) {
      roles.set(held);
      fixture.detectChanges();
      expect(
        (fixture.nativeElement as HTMLElement).querySelector('a[href="/alternation"]'),
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

  // --- Tableau de bord par rôle (bloc G1-F) ---------------------

  const reload = (payload: Record<string, unknown>) => {
    (fixture.componentInstance as unknown as { loadDashboard: () => void }).loadDashboard();
    http.expectOne(DASH_URL).flush(payload);
    fixture.detectChanges();
  };

  it('renders the administration counts from the server payload', () => {
    expect(text()).toContain('Actifs');
    expect(text()).toContain('12');
    expect(text()).toContain('En attente');
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
});
