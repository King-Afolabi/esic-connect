import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { NotificationService } from '../../core/notifications/notification.service';
import { CalendarSubscriptionsApiService } from './calendar-subscriptions-api.service';
import { CalendarSubscriptions } from './calendar-subscriptions';

/**
 * Abonnements iCalendar (EF-INT-001 ; AC-034).
 *
 * Vérifie que le lien complet n'est affiché qu'une fois, que la
 * révocation ne supprime pas la ligne, et que le client construit l'URL
 * à partir de son origine — le serveur ne connaît pas celle-ci.
 */
describe('CalendarSubscriptions', () => {
  let fixture: ComponentFixture<CalendarSubscriptions>;

  const api = { list: vi.fn(), create: vi.fn(), revoke: vi.fn() };
  const notifications = { error: vi.fn(), info: vi.fn() };

  const active = {
    publicId: 'sub-1',
    label: 'Téléphone',
    createdAt: '2026-09-05T08:00:00Z',
    lastUsedAt: null,
    revokedAt: null,
  };
  const revoked = { ...active, publicId: 'sub-2', label: 'Ancien', revokedAt: '2026-09-04T08:00:00Z' };

  beforeEach(async () => {
    api.list.mockReset().mockReturnValue(of([active, revoked]));
    api.create.mockReset().mockReturnValue(
      of({
        publicId: 'sub-3',
        label: 'Bureau',
        createdAt: '2026-09-05T09:00:00Z',
        feedPath: '/api/v1/calendar/abc123.ics?token=secret-token',
      }),
    );
    api.revoke.mockReset().mockReturnValue(of(void 0));
    notifications.error.mockReset();

    await TestBed.configureTestingModule({
      imports: [CalendarSubscriptions],
      providers: [
        { provide: CalendarSubscriptionsApiService, useValue: api },
        { provide: NotificationService, useValue: notifications },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(CalendarSubscriptions);
    fixture.detectChanges();
  });

  const text = () => fixture.nativeElement.textContent as string;
  const component = () =>
    fixture.componentInstance as unknown as {
      form: { setValue: (v: { label: string }) => void };
      create: () => void;
      revoke: (s: { publicId: string; revokedAt: string | null }) => void;
    };

  it('lists active and revoked subscriptions without ever showing a token', () => {
    expect(text()).toContain('Téléphone');
    expect(text()).toContain('Révoqué');
    expect(text()).not.toContain('token=');
  });

  it('builds the full URL from the browser origin and warns it is shown once', () => {
    component().form.setValue({ label: 'Bureau' });
    component().create();
    fixture.detectChanges();
    expect(text()).toContain(`${window.location.origin}/api/v1/calendar/abc123.ics?token=secret-token`);
    expect(text()).toContain('il ne sera plus affiché');
  });

  it('revokes an active subscription and reloads the list', () => {
    component().revoke(active);
    expect(api.revoke).toHaveBeenCalledWith('sub-1');
    // La liste est rechargée : une révocation ne retire pas la ligne, elle
    // la date.
    expect(api.list).toHaveBeenCalledTimes(2);
  });

  it('never tries to revoke an already revoked subscription', () => {
    component().revoke(revoked);
    expect(api.revoke).not.toHaveBeenCalled();
  });

  it('explains the 409 when too many subscriptions are active', () => {
    api.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' })),
    );
    component().create();
    expect(notifications.error).toHaveBeenCalledWith(
      "Trop d'abonnements actifs. Révoquez-en un avant d'en créer un autre.",
    );
  });
});
