import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subscription, filter } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Message échangé entre onglets (Lot A — synchronisation multi-onglets).
 * Aucune donnée sensible : ni jeton, ni identité, seulement l'événement.
 */
interface SessionSignal {
  type: 'renewed' | 'ended';
}

const CHANNEL_NAME = 'esic-session';
/**
 * Verrou de renouvellement partagé entre onglets. Sa valeur est un simple
 * horodatage (chaîne) : ce n'est PAS un jeton, RG-093 ne s'y applique pas.
 * Il borne à ~1 le nombre de renouvellements proactifs concurrents, ce qui
 * évite de déclencher la détection de rejeu de la rotation de cookie côté
 * back-end (« un cookie dont le secret ne correspond plus coupe la
 * famille »).
 */
const RENEW_LOCK_KEY = 'esic-session-renew-lock';
const RENEW_LOCK_TTL_MS = 8_000;

/**
 * Expiration glissante de session, pilotée par l'activité (Lot A).
 *
 * Le jeton d'accès est de courte durée (15 min par défaut) et n'était
 * jusqu'ici renouvelé que **réactivement**, au premier `401` d'un appel
 * métier. Un utilisateur qui lit, saisit ou navigue sans déclencher
 * d'appel voyait sa session tomber sans préavis, et perdait sa place.
 *
 * Ce service ajoute, SANS affaiblir MFA, CSRF, la rotation de jetons ni
 * l'expiration serveur :
 *
 * - un renouvellement **proactif** quand (a) une activité *significative*
 *   récente est constatée — clic, frappe, navigation interne, soumission,
 *   jamais un simple survol souris — et (b) le jeton approche de son
 *   terme. Le renouvellement est throttlé (au plus un par
 *   `minRenewIntervalMs`) : aucune requête permanente ;
 * - un **avertissement** accessible peu avant l'expiration lorsqu'aucune
 *   activité ne justifie un renouvellement, avec « Continuer la session » ;
 * - le respect du **plafond absolu** back-end : passé celui-ci, plus aucun
 *   renouvellement, l'avertissement bascule en fin de session ;
 * - la **synchronisation multi-onglets** : un renouvellement ou une fin de
 *   session dans un onglet est répercuté dans les autres ;
 * - la **préservation de la route de retour** (`AuthService.expireSession`).
 *
 * Démarré par la coquille applicative (présente uniquement quand une
 * session est ouverte) et arrêté à sa destruction.
 */
@Injectable({ providedIn: 'root' })
export class SessionActivityService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly config = environment.session;

  /** Avertissement de fin de session visible (piloté par un composant). */
  readonly warningVisible = signal(false);
  /** Renouvellement déclenché par « Continuer la session » en cours. */
  readonly renewing = signal(false);
  /** Millisecondes restantes affichables dans l'avertissement. */
  readonly msRemaining = signal(0);

  private lastActivityAt = 0;
  private lastRenewAt = 0;
  private sessionStartedAt = 0;
  private started = false;

  private tickHandle: ReturnType<typeof setInterval> | null = null;
  private channel: BroadcastChannel | null = null;
  private routerSub: Subscription | null = null;
  private readonly activityEvents = ['pointerdown', 'keydown', 'submit'] as const;

  /**
   * Démarre la surveillance. Idempotent. `startedAt` permet à un test —
   * ou à une reprise de session — de fixer le début absolu ; par défaut
   * « maintenant ».
   */
  start(startedAt: number = Date.now()): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.sessionStartedAt = startedAt;
    this.lastActivityAt = Date.now();
    this.lastRenewAt = 0;

    for (const type of this.activityEvents) {
      this.document.addEventListener(type, this.onActivity, { capture: true, passive: true });
    }
    this.routerSub = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => this.onActivity());

    if (typeof BroadcastChannel !== 'undefined') {
      this.channel = new BroadcastChannel(CHANNEL_NAME);
      this.channel.onmessage = (event: MessageEvent<SessionSignal>) =>
        this.onSignal(event.data);
    }

    this.tickHandle = setInterval(() => this.tick(), this.config.pollIntervalMs);
    this.tick();
  }

  /** Arrête tout et remet l'état d'affichage à zéro. */
  stop(): void {
    if (!this.started) {
      return;
    }
    this.started = false;
    for (const type of this.activityEvents) {
      this.document.removeEventListener(type, this.onActivity, { capture: true });
    }
    this.routerSub?.unsubscribe();
    this.routerSub = null;
    if (this.tickHandle !== null) {
      clearInterval(this.tickHandle);
      this.tickHandle = null;
    }
    this.channel?.close();
    this.channel = null;
    this.warningVisible.set(false);
    this.renewing.set(false);
  }

  /**
   * « Continuer la session » : renouvellement explicite. Ferme
   * l'avertissement en cas de succès, termine la session en cas d'échec
   * (le cookie de renouvellement est mort).
   */
  continueSession(): void {
    if (this.renewing()) {
      return;
    }
    this.renewing.set(true);
    this.auth.refreshSession().subscribe((renewed) => {
      this.renewing.set(false);
      if (renewed) {
        this.lastRenewAt = Date.now();
        this.warningVisible.set(false);
        this.broadcast({ type: 'renewed' });
      } else {
        this.endSession();
      }
    });
  }

  /** « Se déconnecter » depuis l'avertissement. */
  endNow(): void {
    this.broadcast({ type: 'ended' });
    this.auth.logout();
    this.warningVisible.set(false);
  }

  // ------------------------------------------------------------------

  private readonly onActivity = (): void => {
    const now = Date.now();
    if (now - this.lastActivityAt >= this.config.activityThrottleMs) {
      this.lastActivityAt = now;
    }
  };

  private tick(): void {
    const session = this.auth.session();
    if (!session) {
      this.warningVisible.set(false);
      return;
    }

    const now = Date.now();
    const msLeft = session.expiresAt - now;
    const absoluteLeft = this.sessionStartedAt + this.config.absoluteMaxMs - now;
    this.msRemaining.set(Math.max(0, msLeft));

    // Plafond absolu atteint : le back-end refusera tout renouvellement.
    if (absoluteLeft <= 0) {
      this.endSession();
      return;
    }

    if (msLeft <= 0) {
      this.endSession();
      return;
    }

    const activeRecently = now - this.lastActivityAt <= this.config.activityWindowMs;
    const renewWouldExceedAbsolute = absoluteLeft <= this.config.renewLeadMs;

    if (
      activeRecently &&
      !renewWouldExceedAbsolute &&
      msLeft <= this.config.renewLeadMs &&
      now - this.lastRenewAt >= this.config.minRenewIntervalMs
    ) {
      this.renewSilently();
      return;
    }

    // Aucun renouvellement en vue et le jeton va bientôt tomber : prévenir.
    this.warningVisible.set(msLeft <= this.config.warningLeadMs);
  }

  private renewSilently(): void {
    if (!this.acquireRenewLock()) {
      // Un autre onglet renouvelle : ne pas rejouer le cookie en parallèle.
      this.lastRenewAt = Date.now();
      return;
    }
    this.lastRenewAt = Date.now();
    this.auth.refreshSession().subscribe((renewed) => {
      this.releaseRenewLock();
      if (renewed) {
        this.warningVisible.set(false);
        this.broadcast({ type: 'renewed' });
      }
      // Échec : le prochain tick ou le prochain 401 métier gère la fin.
    });
  }

  private endSession(): void {
    this.warningVisible.set(false);
    this.broadcast({ type: 'ended' });
    this.auth.expireSession();
  }

  private onSignal(signal: SessionSignal): void {
    if (signal.type === 'ended') {
      this.warningVisible.set(false);
      this.auth.expireSession();
      return;
    }
    // 'renewed' dans un autre onglet : notre jeton en mémoire reste le
    // nôtre, mais la session est saine. On lève l'avertissement et on
    // laisse le prochain tick renouveler notre propre jeton si besoin.
    this.lastRenewAt = Date.now();
    this.warningVisible.set(false);
  }

  private broadcast(signal: SessionSignal): void {
    this.channel?.postMessage(signal);
  }

  private acquireRenewLock(): boolean {
    try {
      const raw = localStorage.getItem(RENEW_LOCK_KEY);
      const now = Date.now();
      if (raw) {
        const heldAt = Number(raw);
        if (Number.isFinite(heldAt) && now - heldAt < RENEW_LOCK_TTL_MS) {
          return false;
        }
      }
      localStorage.setItem(RENEW_LOCK_KEY, String(now));
      return true;
    } catch {
      // Stockage indisponible (navigation privée, quota) : on renouvelle
      // quand même — un seul onglet est le cas courant.
      return true;
    }
  }

  private releaseRenewLock(): void {
    try {
      localStorage.removeItem(RENEW_LOCK_KEY);
    } catch {
      /* sans effet */
    }
  }
}
