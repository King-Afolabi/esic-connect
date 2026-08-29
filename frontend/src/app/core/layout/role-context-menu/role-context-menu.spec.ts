import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Role } from '../../models/role';
import { RoleContextService, ROLE_CONTEXT_LABELS } from '../../auth/role-context.service';
import { RoleContextMenu } from './role-context-menu';

class FakeRoleContext {
  readonly available = signal<Role[]>([]);
  readonly active = signal<Role | null>(null);
  readonly activeLabel = computed(() => {
    const role = this.active();
    return role ? ROLE_CONTEXT_LABELS[role] : null;
  });
  readonly hasChoice = computed(() => this.available().length > 1);
  readonly effectiveRoles = computed<readonly Role[]>(() => {
    const role = this.active();
    return role ? [role] : this.available();
  });
  readonly select = vi.fn((role: Role) => this.active.set(role));
}

describe('RoleContextMenu', () => {
  let fixture: ComponentFixture<RoleContextMenu>;
  let context: FakeRoleContext;

  beforeEach(async () => {
    context = new FakeRoleContext();

    await TestBed.configureTestingModule({
      imports: [RoleContextMenu],
      providers: [{ provide: RoleContextService, useValue: context }],
    }).compileComponents();

    fixture = TestBed.createComponent(RoleContextMenu);
    fixture.detectChanges();
  });

  const trigger = () =>
    fixture.nativeElement.querySelector('.role-context__trigger') as HTMLButtonElement | null;

  it('renders nothing for a single-role account', () => {
    context.available.set(['TEACHER']);
    context.active.set('TEACHER');
    fixture.detectChanges();
    expect(trigger()).toBeNull();
  });

  it('shows the active context in the trigger when several roles are held', () => {
    context.available.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    context.active.set('PEDAGOGICAL_MANAGER');
    fixture.detectChanges();

    const button = trigger();
    expect(button).not.toBeNull();
    expect(button?.textContent).toContain('Contexte :');
    expect(button?.textContent).toContain('Gestion pédagogique');
    expect(button?.getAttribute('aria-haspopup')).toBe('menu');
  });

  it('lists every held role as a menu item and delegates the choice to the service', () => {
    context.available.set(['PEDAGOGICAL_MANAGER', 'TEACHER']);
    context.active.set('PEDAGOGICAL_MANAGER');
    fixture.detectChanges();

    trigger()!.click();
    fixture.detectChanges();

    const items = Array.from(
      document.querySelectorAll('.mat-mdc-menu-item'),
    ) as HTMLButtonElement[];
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Gestion pédagogique');
    expect(items[1].textContent).toContain('Mes séances de formateur');
    // Le contexte actif porte un repère visuel (icône « check »).
    expect(items[0].querySelector('mat-icon')?.textContent).toContain('check');
    expect(items[0].getAttribute('aria-current')).toBe('true');

    const teacherItem = items.find((i) => i.textContent?.includes('Mes séances de formateur'));
    teacherItem?.click();
    fixture.detectChanges();

    expect(context.select).toHaveBeenCalledWith('TEACHER');
  });
});
