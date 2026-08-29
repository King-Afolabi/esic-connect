import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  const auth = { isAuthenticated: vi.fn() };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
  });

  const run = (url: string) =>
    TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  it('allows navigation for an authenticated user', () => {
    auth.isAuthenticated.mockReturnValue(true);
    expect(run('/dashboard')).toBe(true);
  });

  it('redirects an anonymous user to /login, keeping the requested URL', () => {
    auth.isAuthenticated.mockReturnValue(false);
    const result = run('/students') as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
    expect(result.queryParams['redirect']).toBe('/students');
  });
});
