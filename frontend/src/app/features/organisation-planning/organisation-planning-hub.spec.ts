import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { RoleContextService } from '../../core/auth/role-context.service';
import { Role } from '../../core/models/role';
import { OrganisationPlanningHub } from './organisation-planning-hub';

function setup(roles: Role[]) {
  TestBed.resetTestingModule();
  const effectiveRoles: WritableSignal<Role[]> = signal(roles);
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: RoleContextService, useValue: { effectiveRoles } },
    ],
  });
  const fixture: ComponentFixture<OrganisationPlanningHub> =
    TestBed.createComponent(OrganisationPlanningHub);
  fixture.detectChanges();
  return {
    fixture,
    effectiveRoles,
    hrefs: () =>
      Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('.ophub__link'),
      ).map((a) => a.getAttribute('href')),
  };
}

describe('OrganisationPlanningHub', () => {
  it('rend un seul titre de page « Organisation & planning »', () => {
    const { fixture } = setup(['ADMIN']);
    const headings = (fixture.nativeElement as HTMLElement).querySelectorAll('h1');
    expect(headings.length).toBe(1);
    expect(headings[0].textContent).toContain('Organisation');
  });

  it('liste les quatre sous-sections comme liens de route vers leurs routes inchangées', () => {
    const { hrefs } = setup(['ADMIN']);
    expect(hrefs()).toEqual(['/academic', '/organization', '/planning', '/alternation']);
  });

  it("n'affiche aucune sous-section pour un rôle hors périmètre", () => {
    for (const role of ['TEACHER', 'STUDENT'] as Role[]) {
      const { hrefs } = setup([role]);
      expect(hrefs()).toEqual([]);
    }
  });

  it('reflète le contexte de rôle actif (le serveur reste l’autorité)', () => {
    const { hrefs, effectiveRoles, fixture } = setup(['PEDAGOGICAL_MANAGER']);
    expect(hrefs().length).toBe(4);
    effectiveRoles.set(['STUDENT']);
    fixture.detectChanges();
    expect(hrefs()).toEqual([]);
  });
});
