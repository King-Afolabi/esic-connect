import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { OutboxConsole } from './outbox-console';
import { OutboxMessage } from './outbox-api.service';

const LIST_URL = (status: string | null) =>
  status ? `/api/v1/outbox/messages?status=${status}&page=0&size=20` : '/api/v1/outbox/messages?page=0&size=20';
const SUMMARY_URL = '/api/v1/outbox/messages/summary';

function message(over: Partial<OutboxMessage> = {}): OutboxMessage {
  return {
    publicId: 'm-1',
    messageType: 'NOTIFICATION_EMAIL',
    status: 'DEAD',
    attempts: 5,
    lastError: 'MailSendException',
    nextAttemptAt: '2026-09-10T08:00:00Z',
    createdAt: '2026-09-10T07:00:00Z',
    processedAt: '2026-09-10T07:30:00Z',
    ...over,
  };
}

const SUMMARY = { pending: 2, failed: 1, dead: 3, sent: 40 };

interface Internals {
  replay: (message: OutboxMessage) => void;
  changeFilter: (filter: 'DEAD' | 'FAILED' | 'PENDING' | 'ALL') => void;
}

function setup(): {
  fixture: ComponentFixture<OutboxConsole>;
  http: HttpTestingController;
  text: () => string;
  internals: () => Internals;
  toasts: { messages: string[] };
} {
  TestBed.resetTestingModule();
  const toasts = { messages: [] as string[] };
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: NotificationService,
        useValue: {
          error: (m: string) => toasts.messages.push(m),
          info: (m: string) => toasts.messages.push(m),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(OutboxConsole);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return {
    fixture,
    http,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    internals: () => fixture.componentInstance as unknown as Internals,
    toasts,
  };
}

/** File d'échec des effets de bord (EF-OPS-005 ; docs/02 §25.2). */
describe('OutboxConsole', () => {
  it('ouvre sur la file d’échec — la seule qui appelle une décision humaine', () => {
    const { http, fixture, text } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [message()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    expect(text()).toContain('NOTIFICATION_EMAIL');
    expect(text()).toContain('MailSendException');
    expect(text()).toContain('Rejouer');
  });

  it('n’affiche jamais le contenu du message — l’API ne le renvoie pas', () => {
    const { http, fixture, text } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [message()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    expect(text()).toContain('n’est jamais affiché');
  });

  it('ne propose le rejeu que sur un message abandonné', () => {
    const { http, fixture, text } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [message({ status: 'FAILED', publicId: 'm-2' })],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    // Un message encore en reprise se résorbe seul : proposer un bouton
    // laisserait croire à une action nécessaire.
    expect(text()).toContain('Reprise automatique');
    expect(text()).not.toContain('Rejouer');
  });

  it('rejoue un message abandonné puis recharge la file', () => {
    const { http, fixture, internals, toasts } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [message()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    internals().replay(message());
    const replay = http.expectOne('/api/v1/outbox/messages/m-1/replay');
    expect(replay.request.method).toBe('POST');
    replay.flush(null);

    // Le rejeu recharge : c'est le serveur qui dit ce qu'il est devenu.
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    http.expectOne(SUMMARY_URL).flush({ ...SUMMARY, dead: 2 });
    fixture.detectChanges();

    expect(toasts.messages).toContain('Effet de bord remis en file.');
  });

  it('signale un rejeu refusé sans laisser l’écran dans un état inventé', () => {
    const { http, fixture, internals, toasts } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [message()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    internals().replay(message());
    http
      .expectOne('/api/v1/outbox/messages/m-1/replay')
      .flush({ code: 'OUTBOX_NOT_REPLAYABLE' }, { status: 409, statusText: 'Conflict' });

    http.expectOne(LIST_URL('DEAD')).flush({
      content: [message()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    expect(toasts.messages).toContain('Ce message n’a pas pu être rejoué.');
  });

  it('change de filtre en interrogeant le serveur', () => {
    const { http, fixture, internals } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    internals().changeFilter('ALL');
    // Filtre « Tous » : aucun paramètre `status` n'est transmis.
    http.expectOne(LIST_URL(null)).flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();
    http.verify();
  });

  it('propose de réessayer quand la file ne se charge pas', () => {
    const { http, fixture, text } = setup();
    http.expectOne(LIST_URL('DEAD')).flush({}, { status: 500, statusText: 'Server Error' });
    http.expectOne(SUMMARY_URL).flush(SUMMARY);
    fixture.detectChanges();

    expect(text()).toContain('n’a pas pu être chargée');
    expect(text()).toContain('Réessayer');
  });
});
