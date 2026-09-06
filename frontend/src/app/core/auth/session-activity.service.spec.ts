import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject, of } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Session } from '../models/session';
import { AuthService } from './auth.service';
import { SessionActivityService } from './session-activity.service';

const CFG = environment.session;

function makeSession(expiresInMs: number): Session {
  return {
    accessToken: 'tok',
    subject: 'public-1',
    roles: [],
    email: 'u@esic.test',
    expiresAt: Date.now() + expiresInMs,
  };
}

/** Accès aux membres privés testés directement (synchronisation multi-onglets). */
interface Internals {
  onSignal(s: { type: 'renewed' | 'ended' }): void;
}

describe('SessionActivityService', () => {
  let service: SessionActivityService;
  let sessionSig: ReturnType<typeof signal<Session | null>>;
  let refreshSession: ReturnType<typeof vi.fn>;
  let expireSession: ReturnType<typeof vi.fn>;
  let logout: ReturnType<typeof vi.fn>;
  let routerEvents: Subject<unknown>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-06T10:00:00Z'));

    sessionSig = signal<Session | null>(null);
    refreshSession = vi.fn(() => {
      // Émule le renouvellement réel : le jeton d'accès repart pour 15 min.
      const current = sessionSig();
      if (current) {
        sessionSig.set({ ...current, expiresAt: Date.now() + 900_000 });
      }
      return of(true);
    });
    expireSession = vi.fn(() => sessionSig.set(null));
    logout = vi.fn(() => sessionSig.set(null));
    routerEvents = new Subject();

    TestBed.configureTestingModule({
      providers: [
        SessionActivityService,
        {
          provide: AuthService,
          useValue: { session: () => sessionSig(), refreshSession, expireSession, logout },
        },
        { provide: Router, useValue: { events: routerEvents.asObservable(), url: '/students' } },
      ],
    });
    service = TestBed.inject(SessionActivityService);
  });

  afterEach(() => {
    service.stop();
    vi.useRealTimers();
    try {
      localStorage.clear();
    } catch {
      /* stockage indisponible : sans effet sur le test */
    }
  });

  it('renews proactively when the user is active and the token is near expiry', () => {
    sessionSig.set(makeSession(CFG.renewLeadMs - 30_000));
    service.start(); // start() marque une activité « maintenant »

    vi.advanceTimersByTime(CFG.pollIntervalMs);

    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(expireSession).not.toHaveBeenCalled();
  });

  it('lets the session expire when there is no significant activity', () => {
    sessionSig.set(makeSession(900_000));
    service.start();

    // Personne ne touche à rien : l'activité initiale se périme, puis le jeton.
    vi.advanceTimersByTime(900_000 + CFG.pollIntervalMs);

    expect(refreshSession).not.toHaveBeenCalled();
    expect(expireSession).toHaveBeenCalled();
  });

  it('shows an accessible warning shortly before expiry, without activity', () => {
    sessionSig.set(makeSession(900_000));
    service.start();

    vi.advanceTimersByTime(900_000 - CFG.warningLeadMs + CFG.pollIntervalMs);

    expect(service.warningVisible()).toBe(true);
    expect(expireSession).not.toHaveBeenCalled();
  });

  it('throttles renewal — one refresh, not one per tick', () => {
    sessionSig.set(makeSession(CFG.renewLeadMs - 60_000));
    service.start();

    for (let i = 0; i < 6; i++) {
      document.dispatchEvent(new Event('pointerdown'));
      vi.advanceTimersByTime(CFG.pollIntervalMs);
    }

    // Le premier tick renouvelle et repousse l'échéance ; les suivants
    // voient un jeton frais et ne redemandent rien.
    expect(refreshSession).toHaveBeenCalledTimes(1);
  });

  it('stops renewing near the absolute cap and then ends the session', () => {
    const startedAt = Date.now() - (CFG.absoluteMaxMs - 40_000);
    sessionSig.set(makeSession(900_000)); // jeton d'accès sain
    service.start(startedAt);
    document.dispatchEvent(new Event('pointerdown'));

    vi.advanceTimersByTime(CFG.pollIntervalMs);
    expect(refreshSession).not.toHaveBeenCalled(); // renouvellement interdit près du plafond
    expect(expireSession).not.toHaveBeenCalled();

    vi.advanceTimersByTime(60_000); // on franchit le plafond absolu
    expect(expireSession).toHaveBeenCalled();
  });

  it('multi-tab: an "ended" signal from another tab ends this session', () => {
    sessionSig.set(makeSession(900_000));
    service.start();

    (service as unknown as Internals).onSignal({ type: 'ended' });

    expect(expireSession).toHaveBeenCalled();
  });

  it('multi-tab: a "renewed" signal clears a pending warning', () => {
    sessionSig.set(makeSession(900_000));
    service.start();
    service.warningVisible.set(true);

    (service as unknown as Internals).onSignal({ type: 'renewed' });

    expect(service.warningVisible()).toBe(false);
  });

  it('does nothing once stopped', () => {
    sessionSig.set(makeSession(1_000));
    service.start();
    service.stop();

    vi.advanceTimersByTime(CFG.pollIntervalMs * 5);

    expect(expireSession).not.toHaveBeenCalled();
  });

  it('continueSession renews and hides the warning', () => {
    sessionSig.set(makeSession(30_000));
    service.warningVisible.set(true);

    service.continueSession();

    expect(refreshSession).toHaveBeenCalledTimes(1);
    expect(service.warningVisible()).toBe(false);
  });

  it('continueSession ends the session when the refresh cookie is dead', () => {
    refreshSession.mockReturnValue(of(false));
    sessionSig.set(makeSession(30_000));
    service.warningVisible.set(true);

    service.continueSession();

    expect(expireSession).toHaveBeenCalled();
  });

  it('an internal navigation counts as significant activity and keeps the session alive', () => {
    sessionSig.set(makeSession(900_000));
    service.start();

    // L'activité initiale se périme sans navigation ni interaction.
    vi.advanceTimersByTime(CFG.activityWindowMs + 5_000);
    routerEvents.next(new NavigationEnd(1, '/students/42', '/students/42'));

    // Quand le jeton approche de son terme, l'activité « navigation »
    // récente suffit à déclencher le renouvellement proactif.
    vi.advanceTimersByTime(900_000 - CFG.renewLeadMs - (CFG.activityWindowMs + 5_000) + CFG.pollIntervalMs);

    expect(refreshSession).toHaveBeenCalled();
    expect(expireSession).not.toHaveBeenCalled();
  });

  it('without any navigation or interaction the same run expires instead', () => {
    sessionSig.set(makeSession(900_000));
    service.start();

    vi.advanceTimersByTime(900_000 + CFG.pollIntervalMs);

    expect(refreshSession).not.toHaveBeenCalled();
    expect(expireSession).toHaveBeenCalled();
  });
});
