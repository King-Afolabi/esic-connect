import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { roleGuard } from './role.guard';

describe('roleGuard', () => {
  const auth = { isAuthenticated: vi.fn(), hasAnyRole: vi.fn() };

  beforeEach(() => {
    auth.isAuthenticated.mockReset();
    auth.hasAnyRole.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
  });

  const run = (roles: Parameters<typeof roleGuard>[0]) =>
    TestBed.runInInjectionContext(() =>
      roleGuard(roles)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  it('allows access when the user holds one of the required roles', () => {
    auth.isAuthenticated.mockReturnValue(true);
    auth.hasAnyRole.mockReturnValue(true);

    expect(run(['ADMIN', 'SUPER_ADMIN'])).toBe(true);
    expect(auth.hasAnyRole).toHaveBeenCalledWith(['ADMIN', 'SUPER_ADMIN']);
  });

  it('routes an authenticated but unauthorized role to /forbidden', () => {
    auth.isAuthenticated.mockReturnValue(true);
    auth.hasAnyRole.mockReturnValue(false);

    const result = run(['ADMIN']) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/forbidden');
  });

  it('routes an anonymous user to /login', () => {
    auth.isAuthenticated.mockReturnValue(false);

    const result = run(['ADMIN']) as UrlTree;
    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
  });
});
