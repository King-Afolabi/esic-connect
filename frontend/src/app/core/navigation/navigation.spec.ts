import { NAV_ITEMS, visibleNavItems } from './navigation';

describe('visibleNavItems', () => {
  it('always exposes entries without a role restriction', () => {
    const visible = visibleNavItems(NAV_ITEMS, []);
    expect(visible.map((i) => i.path)).toEqual(['/dashboard']);
  });

  it('reveals the administration entry only to ADMIN / SUPER_ADMIN', () => {
    expect(visibleNavItems(NAV_ITEMS, ['ADMIN']).map((i) => i.path)).toContain('/administration');
    expect(visibleNavItems(NAV_ITEMS, ['TEACHER']).map((i) => i.path)).not.toContain(
      '/administration',
    );
  });

  it('reveals the students entry to SCHOOL_ADMINISTRATION but not to a bare STUDENT', () => {
    expect(visibleNavItems(NAV_ITEMS, ['SCHOOL_ADMINISTRATION']).map((i) => i.path)).toContain(
      '/students',
    );
    expect(visibleNavItems(NAV_ITEMS, ['STUDENT']).map((i) => i.path)).toEqual(['/dashboard']);
  });
});
