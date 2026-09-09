import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../auth/auth.service';
import { RoleContextService } from '../../auth/role-context.service';
import { Role } from '../../models/role';
import { ProfileMenu } from './profile-menu';

describe('ProfileMenu', () => {
  let fixture: ComponentFixture<ProfileMenu>;
  const email = signal<string | null>('claire.durand@esic.test');
  const roles = signal<Role[]>(['PEDAGOGICAL_MANAGER']);
  const active = signal<Role | null>(null);

  const auth = { currentUserEmail: email, roles };
  const context = {
    hasChoice: computed(() => roles().length > 1),
    activeLabel: computed(() => (active() === 'TEACHER' ? 'Mes séances de formateur' : 'Gestion pédagogique')),
  };

  beforeEach(async () => {
    email.set('claire.durand@esic.test');
    roles.set(['PEDAGOGICAL_MANAGER']);
    active.set(null);

    await TestBed.configureTestingModule({
      imports: [ProfileMenu],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: RoleContextService, useValue: context },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileMenu);
    fixture.detectChanges();
  });

  const trigger = () =>
    fixture.nativeElement.querySelector('button[aria-haspopup="menu"]') as HTMLButtonElement;

  const openPanel = () => {
    trigger().click();
    fixture.detectChanges();
  };

  it('keeps a short identifier in the header, not the full address', () => {
    const button = trigger();
    expect(button.textContent).toContain('claire.durand');
    expect(button.textContent).not.toContain('@esic.test');
    // L'adresse complète reste accessible aux technologies d'assistance.
    expect(button.getAttribute('aria-label')).toContain('claire.durand@esic.test');
  });

  it('keeps the header trigger compact: avatar + short name + caret, nothing else', () => {
    const button = trigger();
    expect(button.querySelector('.profile-menu__avatar')).not.toBeNull();
    expect(button.querySelector('.profile-menu__caret')).not.toBeNull();
    // L'adresse et les rôles détaillés ne sont JAMAIS dans la topbar.
    expect(button.textContent).not.toContain('@esic.test');
    expect(button.textContent).not.toContain('Responsable pédagogique');
  });

  it('gives the panel a wider, hierarchised layout (head + roles section)', () => {
    openPanel();
    const panel = document.querySelector('.profile-menu__panel') as HTMLElement;
    expect(panel.classList.contains('profile-menu__panel')).toBe(true);
    expect(panel.querySelector('.profile-menu__head')).not.toBeNull();
    expect(panel.querySelector('.profile-menu__email')?.textContent).toContain(
      'claire.durand@esic.test',
    );
    expect(panel.querySelector('.profile-menu__section')?.textContent).toContain(
      'Responsable pédagogique',
    );
  });

  it('falls back to "Profil" when no address is known', () => {
    email.set(null);
    fixture.detectChanges();
    expect(trigger().textContent).toContain('Profil');
  });

  it('shows the address, the role and a link to the account security page in the panel', () => {
    openPanel();
    const panel = document.querySelector('.profile-menu__panel') as HTMLElement;
    expect(panel.textContent).toContain('claire.durand@esic.test');
    expect(panel.textContent).toContain('Responsable pédagogique');
    const link = panel.querySelector('a[mat-menu-item]') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/mon-compte/securite');
    expect(link.textContent).toContain('Sécurité du compte');
  });

  it('shows the active usage context only when the account carries several roles', () => {
    openPanel();
    expect((document.querySelector('.profile-menu__panel') as HTMLElement).textContent).not.toContain(
      'Contexte actif',
    );

    // Refermer, puis rouvrir avec deux rôles.
    document.body.click();
    fixture.detectChanges();
    roles.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    fixture.detectChanges();
    openPanel();

    const panel = document.querySelector('.profile-menu__panel') as HTMLElement;
    expect(panel.textContent).toContain('Contexte actif');
    expect(panel.textContent).toContain('Gestion pédagogique');
  });
});
