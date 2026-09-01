import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/notifications/notification.service';
import { NotificationList } from './notification-list';
import { NotificationView } from '../notifications.models';

const LIST_URL = '/api/v1/me/notifications';
const UNREAD_URL = '/api/v1/me/notifications/unread-count';

function notif(over: Partial<NotificationView> = {}): NotificationView {
  return {
    publicId: 'n-1',
    type: 'SESSION_CANCELLED',
    title: 'Séance annulée',
    body: 'La séance « Rattrapage » a été annulée.',
    resourceType: 'COURSE_SESSION',
    resourcePublicId: 's-1',
    status: 'UNREAD',
    createdAt: '2026-09-10T08:00:00Z',
    readAt: null,
    ...over,
  };
}

function page(content: NotificationView[], totalPages = 1) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages };
}

interface Internals {
  setFilter: (v: 'ALL' | 'UNREAD') => void;
  markRead: (n: NotificationView) => void;
  markAllRead: () => void;
  retry: () => void;
  items: () => NotificationView[];
  hasUnread: () => boolean;
}

function setup(roles: string[] = ['TEACHER']) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: NotificationService, useValue: { info: vi.fn(), error: vi.fn() } },
      { provide: AuthService, useValue: { isAuthenticated: () => true, roles: () => roles } },
    ],
  });
  const fixture = TestBed.createComponent(NotificationList);
  const http = TestBed.inject(HttpTestingController);
  const internals = fixture.componentInstance as unknown as Internals;
  fixture.detectChanges();
  return { fixture, http, internals };
}

describe('NotificationList', () => {
  let fixture: ComponentFixture<NotificationList>;
  let http: HttpTestingController;
  let internals: Internals;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const flushInitial = (content: NotificationView[]) => {
    http.expectOne((r) => r.url === LIST_URL && !r.params.has('status')).flush(page(content));
    http.expectOne(UNREAD_URL).flush({ unread: content.filter((n) => n.status === 'UNREAD').length });
  };

  afterEach(() => {
    http.match(UNREAD_URL).forEach((r) => !r.cancelled && r.flush({ unread: 0 }));
    http.verify();
  });

  it('lists notifications with the newest-first content from the server', () => {
    ({ fixture, http, internals } = setup());
    flushInitial([notif({ publicId: 'a', title: 'Alpha' }), notif({ publicId: 'b', title: 'Beta' })]);
    fixture.detectChanges();
    expect(text()).toContain('Alpha');
    expect(text()).toContain('Beta');
    expect(internals.items()).toHaveLength(2);
  });

  it('shows an empty panel when there is nothing', () => {
    ({ fixture, http, internals } = setup());
    flushInitial([]);
    fixture.detectChanges();
    expect(text()).toContain('Aucune notification');
  });

  it('shows an error panel with retry on a failed load', () => {
    ({ fixture, http, internals } = setup());
    // Échec du chargement : pas de rafraîchissement du badge (uniquement au succès).
    http.expectOne((r) => r.url === LIST_URL).flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(text()).toContain('Réessayer');

    internals.retry();
    http.expectOne((r) => r.url === LIST_URL).flush(page([notif()]));
    http.expectOne(UNREAD_URL).flush({ unread: 1 });
    fixture.detectChanges();
    expect(text()).toContain('Séance annulée');
  });

  it('applies the "unread only" filter by re-querying with status=UNREAD', () => {
    ({ fixture, http, internals } = setup());
    flushInitial([notif()]);
    fixture.detectChanges();

    internals.setFilter('UNREAD');
    http.expectOne((r) => r.url === LIST_URL && r.params.get('status') === 'UNREAD').flush(page([notif()]));
    http.expectOne(UNREAD_URL).flush({ unread: 1 });
  });

  it('marks a single notification as read and refreshes the list and the badge', () => {
    ({ fixture, http, internals } = setup());
    flushInitial([notif({ publicId: 'n-9' })]);
    fixture.detectChanges();

    internals.markRead(notif({ publicId: 'n-9' }));
    http.expectOne((r) => r.url === `${LIST_URL}/n-9/read` && r.method === 'POST').flush(null, {
      status: 204,
      statusText: 'No Content',
    });
    // Badge refresh + list reload.
    http.expectOne(UNREAD_URL).flush({ unread: 0 });
    http.expectOne((r) => r.url === LIST_URL).flush(page([notif({ publicId: 'n-9', status: 'READ' })]));
    http.expectOne(UNREAD_URL).flush({ unread: 0 });
    fixture.detectChanges();
    expect(internals.hasUnread()).toBe(false);
  });

  it('marks all as read only when there is something unread', () => {
    ({ fixture, http, internals } = setup());
    flushInitial([notif({ status: 'READ', readAt: '2026-09-10T09:00:00Z' })]);
    fixture.detectChanges();
    // Rien de non lu -> aucun appel.
    internals.markAllRead();
    http.expectNone((r) => r.url === `${LIST_URL}/read-all`);
  });

  it('shows the session link for a TEACHER but hides the planning link (G1-D.1)', () => {
    ({ fixture, http, internals } = setup(['TEACHER']));
    flushInitial([
      notif({ publicId: 's', resourceType: 'COURSE_SESSION', resourcePublicId: '11111111-1111-4111-8111-111111111111' }),
      notif({ publicId: 'p', type: 'PLANNING_PUBLISHED', resourceType: 'PLANNING_VERSION', resourcePublicId: '22222222-2222-4222-8222-222222222222' }),
    ]);
    fixture.detectChanges();
    const hrefs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toContain('/sessions/11111111-1111-4111-8111-111111111111');
    expect(hrefs).not.toContain('/planning/versions');
  });

  it('shows the planning link for a PEDAGOGICAL_MANAGER (G1-D.1)', () => {
    ({ fixture, http, internals } = setup(['PEDAGOGICAL_MANAGER']));
    flushInitial([
      notif({ publicId: 'p', type: 'PLANNING_PUBLISHED', resourceType: 'PLANNING_VERSION', resourcePublicId: '22222222-2222-4222-8222-222222222222' }),
    ]);
    fixture.detectChanges();
    const hrefs = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).map((a) =>
      a.getAttribute('href'),
    );
    expect(hrefs).toContain('/planning/versions');
  });

  it('renders the body even when no safe link is available (G1-D.1)', () => {
    ({ fixture, http, internals } = setup([]));
    flushInitial([
      notif({ resourceType: 'COURSE_SESSION', resourcePublicId: '33333333-3333-4333-8333-333333333333' }),
    ]);
    fixture.detectChanges();
    expect(text()).toContain('La séance');
    expect((fixture.nativeElement as HTMLElement).querySelector('.notif__meta a')).toBeNull();
  });

  it('never exposes a SQL identifier in the rendered rows', () => {
    ({ fixture, http, internals } = setup());
    flushInitial([notif()]);
    fixture.detectChanges();
    expect(Object.keys(internals.items()[0])).not.toContain('id');
    expect(Object.keys(internals.items()[0])).not.toContain('recipientUserId');
    expect(Object.keys(internals.items()[0])).not.toContain('dedupKey');
  });
});
