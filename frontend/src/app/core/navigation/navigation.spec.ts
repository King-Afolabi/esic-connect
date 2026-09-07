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
    // Regroupement ANO-NAV-001 : l'import et la création manuelle sont des
    // sous-écrans de « Apprenants », pas des entrées racines.
    expect(students?.matchPaths).toEqual(['/students/import', '/students/nouveau']);
  });

  it("ANO-NAV-001 — l'import n'a plus qu'une entrée racine, réservée au PEDAGOGICAL_MANAGER", () => {
    const imp = NAV_ITEMS.find((i) => i.path === '/students/import');
    expect(imp).toBeDefined();
    // Seul rôle autorisé à importer qui n'a PAS l'entrée « Apprenants ».
    expect(imp?.roles).toEqual(['PEDAGOGICAL_MANAGER']);
    // Les rôles d'administration ne voient plus d'entrée racine « Import ».
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/students/import');
    }
    expect(visibleNavItems(NAV_ITEMS, ['PEDAGOGICAL_MANAGER']).map((i) => i.path)).toContain(
      '/students/import',
    );
  });

  it('ANO-NAV-001 — « Invitations non activées » est une vue de « Invitations », plus une entrée racine', () => {
    expect(NAV_ITEMS.find((i) => i.path === '/invitations/non-activees')).toBeUndefined();
    const inv = NAV_ITEMS.find((i) => i.path === '/invitations');
    expect(inv?.matchPaths).toEqual(['/invitations/non-activees']);
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const) {
      const paths = visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path);
      expect(paths).toContain('/invitations');
      expect(paths).not.toContain('/invitations/non-activees');
    }
  });

  it('regroupe référentiels / organisation / planning / alternance sous une seule entrée', () => {
    const group = NAV_ITEMS.find((i) => i.path === '/organisation-planning');
    expect(group).toBeDefined();
    expect(group?.label).toBe('Organisation & planning');
    expect(group?.placeholder).toBeUndefined();
    expect(group?.roles).toEqual([
      'ADMIN',
      'SUPER_ADMIN',
      'SCHOOL_ADMINISTRATION',
      'PEDAGOGICAL_MANAGER',
    ]);
    // Les chemins possédés : les anciennes entrées ne sont plus dans le
    // menu, mais leurs routes restent adressables et l'entrée groupée
    // reste active dessus.
    expect(group?.matchPaths).toEqual(['/academic', '/organization', '/planning', '/alternation']);
    for (const path of ['/academic', '/organization', '/planning', '/alternation']) {
      expect(NAV_ITEMS.find((i) => i.path === path)).toBeUndefined();
    }
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
    // la sécurité du compte, il ne dépend d'aucun rôle. « Préférences de
    // notification » n'est plus une entrée latérale : c'est un onglet
    // interne de l'espace « Notifications ».
    expect(visibleNavItems(NAV_ITEMS, []).map((i) => i.path)).toEqual([
      '/dashboard',
      '/notifications',
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

  it('shows /organisation-planning for the read roles of its four sub-sections, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION', 'PEDAGOGICAL_MANAGER'] as const) {
      const paths = visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path);
      expect(paths).toContain('/organisation-planning');
      // Les quatre anciennes entrées ne sont plus rendues séparément.
      expect(paths).not.toContain('/academic');
      expect(paths).not.toContain('/organization');
      expect(paths).not.toContain('/planning');
      expect(paths).not.toContain('/alternation');
    }
    for (const role of ['TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain(
        '/organisation-planning',
      );
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
  // Les chemins « possédés » par une autre entrée via `matchPaths`
  // (regroupements Organisation & planning, Apprenants, Invitations) sont
  // intentionnellement résolus vers leur entrée parente : on les exclut de
  // la boucle « chaque route résout vers elle-même ».
  const ownedPaths = new Set(NAV_ITEMS.flatMap((i) => i.matchPaths ?? []));
  const menu = NAV_ITEMS.filter((i) => !i.placeholder && !ownedPaths.has(i.path));

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
    // « Préférences » est un onglet interne : la seule entrée latérale
    // « Notifications » reste active sur les deux vues de l'espace.
    expect(activeNavPath('/notifications/preferences', NAV_ITEMS)).toBe('/notifications');
    expect(activeNavPath('/notifications', NAV_ITEMS)).toBe('/notifications');
    expect(activeNavPath('/my-attendance/transparency', NAV_ITEMS)).toBe(
      '/my-attendance/transparency',
    );
  });

  it('ANO-NAV-001 — les sous-écrans regroupés gardent leur entrée parente active', () => {
    // Vu par un rôle d'administration : « Apprenants » (avec matchPaths)
    // est visible, « Import » ne l'est pas → le parent reste actif.
    const adminItems = visibleNavItems(NAV_ITEMS, ['ADMIN']);
    expect(activeNavPath('/students/import', adminItems)).toBe('/students');
    expect(activeNavPath('/students/import/7', adminItems)).toBe('/students');
    expect(activeNavPath('/students/nouveau', adminItems)).toBe('/students');
    expect(activeNavPath('/invitations/non-activees', adminItems)).toBe('/invitations');

    // Vu par un PEDAGOGICAL_MANAGER : pas d'entrée « Apprenants », mais une
    // entrée « Importer des apprenants » → c'est elle qui reste active.
    const managerItems = visibleNavItems(NAV_ITEMS, ['PEDAGOGICAL_MANAGER']);
    expect(activeNavPath('/students/import', managerItems)).toBe('/students/import');
  });

  it('sur une fiche de détail hors menu, le parent reste actif', () => {
    expect(activeNavPath('/students/42', NAV_ITEMS)).toBe('/students');
    expect(activeNavPath('/students/42?tab=history', NAV_ITEMS)).toBe('/students');
    expect(activeNavPath('/sessions/abc-123', NAV_ITEMS)).toBe('/sessions');
  });

  it('garde « Organisation & planning » active sur ses quatre sous-sections regroupées', () => {
    for (const url of [
      '/organisation-planning',
      '/academic',
      '/academic/class-groups',
      '/organization',
      '/organization/sites/abc',
      '/planning',
      '/planning/import/7',
      '/alternation',
    ]) {
      expect(activeNavPath(url, NAV_ITEMS), `url ${url}`).toBe('/organisation-planning');
    }
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
    expect(activeNavPath('/planning?jobId=7#top', NAV_ITEMS)).toBe('/organisation-planning');
  });
});
