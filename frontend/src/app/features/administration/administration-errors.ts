import { NormalizedError, normalizeHttpError } from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `identity`
 * (`UserManagementExceptionHandler`) en éléments d'affichage.
 *
 * On s'appuie sur {@link normalizeHttpError} : les messages `USER_*` du
 * back-end sont déjà des phrases françaises sûres, sans donnée
 * personnelle ni contenu de compte. Aucune règle métier n'est
 * réinventée ; un code inconnu ou un `5xx` retombe sur le message
 * générique de `normalizeHttpError`.
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

/** Codes `USER_*` rattachables à un champ de formulaire précis. */
const USER_FIELD_CODES: Record<string, 'role'> = {
  USER_ROLE_UNKNOWN: 'role',
};

export function toAdministrationError(error: unknown): AdministrationErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  const known = normalized.code.startsWith('USER_');
  return {
    status: normalized.status,
    code: known ? normalized.code : null,
    message: normalized.message,
    field: known ? (USER_FIELD_CODES[normalized.code] ?? null) : null,
  };
}
