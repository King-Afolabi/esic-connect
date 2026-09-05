import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotificationService } from '../../../core/notifications/notification.service';
import { NotificationPreferences } from './notification-preferences';
import {
  NotificationPreference,
  PushStatus,
} from '../notification-settings-api.service';

const PREF_URL = '/api/v1/me/notification-preferences';
const PUSH_URL = '/api/v1/me/push/status';

function pref(
  category: string,
  channel: string,
  enabled = true,
  locked = false,
): NotificationPreference {
  return { category, channel, enabled, locked };
}

const DEFAULT_PREFS: NotificationPreference[] = [
  pref('SESSION', 'IN_APP', true, true),
  pref('SESSION', 'EMAIL'),
  pref('SESSION', 'PUSH'),
  pref('SECURITY', 'IN_APP', true, true),
  pref('SECURITY', 'EMAIL', true, true),
  pref('SECURITY', 'PUSH', true, true),
];

const INACTIVE_PUSH: PushStatus = { providerActive: false, subscriptions: [] };

interface Internals {
  toggle: (preference: NotificationPreference, enabled: boolean) => void;
}

function setup(): {
  fixture: ComponentFixture<NotificationPreferences>;
  http: HttpTestingController;
  text: () => string;
  internals: () => Internals;
} {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      { provide: NotificationService, useValue: { error: () => undefined, info: () => undefined } },
    ],
  });
  const fixture = TestBed.createComponent(NotificationPreferences);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  return {
    fixture,
    http,
    text: () => (fixture.nativeElement as HTMLElement).textContent ?? '',
    internals: () => fixture.componentInstance as unknown as Internals,
  };
}

/** Préférences de notification (EF-NOTIF-006 ; docs/02 §21.6). */
describe('NotificationPreferences', () => {
  it('affiche une ligne par catégorie et une colonne par canal', () => {
    const { http, fixture, text } = setup();
    http.expectOne(PREF_URL).flush({ preferences: DEFAULT_PREFS });
    http.expectOne(PUSH_URL).flush(INACTIVE_PUSH);
    fixture.detectChanges();

    expect(text()).toContain('Séances');
    expect(text()).toContain('Sécurité du compte');
    expect(text()).toContain('Courriel');
    expect(text()).toContain('Notification poussée');
  });

  it('marque les réglages verrouillés « Toujours actif » plutôt que de les proposer', () => {
    const { http, fixture, text } = setup();
    http.expectOne(PREF_URL).flush({ preferences: DEFAULT_PREFS });
    http.expectOne(PUSH_URL).flush(INACTIVE_PUSH);
    fixture.detectChanges();

    // Le centre de notifications et les alertes de sécurité restent
    // toujours actifs : l'écran l'annonce au lieu de laisser cliquer sur
    // un interrupteur que le serveur refuserait.
    expect(text()).toContain('Toujours actif');
    const toggles = (fixture.nativeElement as HTMLElement).querySelectorAll('mat-slide-toggle');
    expect(toggles.length).toBe(DEFAULT_PREFS.length);
  });

  it('enregistre un changement de canal et applique la réponse du serveur', () => {
    const { http, fixture, internals } = setup();
    http.expectOne(PREF_URL).flush({ preferences: DEFAULT_PREFS });
    http.expectOne(PUSH_URL).flush(INACTIVE_PUSH);
    fixture.detectChanges();

    internals().toggle(pref('SESSION', 'EMAIL'), false);
    const request = http.expectOne(PREF_URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      category: 'SESSION',
      channel: 'EMAIL',
      enabled: false,
    });
    request.flush({
      preferences: DEFAULT_PREFS.map((p) =>
        p.category === 'SESSION' && p.channel === 'EMAIL' ? { ...p, enabled: false } : p,
      ),
    });
    fixture.detectChanges();
    http.verify();
  });

  it('n’émet aucune requête pour un réglage verrouillé', () => {
    const { http, fixture, internals } = setup();
    http.expectOne(PREF_URL).flush({ preferences: DEFAULT_PREFS });
    http.expectOne(PUSH_URL).flush(INACTIVE_PUSH);
    fixture.detectChanges();

    internals().toggle(pref('SECURITY', 'EMAIL', true, true), false);

    http.verify(); // aucune requête supplémentaire
  });

  it('annonce que la poussée est inactive quand le serveur ne l’a pas configurée', () => {
    const { http, fixture, text } = setup();
    http.expectOne(PREF_URL).flush({ preferences: DEFAULT_PREFS });
    http.expectOne(PUSH_URL).flush(INACTIVE_PUSH);
    fixture.detectChanges();

    // Le produit dit ce qui est, plutôt que d'afficher un réglage sans
    // effet : c'est la même honnêteté que pour l'antivirus.
    expect(text()).toContain('ne sont pas activées sur ce serveur');
    expect(text()).toContain('Aucun appareil abonné');
  });

  it('annonce la poussée active et liste les appareils quand elle l’est', () => {
    const { http, fixture, text } = setup();
    http.expectOne(PREF_URL).flush({ preferences: DEFAULT_PREFS });
    http.expectOne(PUSH_URL).flush({
      providerActive: true,
      subscriptions: [
        {
          publicId: 'p-1',
          active: true,
          createdAt: '2026-09-01T10:00:00Z',
          lastUsedAt: null,
          revokedAt: null,
        },
      ],
    } satisfies PushStatus);
    fixture.detectChanges();

    expect(text()).toContain('sont actives sur ce serveur');
    expect(text()).toContain('actif');
  });

  it('propose de réessayer quand le chargement échoue', () => {
    const { http, fixture, text } = setup();
    http.expectOne(PREF_URL).flush({}, { status: 500, statusText: 'Server Error' });
    http.expectOne(PUSH_URL).flush(INACTIVE_PUSH);
    fixture.detectChanges();

    expect(text()).toContain('n’ont pas pu être chargées');
    expect(text()).toContain('Réessayer');
  });
});
