import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ConnectivityService } from './connectivity.service';

/** Cycle de vie d'une action mise en attente (AC-031). */
export type QueuedActionStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED';

export interface QueuedAction {
  /** Identifiant local, sans rapport avec un identifiant serveur. */
  readonly id: string;
  /** Libellé affiché à l'utilisateur — jamais le contenu de la requête. */
  readonly label: string;
  readonly path: string;
  readonly body: unknown;
  readonly queuedAt: string;
  status: QueuedActionStatus;
  /** Message de refus, quand le serveur a tranché contre l'action. */
  reason?: string;
}

/**
 * File d'actions différées et rejeu au retour du réseau (EF-PWA-003 ;
 * docs/02 §29.2 ; RG-063, AC-031).
 *
 * <p><strong>La règle qui gouverne tout ce fichier</strong> : « une
 * présence enregistrée hors ligne n'est jamais définitive avant
 * validation par le serveur ». Une action en file est donc affichée
 * <em>en attente de confirmation</em> — jamais comme un émargement
 * réussi — et n'est réputée acquise que lorsque le serveur l'a acceptée.
 * Le rejet est tout aussi visible que l'acceptation : un apprenant qui
 * croirait avoir émargé alors que le serveur a refusé serait plus mal
 * loti qu'un apprenant sachant qu'il n'a pas émargé.
 *
 * <p><strong>La file vit en mémoire, et cela découle de deux
 * contraintes qui pointent dans le même sens.</strong>
 *
 * <p>La première est une règle : le corps d'un émargement contient le
 * <em>code court</em> affiché par le formateur, c'est-à-dire un jeton.
 * RG-093 interdit de placer un jeton sensible dans {@code localStorage},
 * et cette interdiction ne souffre pas d'exception au motif que ce serait
 * commode.
 *
 * <p>La seconde est un fait : ce code vit trente secondes
 * ({@code app.attendance.token-ttl}). Une file qui survivrait au
 * rechargement d'une page ne rejouerait donc que des codes expirés, et
 * offrirait à l'utilisateur une promesse que le serveur refuserait —
 * exactement le genre de faux confort que RG-063 cherche à éviter.
 *
 * <p>Ce que la file couvre réellement, et c'est le cas fréquent : une
 * coupure de quelques secondes pendant que l'application reste ouverte.
 * L'action part dès le retour du réseau, dans la durée de vie du code.
 * Une coupure plus longue, ou une fermeture de l'application, se solde
 * par un refus explicite du serveur — jamais par une présence
 * silencieusement perdue ou silencieusement inventée.
 */
@Injectable({ providedIn: 'root' })
export class OfflineQueueService {
  private readonly http = inject(HttpClient);
  private readonly connectivity = inject(ConnectivityService);

  private readonly _actions = signal<QueuedAction[]>([]);
  private replaying = false;

  readonly actions = this._actions.asReadonly();
  readonly pendingCount = computed(
    () => this._actions().filter((action) => action.status === 'PENDING').length,
  );

  constructor() {
    if (typeof window !== 'undefined') {
      // Le retour du réseau est le seul déclencheur automatique : rejouer
      // en boucle pendant une coupure ne ferait qu'épuiser la batterie.
      window.addEventListener('online', () => void this.replay());
    }
  }

  /**
   * Met une action en attente et rend immédiatement la main : l'écran
   * affiche « en attente de confirmation », sans jamais annoncer un
   * succès que rien ne soutient.
   */
  enqueue(label: string, path: string, body: unknown): QueuedAction {
    const action: QueuedAction = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
      label,
      path,
      body,
      queuedAt: new Date().toISOString(),
      status: 'PENDING',
    };
    this._actions.update((actions) => [...actions, action]);
    return action;
  }

  /**
   * Rejoue les actions en attente, dans l'ordre où elles ont été faites.
   *
   * <p><strong>Résolution de conflit.</strong> Le serveur reste seul
   * juge. Un `409` signifie que la présence est déjà enregistrée : c'est
   * un succès du point de vue de l'utilisateur — le résultat voulu est
   * atteint — et l'action est confirmée. Une erreur `4xx` est une
   * décision du serveur : l'action est marquée refusée, avec son motif,
   * et n'est jamais retentée. Une panne réseau ou un `5xx` laisse
   * l'action en attente pour la prochaine tentative.
   */
  async replay(): Promise<void> {
    if (this.replaying) {
      return;
    }
    this.replaying = true;
    try {
      for (const action of this._actions().filter((a) => a.status === 'PENDING')) {
        const outcome = await this.send(action);
        if (outcome === 'RETRY') {
          break; // le réseau est retombé : inutile d'insister
        }
      }
    } finally {
      this.replaying = false;
    }
  }

  /** Retire les actions déjà tranchées, sur demande de l'utilisateur. */
  clearSettled(): void {
    this._actions.update((actions) => actions.filter((action) => action.status === 'PENDING'));
  }

  /** Vide la file — à la déconnexion. */
  clear(): void {
    this._actions.set([]);
  }

  private async send(action: QueuedAction): Promise<'DONE' | 'RETRY'> {
    try {
      await firstValueFrom(
        this.http.post(`${environment.apiBaseUrl}${action.path}`, action.body, {
          responseType: 'text' as const,
        }),
      );
      this.settle(action.id, 'CONFIRMED');
      return 'DONE';
    } catch (error) {
      const response = error as HttpErrorResponse;
      if (response.status === 409) {
        // Déjà enregistrée : le résultat voulu est atteint.
        this.settle(action.id, 'CONFIRMED');
        return 'DONE';
      }
      if (response.status === 0) {
        this.connectivity.reportNetworkFailure();
        return 'RETRY';
      }
      if (response.status >= 500) {
        return 'RETRY';
      }
      this.settle(action.id, 'REJECTED', messageOf(response));
      return 'DONE';
    }
  }

  private settle(id: string, status: QueuedActionStatus, reason?: string): void {
    this._actions.update((actions) =>
      actions.map((action) => (action.id === id ? { ...action, status, reason } : action)),
    );
  }

}

/**
 * Motif de refus, tel que le serveur l'a formulé.
 *
 * <p>La requête est émise en {@code responseType: 'text'} — le corps
 * d'un succès n'est pas exploité ici — ce qui fait qu'Angular remet le
 * corps d'erreur sous forme de <strong>chaîne</strong>, et non d'objet.
 * Sans cette analyse, le message précis du serveur (« le code
 * d'émargement a expiré ») serait remplacé par une formule générique, et
 * l'apprenant ne saurait pas ce qui s'est réellement passé.
 */
function messageOf(response: HttpErrorResponse): string {
  const generic = 'Le serveur a refusé cet émargement. Rapprochez-vous de votre formateur.';
  const body: unknown = response.error;
  const parsed = typeof body === 'string' ? tryParse(body) : body;
  if (parsed && typeof parsed === 'object' && 'message' in parsed) {
    const message = (parsed as { message?: unknown }).message;
    if (typeof message === 'string' && message.trim().length > 0) {
      return message;
    }
  }
  return generic;
}

function tryParse(raw: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}
