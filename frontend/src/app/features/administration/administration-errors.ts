import {
  NormalizedError,
  SAFE_FALLBACK_MESSAGE,
  normalizeHttpError,
} from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `identity`
 * (`UserManagementExceptionHandler`) en éléments d'affichage.
 *
 * On s'appuie sur {@link normalizeHttpError} : les messages `USER_*` du
 * back-end sont déjà des phrases françaises sûres, sans donnée
 * personnelle ni contenu de compte. Aucune règle métier n'est
 * réinventée.
 *
 * Seuls les codes de la {@link KNOWN_USER_CODES liste blanche} sont
 * considérés comme connus : leur message serveur (déjà contrôlé) est
 * conservé. Tout autre code — code futur ou inattendu, y compris un
 * `USER_*` non listé — ainsi que tout `5xx` retombe sur un message
 * générique, avec `code` et `field` à `null` (le message arbitraire de
 * la réponse n'est jamais affiché).
 *
 * Codes réellement produits par le back-end :
 * `USER_NOT_FOUND` (404), `USER_INVALID_STATE` (409),
 * `USER_ROLE_ALREADY_ASSIGNED` (409), `USER_ROLE_NOT_ASSIGNED` (409),
 * `USER_LAST_ACTIVE_ROLE` (409), `USER_SELF_ACTION_FORBIDDEN` (409),
 * `USER_SUPER_ADMIN_PROTECTED` (403), `USER_OPERATION_FORBIDDEN` (403),
 * `USER_ROLE_UNKNOWN` (400), `USER_INVALID_SORT` (400),
 * `USER_INVALID_FILTER` (400).
 */
export interface AdministrationErrorView {
  status: number;
  /** Code métier (`USER_*`) quand il est connu, sinon `null`. */
  code: string | null;
  /** Message présentable à l'utilisateur (jamais une trace serveur). */
  message: string;
  /**
   * Champ de formulaire à marquer en erreur, ou `null` pour un message
   * global. Seul `USER_ROLE_UNKNOWN` cible un champ (`role`).
   */
  field: 'role' | null;
}

/**
 * Liste blanche explicite des codes métier réellement émis par le
 * module `identity`. Un `startsWith('USER_')` traiterait à tort un code
 * futur ou inconnu comme connu ; on énumère donc les valeurs attendues.
 */
const KNOWN_USER_CODES: ReadonlySet<string> = new Set([
  'USER_NOT_FOUND',
  'USER_INVALID_STATE',
  'USER_ROLE_ALREADY_ASSIGNED',
  'USER_ROLE_NOT_ASSIGNED',
  'USER_LAST_ACTIVE_ROLE',
  'USER_SELF_ACTION_FORBIDDEN',
  'USER_SUPER_ADMIN_PROTECTED',
  'USER_OPERATION_FORBIDDEN',
  'USER_ROLE_UNKNOWN',
  'USER_INVALID_SORT',
  'USER_INVALID_FILTER',
]);

/** Codes `USER_*` rattachables à un champ de formulaire précis. */
const USER_FIELD_CODES: Record<string, 'role'> = {
  USER_ROLE_UNKNOWN: 'role',
};

export function toAdministrationError(error: unknown): AdministrationErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  // Un `5xx` reste masqué même s'il porte par hasard un code connu.
  const known = normalized.status < 500 && KNOWN_USER_CODES.has(normalized.code);
  return {
    status: normalized.status,
    code: known ? normalized.code : null,
    message: known ? normalized.message : SAFE_FALLBACK_MESSAGE,
    field: known ? (USER_FIELD_CODES[normalized.code] ?? null) : null,
  };
}
