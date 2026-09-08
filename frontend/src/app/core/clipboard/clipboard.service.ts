import { Injectable } from '@angular/core';

/**
 * Copie de texte dans le presse-papiers, sur **action explicite de
 * l'utilisateur** uniquement.
 *
 * - jamais de copie automatique au chargement ;
 * - jamais de **lecture** du presse-papiers ;
 * - aucun stockage (`localStorage` / `sessionStorage` interdits) ;
 * - si l'API Clipboard est absente ou refuse l'écriture, renvoie
 *   `false` : l'appelant bascule alors sur une sélection manuelle d'un
 *   champ `readonly`.
 *
 * N'affiche jamais le contenu copié dans un retour transitoire : seul
 * l'appelant décide du message (« URL copiée », etc.).
 */
@Injectable({ providedIn: 'root' })
export class ClipboardService {
  /**
   * @returns `true` si l'écriture a réussi, `false` sinon (API absente,
   *          permission refusée, contexte non sécurisé, exception).
   */
  async copy(text: string): Promise<boolean> {
    if (typeof navigator === 'undefined' || !navigator.clipboard?.writeText) {
      return false;
    }
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      return false;
    }
  }
}
