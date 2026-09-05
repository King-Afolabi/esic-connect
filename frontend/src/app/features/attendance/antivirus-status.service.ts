import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

import { environment } from '../../../environments/environment';

/**
 * État réel de l'analyse antivirus des pièces jointes (dette T-04 ;
 * EF-JUS-002 ; docs/02 §19.2).
 *
 * <p><strong>Pourquoi l'interface en a besoin.</strong> Le produit sait
 * qu'aucun analyseur n'est joignable — il marque chaque pièce
 * {@code NOT_SCANNED}. Mais une personne qui dépose un justificatif ne
 * lit pas les colonnes de la base : sans cette information, l'absence de
 * message se lirait comme une protection en place.
 *
 * <p>L'état est chargé une fois et mémorisé : c'est une propriété du
 * déploiement, pas une donnée qui change d'une minute à l'autre.
 */
@Injectable({ providedIn: 'root' })
export class AntivirusStatusService {
  private readonly http = inject(HttpClient);
  private readonly _status = signal<AntivirusStatus | null>(null);
  private loading = false;

  /** `null` tant que l'état n'est pas connu : ne rien affirmer entre-temps. */
  readonly status = this._status.asReadonly();

  load(): void {
    if (this.loading || this._status() !== null) {
      return;
    }
    this.loading = true;
    this.http
      .get<AntivirusStatus>(`${environment.apiBaseUrl}/v1/attendance/antivirus/status`)
      .subscribe({
        next: (status) => {
          this._status.set(status);
          this.loading = false;
        },
        // En cas d'échec, l'état reste inconnu et l'interface se tait :
        // annoncer « inactif » sur une simple erreur réseau serait aussi
        // faux qu'annoncer « actif ».
        error: () => {
          this.loading = false;
        },
      });
  }
}

export interface AntivirusStatus {
  /**
   * `false` : **aucun contrôle antivirus n'a lieu**. Les pièces sont
   * marquées `NOT_SCANNED` et ne doivent jamais être présentées comme
   * saines.
   */
  readonly active: boolean;
  /** Une pièce sans verdict exploitable est retenue et non téléchargeable. */
  readonly quarantineRequired: boolean;
}
