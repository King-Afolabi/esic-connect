import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SessionActivityService } from '../../auth/session-activity.service';
import { SessionTimeoutWarning } from './session-timeout-warning';

describe('SessionTimeoutWarning', () => {
  let fixture: ComponentFixture<SessionTimeoutWarning>;
  const warningVisible = signal(false);
  const renewing = signal(false);
  const msRemaining = signal(90_000);
  const activity = {
    warningVisible,
    renewing,
    msRemaining,
    continueSession: vi.fn(),
    endNow: vi.fn(),
  };

  beforeEach(async () => {
    warningVisible.set(false);
    renewing.set(false);
    msRemaining.set(90_000);
    activity.continueSession.mockReset();
    activity.endNow.mockReset();

    await TestBed.configureTestingModule({
      imports: [SessionTimeoutWarning],
      providers: [{ provide: SessionActivityService, useValue: activity }],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionTimeoutWarning);
    fixture.detectChanges();
  });

  afterEach(() => {
    warningVisible.set(false);
    fixture.destroy();
  });

  const host = () => fixture.nativeElement as HTMLElement;
  const dialog = () => host().querySelector('[role="alertdialog"]') as HTMLElement | null;

  it('renders nothing while no warning is active', () => {
    expect(dialog()).toBeNull();
  });

  it('renders an accessible alertdialog when the warning is active', () => {
    warningVisible.set(true);
    fixture.detectChanges();

    const el = dialog();
    expect(el).not.toBeNull();
    expect(el!.getAttribute('aria-modal')).toBe('true');
    expect(el!.getAttribute('aria-labelledby')).toBe('session-warning-title');
    expect(el!.getAttribute('aria-describedby')).toBe('session-warning-desc');
    expect(host().querySelector('#session-warning-title')).not.toBeNull();
    expect(host().querySelector('#session-warning-desc')?.textContent).toContain('seconde');
  });

  it('the primary action asks to continue the session', () => {
    warningVisible.set(true);
    fixture.detectChanges();

    const primary = host().querySelector('button[mat-flat-button]') as HTMLButtonElement;
    primary.click();

    expect(activity.continueSession).toHaveBeenCalledOnce();
  });

  it('the secondary action logs out', () => {
    warningVisible.set(true);
    fixture.detectChanges();

    const buttons = Array.from(host().querySelectorAll('button')) as HTMLButtonElement[];
    const logout = buttons.find((b) => b.textContent?.trim() === 'Se déconnecter')!;
    logout.click();

    expect(activity.endNow).toHaveBeenCalledOnce();
  });

  it('Escape continues the session rather than logging out', () => {
    warningVisible.set(true);
    fixture.detectChanges();

    dialog()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(activity.continueSession).toHaveBeenCalledOnce();
    expect(activity.endNow).not.toHaveBeenCalled();
  });

  it('disables the actions while a renewal is in flight', () => {
    warningVisible.set(true);
    renewing.set(true);
    fixture.detectChanges();

    const buttons = Array.from(host().querySelectorAll('button')) as HTMLButtonElement[];
    expect(buttons.every((b) => b.disabled)).toBe(true);
  });
});
