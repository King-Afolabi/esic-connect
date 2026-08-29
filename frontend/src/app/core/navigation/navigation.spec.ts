import { ROLES } from '../models/role';
import { NAV_ITEMS, visibleNavItems } from './navigation';

describe('NAV_ITEMS', () => {
  it('keeps the role -> protected-route mapping for the placeholder routes (traceability)', () => {
    const admin = NAV_ITEMS.find((i) => i.path === '/administration');
    const students = NAV_ITEMS.find((i) => i.path === '/students');

    expect(admin).toMatchObject({ placeholder: true, roles: ['ADMIN', 'SUPER_ADMIN'] });
    expect(students).toMatchObject({
      placeholder: true,
      roles: ['ADMIN', 'SUPER_ADMIN', 'SCHOOL_ADMINISTRATION'],
    });
  });
});

describe('visibleNavItems', () => {
  it('exposes only usable screens — currently just the dashboard', () => {
    expect(visibleNavItems(NAV_ITEMS, []).map((i) => i.path)).toEqual(['/dashboard']);
  });

  it('never returns a placeholder entry, for any combination of roles', () => {
    const everyRoleCombo = [[], ...ROLES.map((r) => [r]), [...ROLES]];
    for (const held of everyRoleCombo) {
      const paths = visibleNavItems(NAV_ITEMS, held).map((i) => i.path);
      expect(paths).not.toContain('/administration');
      expect(paths).not.toContain('/students');
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
