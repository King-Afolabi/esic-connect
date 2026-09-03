import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { Session } from '../../../core/models/session';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { Login } from './login';

/**
 * Garde-fou d'accessibilité automatisé (FINAL-020) sur l'écran de
 * connexion — un des parcours les plus critiques. Voir
 * `src/testing/axe.ts` pour le périmètre exact (jsdom : pas de contraste).
 */
describe('Login — accessibilité (axe-core)', () => {
  let fixture: ComponentFixture<Login>;
  const auth = { login: vi.fn().mockReturnValue(new Subject<Session>().asObservable()) };

  beforeEach(async () => {
    // Routeur de test réel : l'écran porte un `routerLink` vers « mot de
    // passe oublié », que la directive ne peut pas résoudre avec un
    // routeur factice. La navigation est neutralisée par un espion.
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
  });

  it('ne présente aucune violation axe-core à l’état initial', async () => {
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('ne présente aucune violation axe-core après une soumission invalide', async () => {
    const component = fixture.componentInstance as unknown as {
      form: { markAllAsTouched: () => void };
      submit?: () => void;
    };
    component.form.markAllAsTouched();
    fixture.detectChanges();
    await expectNoAxeViolations(fixture.nativeElement);
  });
});
