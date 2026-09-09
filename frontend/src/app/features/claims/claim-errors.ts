import {
  NormalizedError,
  SAFE_FALLBACK_MESSAGE,
  normalizeHttpError,
} from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `claim` en éléments
 * d'affichage. Même principe que {@code session-errors} : liste blanche
 * explicite des codes attendus ; tout code inconnu ou tout `5xx` retombe
 * sur le message générique, le message brut du serveur n'étant jamais
 * affiché dans ce cas.
 */
export interface ClaimErrorView {
  /** `404` — réclamation absente, ou hors du périmètre de l'appelant. */
  notFound: boolean;
  /** `403` — opération refusée sur une réclamation pourtant visible. */
  forbidden: boolean;
  code: string | null;
  message: string;
}

const KNOWN_CODES = new Set<string>([
  'CLAIM_NOT_FOUND',
  'CLAIM_FORBIDDEN',
  'CLAIM_SESSION_NOT_FOUND',
  'CLAIM_INVALID_STATE',
  'CLAIM_NO_SCOPE_FOR_AUDIENCE',
  'CLAIM_INVALID_SORT',
  'CLAIM_INVALID_SUBMISSION',
  'VALIDATION_ERROR',
]);

export function toClaimError(error: unknown): ClaimErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  const known = typeof normalized.code === 'string' && KNOWN_CODES.has(normalized.code);
  return {
    notFound: normalized.status === 404,
    forbidden: normalized.status === 403,
    code: normalized.code ?? null,
    message: known && normalized.message ? normalized.message : SAFE_FALLBACK_MESSAGE,
  };
}
