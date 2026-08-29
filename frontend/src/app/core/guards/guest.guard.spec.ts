import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { guestGuard } from './guest.guard';

describe('guestGuard', () => {
  const auth = { isAuthenticated: vi.fn() };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }],
    });
  });

  const run = () =>
    TestBed.runInInjectionContext(() =>
      guestGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

  it('lets an anonymous visitor reach the login page', () => {
    auth.isAuthenticated.mockReturnValue(false);
    expect(run()).toBe(true);
  });

  it('sends an already authenticated user to the dashboard', () => {
    auth.isAuthenticated.mockReturnValue(true);
    const result = run() as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/dashboard');
  });
});
