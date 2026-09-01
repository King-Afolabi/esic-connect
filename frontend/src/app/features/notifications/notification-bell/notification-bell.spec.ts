import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { NotificationBell } from './notification-bell';
import { NotificationsBadgeService } from '../notifications-badge.service';

const UNREAD_URL = '/api/v1/me/notifications/unread-count';

/** Session authentifiée factice : le compteur ne sonde qu'authentifié (G1-D.1). */
const authedStub = { isAuthenticated: () => true, roles: () => [] as string[] };

interface Internals {
  unread: () => number;
  badgeText: () => string;
}

function setup() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: authedStub },
    ],
  });
  const fixture = TestBed.createComponent(NotificationBell);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as Internals;
  fixture.detectChanges();
  return { fixture, http, internals };
}

describe('NotificationBell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let http: HttpTestingController;
  let internals: Internals;

  afterEach(() => {
    http.match(UNREAD_URL).forEach((r) => !r.cancelled && r.flush({ unread: 0 }));
    http.verify();
  });

  it('fetches the unread count on init and links to /notifications', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(UNREAD_URL).flush({ unread: 3 });
    fixture.detectChanges();
    expect(internals.unread()).toBe(3);
    expect(internals.badgeText()).toBe('3');
    const link = (fixture.nativeElement as HTMLElement).querySelector('a');
    expect(link?.getAttribute('href')).toBe('/notifications');
    expect(link?.getAttribute('aria-label')).toContain('non lue');
  });

  it('caps the badge text at 99+', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(UNREAD_URL).flush({ unread: 250 });
    fixture.detectChanges();
    expect(internals.badgeText()).toBe('99+');
  });

  it('keeps the last known value on a transient poll failure', () => {
    ({ fixture, http, internals } = setup());
    http.expectOne(UNREAD_URL).flush({ unread: 5 });
    fixture.detectChanges();
    expect(internals.unread()).toBe(5);
    // A later refresh (badge service) failing must not zero the value.
    TestBed.inject(NotificationsBadgeService).refresh();
    http.expectOne(UNREAD_URL).flush('boom', { status: 500, statusText: 'Server Error' });
    expect(internals.unread()).toBe(5);
  });

  it('does not poll and stays at zero when the session is not authenticated (G1-D.1)', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated: () => false, roles: () => [] } },
      ],
    });
    fixture = TestBed.createComponent(NotificationBell);
    http = TestBed.inject(HttpTestingController);
    internals = fixture.componentInstance as unknown as Internals;
    fixture.detectChanges();

    http.expectNone(UNREAD_URL);
    expect(internals.unread()).toBe(0);
  });
});
