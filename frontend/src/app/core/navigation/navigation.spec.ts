import { activeNavPath, NAV_ITEMS, visibleNavItems } from './navigation';

describe('NAV_ITEMS', () => {
  it('exposes /administration as a real screen gated on UserAccountController READ_ROLES', () => {
    const admin = NAV_ITEMS.find((i) => i.path === '/administration');
    expect(admin).toBeDefined();
    expect(admin?.placeholder).toBeUndefined();
    expect(admin?.roles).toEqual(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION']);
  });

  it('exposes /students as a real screen gated on EnrollmentWeb.MANAGE_ROLES', () => {
    const students = NAV_ITEMS.find((i) => i.path === '/students');
    expect(students).toBeDefined();
    expect(students?.placeholder).toBeUndefined();
    expect(students?.roles).toEqual(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION']);
  });

  it('exposes /academic as a real screen gated on AcademicWeb.READ_ROLES', () => {
    const academic = NAV_ITEMS.find((i) => i.path === '/academic');
    expect(academic).toBeDefined();
    expect(academic?.placeholder).toBeUndefined();
    expect(academic?.roles).toEqual([
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
    ]);
  });

  it('exposes /organization as a real screen gated on SiteController.READ_ROLES', () => {
    const organization = NAV_ITEMS.find((i) => i.path === '/organization');
    expect(organization).toBeDefined();
    expect(organization?.placeholder).toBeUndefined();
    expect(organization?.roles).toEqual([
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
    ]);
  });

  it('exposes /planning as a real screen gated on PlanningWeb.MANAGE_ROLES', () => {
    const planning = NAV_ITEMS.find((i) => i.path === '/planning');
    expect(planning).toBeDefined();
    expect(planning?.placeholder).toBeUndefined();
    expect(planning?.roles).toEqual([
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
    ]);
  });

  it('exposes /alternation as a real screen gated on AlternationWeb read roles', () => {
    const alternation = NAV_ITEMS.find((i) => i.path === '/alternation');
    expect(alternation).toBeDefined();
    expect(alternation?.placeholder).toBeUndefined();
    expect(alternation?.roles).toEqual([
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
    ]);
  });

  it('exposes /sessions gated on CourseSessionWeb.READ_ROLES (TEACHER included)', () => {
    const sessions = NAV_ITEMS.find((i) => i.path === '/sessions');
    expect(sessions).toBeDefined();
    expect(sessions?.placeholder).toBeUndefined();
    expect(sessions?.roles).toEqual([
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
      'TEACHER',
    ]);
  });

  it('exposes /attendance gated on the STUDENT role only', () => {
    const attendance = NAV_ITEMS.find((i) => i.path === '/attendance');
    expect(attendance).toBeDefined();
    expect(attendance?.placeholder).toBeUndefined();
    expect(attendance?.roles).toEqual(['STUDENT']);
  });

  it('exposes /notifications as a real screen visible to any authenticated user (G1-D)', () => {
    const notifications = NAV_ITEMS.find((i) => i.path === '/notifications');
    expect(notifications).toBeDefined();
    expect(notifications?.placeholder).toBeUndefined();
    expect(notifications?.roles).toBeUndefined();
  });
});

describe('visibleNavItems', () => {
  it('exposes the always-visible items (dashboard, notifications) when no role is held', () => {
    // `/mon-compte/calendrier` s'ajoute au sprint 11 : l'abonnement
    // iCalendar est propre à chaque personne, et sa route porte
    // `@PreAuthorize("isAuthenticated()")` — comme les notifications et
    // la sécurité du compte, il ne dépend d'aucun rôle.
    expect(visibleNavItems(NAV_ITEMS, []).map((i) => i.path)).toEqual([
      '/dashboard',
      '/notifications',
      '/notifications/preferences',
      '/mon-compte/calendrier',
      '/mon-compte/securite',
    ]);
  });

  it('shows /administration for the roles that back UserAccountController READ_ROLES, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/administration');
    }
    for (const role of ['PEDAGOGICAL_MANAGER', 'TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/administration');
    }
  });

  it('never returns an entry flagged as a placeholder (mechanism kept for future routes)', () => {
    const items = [
      { label: 'Real', path: '/real', icon: '' },
      { label: 'Soon', path: '/soon', icon: '', placeholder: true },
    ];
    expect(visibleNavItems(items, []).map((i) => i.path)).toEqual(['/real']);
  });

  it('shows /students for the roles that back EnrollmentWeb.MANAGE_ROLES, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/students');
    }
    for (const role of ['PEDAGOGICAL_MANAGER', 'TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/students');
    }
  });

  it('shows /academic for the roles that back AcademicWeb.READ_ROLES, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/academic');
    }
    for (const role of ['TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/academic');
    }
  });

  it('shows /alternation for the alternation read roles, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/alternation');
    }
    for (const role of ['TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/alternation');
    }
  });

  it('shows /organization for the SiteController read roles, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/organization');
    }
    for (const role of ['TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/organization');
    }
  });

  it('shows /planning for PlanningWeb.MANAGE_ROLES, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/planning');
    }
    for (const role of ['TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/planning');
    }
  });

  it('shows /sessions for the session read roles (TEACHER included), and hides it from a STUDENT', () => {
    for (const role of [
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
      'TEACHER',
    ] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/sessions');
    }
    expect(visibleNavItems(NAV_ITEMS, ['STUDENT']).map((i) => i.path)).not.toContain('/sessions');
  });

  it('shows /attendance only for a STUDENT', () => {
    expect(visibleNavItems(NAV_ITEMS, ['STUDENT']).map((i) => i.path)).toContain('/attendance');
    for (const role of [
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
      'TEACHER',
    ] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/attendance');
    }
  });

  it('still applies the role filter to non-placeholder entries', () => {
    // Sanity check with a fabricated real (non-placeholder) restricted entry.
    const items = [
      { label: 'Public', path: '/p', icon: '' },
      { label: 'Staff', path: '/s', icon: '', roles: ['ADMIN'] as const },
    ];
    expect(visibleNavItems(items, []).map((i) => i.path)).toEqual(['/p']);
    expect(visibleNavItems(items, ['ADMIN']).map((i) => i.path)).toEqual(['/p', '/s']);
  });
});

describe('activeNavPath (Lot C — un seul élément actif)', () => {
  const menu = NAV_ITEMS.filter((i) => !i.placeholder);

  it('résout chaque route du menu vers elle-même, et une seule', () => {
    for (const item of menu) {
      const active = activeNavPath(item.path, NAV_ITEMS);
      expect(active, `route ${item.path}`).toBe(item.path);
      // Aucune autre entrée ne peut se dire active pour cette URL.
      const alsoActive = menu.filter(
        (other) => other.path !== item.path && activeNavPath(item.path, [other]) === other.path,
      );
      const nested = alsoActive.filter((o) => o.path !== active);
      // Les seules correspondances tolérées sont des parents stricts, que
      // `activeNavPath` écarte au profit du plus profond.
      for (const parent of nested) {
        expect(item.path.startsWith(parent.path + '/')).toBe(true);
      }
    }
  });

  it('sur une route imbriquée, seul l’enfant est actif (le bug signalé)', () => {
    expect(activeNavPath('/notifications/preferences', NAV_ITEMS)).toBe('/notifications/preferences');
    expect(activeNavPath('/notifications', NAV_ITEMS)).toBe('/notifications');
    expect(activeNavPath('/students/import', NAV_ITEMS)).toBe('/students/import');
    expect(activeNavPath('/my-attendance/transparency', NAV_ITEMS)).toBe(
      '/my-attendance/transparency',
    );
  });

  it('sur une fiche de détail hors menu, le parent reste actif', () => {
    expect(activeNavPath('/students/42', NAV_ITEMS)).toBe('/students');
    expect(activeNavPath('/students/42?tab=history', NAV_ITEMS)).toBe('/students');
    expect(activeNavPath('/sessions/abc-123', NAV_ITEMS)).toBe('/sessions');
  });

  it('ne confond pas deux routes qui partagent un préfixe de chaîne', () => {
    // `/attendance` n’est PAS un parent de `/attendance-management`.
    expect(activeNavPath('/attendance-management', NAV_ITEMS)).toBe('/attendance-management');
    expect(activeNavPath('/attendance', NAV_ITEMS)).toBe('/attendance');
  });

  it('renvoie null pour une URL qui ne relève d’aucune entrée', () => {
    expect(activeNavPath('/connexion/verification', NAV_ITEMS)).toBeNull();
    expect(activeNavPath('/', NAV_ITEMS)).toBeNull();
  });

  it('ignore la chaîne de requête et le fragment', () => {
    expect(activeNavPath('/planning?jobId=7#top', NAV_ITEMS)).toBe('/planning');
  });
});
