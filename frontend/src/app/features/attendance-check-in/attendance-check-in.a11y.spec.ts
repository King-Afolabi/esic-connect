import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { WritableSignal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Role } from '../../core/models/role';
import { RoleContextService } from '../../core/auth/role-context.service';
import { expectNoAxeViolations } from '../../../testing/axe';
import { AttendanceCheckIn } from './attendance-check-in';

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur l'écran
 * d'émargement de l'apprenant — parcours critique (alternative au scan
 * caméra). Voir `src/testing/axe.ts` pour le périmètre.
 */
describe('AttendanceCheckIn — accessibilité (axe-core)', () => {
  function render(roles: Role[] = ['STUDENT']) {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.resetTestingModule();
    const effectiveRoles: WritableSignal<Role[]> = signal(roles);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RoleContextService, useValue: { effectiveRoles } },
      ],
    });
    const fixture = TestBed.createComponent(AttendanceCheckIn);
    fixture.detectChanges();
    return fixture;
  }

  it('ne présente aucune violation axe-core pour un STUDENT', async () => {
    await expectNoAxeViolations(render().nativeElement);
  });

  it('ne présente aucune violation axe-core hors contexte STUDENT', async () => {
    await expectNoAxeViolations(render(['TEACHER']).nativeElement);
  });
});
