import { NAV_ITEMS, visibleNavItems } from './navigation';

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
});

describe('visibleNavItems', () => {
  it('exposes only the dashboard when no role is held', () => {
    expect(visibleNavItems(NAV_ITEMS, []).map((i) => i.path)).toEqual(['/dashboard']);
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
