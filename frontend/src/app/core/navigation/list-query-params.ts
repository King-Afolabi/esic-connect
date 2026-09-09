import { ActivatedRoute, Params, Router } from '@angular/router';

/**
 * Persistance de l'état d'une liste (filtres, recherche, tri, pagination)
 * dans les **paramètres d'URL** (Lot G).
 *
 * Objectif : après « ouvrir une fiche → Retour », l'utilisateur retrouve
 * exactement sa liste — mêmes filtres, même page, même tri — et une URL
 * ouverte directement reconstruit le même état. L'URL est le support :
 * elle est partageable, elle survit au rechargement, et le composant de
 * liste étant recréé au retour, il n'a qu'à relire `queryParamMap`.
 *
 * Conventions :
 * - une valeur vide / `null` / `0` (page par défaut) n'est **pas** écrite,
 *   pour garder l'URL propre ;
 * - l'écriture se fait en `replaceUrl` : ajuster un filtre ne crée pas une
 *   entrée d'historique par frappe ;
 * - `queryParamsHandling: 'merge'` : on ne touche qu'aux clés fournies.
 *
 * Aucune donnée sensible ne doit transiter ici (pas d'identifiant de
 * session, pas de jeton) — uniquement des critères d'affichage.
 */

/** Lecture typée d'un paramètre d'URL au démarrage d'une liste. */
export class ListQueryReader {
  private readonly params: Params;

  constructor(route: ActivatedRoute) {
    this.params = route.snapshot.queryParams;
  }

  /** Chaîne brute, ou `fallback` si absente / vide. */
  str(key: string, fallback = ''): string {
    const value = this.params[key];
    return typeof value === 'string' && value.length > 0 ? value : fallback;
  }

  /** Entier ≥ 0, ou `fallback` si absent / invalide. */
  int(key: string, fallback: number): number {
    const value = Number(this.params[key]);
    return Number.isInteger(value) && value >= 0 ? value : fallback;
  }

  /** Valeur contrainte à une liste fermée, sinon `fallback`. */
  oneOf<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
    const value = this.params[key];
    return typeof value === 'string' && (allowed as readonly string[]).includes(value)
      ? (value as T)
      : fallback;
  }

  /** `'asc'` / `'desc'`, sinon `fallback`. */
  direction(key: string, fallback: 'asc' | 'desc'): 'asc' | 'desc' {
    return this.params[key] === 'asc' || this.params[key] === 'desc'
      ? (this.params[key] as 'asc' | 'desc')
      : fallback;
  }

  /** Vrai si au moins une des clés est présente (état à restaurer). */
  hasAny(keys: readonly string[]): boolean {
    return keys.some((key) => this.params[key] != null && this.params[key] !== '');
  }
}

/**
 * Écrit l'état courant dans l'URL. Les clés dont la valeur est `null`,
 * `undefined`, `''` ou `0` sont **retirées** de l'URL (`queryParams` à
 * `null` supprime la clé avec `merge`).
 */
export function writeListQueryParams(
  router: Router,
  route: ActivatedRoute,
  values: Record<string, string | number | null | undefined>,
): void {
  const queryParams: Params = {};
  for (const [key, value] of Object.entries(values)) {
    queryParams[key] =
      value === null || value === undefined || value === '' || value === 0
        ? null
        : String(value);
  }
  void router.navigate([], {
    relativeTo: route,
    queryParams,
    queryParamsHandling: 'merge',
    replaceUrl: true,
  });
}
