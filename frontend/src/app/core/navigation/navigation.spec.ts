import { ROLES } from '../models/role';
import { NAV_ITEMS, visibleNavItems } from './navigation';

describe('NAV_ITEMS', () => {
  it('keeps /administration as a guarded placeholder (screen not built yet)', () => {
    const admin = NAV_ITEMS.find((i) => i.path === '/administration');
    expect(admin).toMatchObject({ placeholder: true, roles: ['ADMIN', 'SUPER_ADMIN'] });
  });

  it('exposes /students as a real screen gated on EnrollmentWeb.MANAGE_ROLES', () => {
    const students = NAV_ITEMS.find((i) => i.path === '/students');
    expect(students).toBeDefined();
    expect(students?.placeholder).toBeUndefined();
    expect(students?.roles).toEqual(['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION']);
  });
});

describe('visibleNavItems', () => {
  it('exposes only the dashboard when no role is held', () => {
    expect(visibleNavItems(NAV_ITEMS, []).map((i) => i.path)).toEqual(['/dashboard']);
  });

  it('never returns the /administration placeholder entry, for any combination of roles', () => {
    const everyRoleCombo = [[], ...ROLES.map((r) => [r]), [...ROLES]];
    for (const held of everyRoleCombo) {
      const paths = visibleNavItems(NAV_ITEMS, held).map((i) => i.path);
      expect(paths).not.toContain('/administration');
    }
  });

  it('shows /students for the roles that back EnrollmentWeb.MANAGE_ROLES, and hides it otherwise', () => {
    for (const role of ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).toContain('/students');
    }
    for (const role of ['PEDAGOGICAL_MANAGER', 'TEACHER', 'STUDENT'] as const) {
      expect(visibleNavItems(NAV_ITEMS, [role]).map((i) => i.path)).not.toContain('/students');
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
