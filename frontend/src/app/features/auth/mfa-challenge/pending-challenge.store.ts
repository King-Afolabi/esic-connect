import { Injectable, signal } from '@angular/core';

import { MfaChallenge } from '../../../core/models/mfa';

/** Défi en cours, entre la vérification du mot de passe et l'ouverture de session. */
export interface PendingChallenge {
  readonly challenge: MfaChallenge;
  readonly email: string;
  readonly redirect?: string;
}

/**
 * Porte le défi de second facteur entre l'écran de connexion et l'écran
 * de vérification.
 *
 * <p><strong>En mémoire uniquement.</strong> Un identifiant de défi placé
 * dans l'URL entrerait dans l'historique du navigateur, dans le
 * `Referer` et dans les journaux des intermédiaires ; placé dans
 * `localStorage`, il survivrait à la fermeture de l'onglet. Il vaut
 * preuve de la première étape d'authentification : il est traité comme
 * telle.
 *
 * <p>Conséquence assumée : recharger la page pendant la vérification
 * ramène à l'écran de connexion.
 */
@Injectable({ providedIn: 'root' })
export class PendingChallengeStore {
  private readonly _pending = signal<PendingChallenge | null>(null);

  readonly pending = this._pending.asReadonly();

  start(pending: PendingChallenge): void {
    this._pending.set(pending);
  }

  clear(): void {
    this._pending.set(null);
  }
}
