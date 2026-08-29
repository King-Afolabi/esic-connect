import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Role } from '../models/role';
import { AuthService } from './auth.service';
import { defaultContext, RoleContextService } from './role-context.service';

describe('defaultContext', () => {
  it('returns the most privileged role, following the declared ROLES order', () => {
    expect(defaultContext(['TEACHER', 'PEDAGOGICAL_MANAGER'])).toBe('PEDAGOGICAL_MANAGER');
    expect(defaultContext(['STUDENT', 'TEACHER', 'ADMIN'])).toBe('ADMIN');
  });

  it('returns null when no role is present', () => {
    expect(defaultContext([])).toBeNull();
  });
});

describe('RoleContextService', () => {
  const roles = signal<Role[]>([]);
  let service: RoleContextService;

  beforeEach(() => {
    roles.set([]);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { roles } }],
    });
    service = TestBed.inject(RoleContextService);
  });

  /** Laisse l'effet de réconciliation contexte ↔ session s'exécuter. */
  const sync = () => TestBed.tick();

  it('only ever offers the roles carried by the JWT as contexts', () => {
    roles.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    sync();
    expect(service.available()).toEqual(['PEDAGOGICAL_MANAGER', 'TEACHER']);
  });

  it('has no context and offers no choice for an account without roles', () => {
    sync();
    expect(service.active()).toBeNull();
    expect(service.activeLabel()).toBeNull();
    expect(service.hasChoice()).toBe(false);
    expect(service.effectiveRoles()).toEqual([]);
  });

  it('adopts a lone role as context and offers no choice', () => {
    roles.set(['TEACHER']);
    sync();
    expect(service.active()).toBe('TEACHER');
    expect(service.hasChoice()).toBe(false);
    expect(service.effectiveRoles()).toEqual(['TEACHER']);
  });

  it('defaults a multi-role account to its most privileged role and offers a choice', () => {
    roles.set(['TEACHER', 'PEDAGOGICAL_MANAGER']);
    sync();
    expect(service.active()).toBe('PEDAGOGICAL_MANAGER');
    expect(service.hasChoice()).toBe(true);
  });

  it('select() narrows the effective roles to the chosen context (display/navigation only)', () => {
    roles.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    sync();

    service.select('TEACHER');

    expect(service.active()).toBe('TEACHER');
    expect(service.activeLabel()).toBe('Mes séances de formateur');
    expect(service.effectiveRoles()).toEqual(['TEACHER']);
    // Les rôles réels du compte sont inchangés : seul l'affichage est filtré.
    expect(service.available()).toEqual(['PEDAGOGICAL_MANAGER', 'TEACHER']);
  });

  it('ignores a context the JWT does not carry (no fabricated role)', () => {
    roles.set(['TEACHER']);
    sync();

    service.select('ADMIN');

    expect(service.active()).toBe('TEACHER');
  });

  it('keeps a still-valid context across a role change, otherwise falls back to the default', () => {
    roles.set(['ADMIN', 'TEACHER']);
    sync();
    service.select('TEACHER');

    roles.set(['TEACHER', 'STUDENT']);
    sync();
    expect(service.active()).toBe('TEACHER');

    roles.set(['STUDENT', 'ADMIN']);
    sync();
    expect(service.active()).toBe('ADMIN');
  });

  it('never writes to localStorage or sessionStorage', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    try {
      roles.set(['ADMIN', 'TEACHER']);
      sync();
      service.select('TEACHER');
      sync();
      expect(setItem).not.toHaveBeenCalled();
    } finally {
      setItem.mockRestore();
    }
  });
});
