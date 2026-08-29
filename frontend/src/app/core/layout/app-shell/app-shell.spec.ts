import { BreakpointObserver } from '@angular/cdk/layout';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { Role } from '../../models/role';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  const roles = signal<Role[]>(['ADMIN']);
  const currentUserEmail = signal<string | null>('admin@esic.test');
  const auth = { roles, currentUserEmail, logout: vi.fn() };

  beforeEach(async () => {
    roles.set(['ADMIN']);
    currentUserEmail.set('admin@esic.test');
    auth.logout.mockReset();

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        {
          provide: BreakpointObserver,
          useValue: { observe: () => of({ matches: false, breakpoints: {} }) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('shows the brand, the current user email and a logout control', () => {
    expect(text()).toContain('ESIC Connect');
    expect(text()).toContain('admin@esic.test');
    expect(text()).toContain('Se déconnecter');
  });

  it('renders navigation entries permitted for the held roles', () => {
    expect(text()).toContain('Tableau de bord');
    expect(text()).toContain('Administration');
  });

  it('hides role-restricted entries for a role that lacks them', () => {
    roles.set(['TEACHER']);
    fixture.detectChanges();
    expect(text()).toContain('Tableau de bord');
    expect(text()).not.toContain('Administration');
    expect(text()).not.toContain('Apprenants');
  });

  it('calls AuthService.logout when the logout control is used', () => {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    const logoutButton = buttons.find((b) => b.textContent?.includes('Se déconnecter'));
    logoutButton?.click();
    expect(auth.logout).toHaveBeenCalledOnce();
  });
});
