import { NormalizedError, normalizeHttpError } from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `alternation` en éléments
 * d'affichage. On s'appuie sur {@link normalizeHttpError} : les messages
 * `ALT_*` du back-end (`AlternationExceptionHandler`) sont déjà des
 * phrases françaises sûres, sans donnée sensible. Aucun comportement
 * métier n'est inventé ; un code inconnu retombe sur le message
 * générique.
 */
export interface AlternationErrorView {
  /** `403 ALT_FORBIDDEN` — hors périmètre / accès refusé. */
  forbidden: boolean;
  /** `404` — ressource absente. */
  notFound: boolean;
  /** Code métier (`ALT_*`) quand il est connu, sinon `null`. */
  code: string | null;
  /** Message présentable (message serveur + précisions non sensibles). */
  message: string;
  /** Précisions non sensibles (`details[]` d'une configuration invalide). */
  details: string[];
}

/** Codes `ALT_*` rattachables à un champ de formulaire précis. */
export const ALT_FIELD_CODES: Record<string, string> = {
  ALT_DUPLICATE_CODE: 'code',
  ALT_INVALID_PATTERN_TYPE: 'type',
  ALT_INVALID_EXCEPTION_TYPE: 'type',
  ALT_INVALID_TIME_ZONE: 'timeZoneId',
  ALT_INVALID_CONFIGURATION: 'configuration',
};

export function toAlternationError(error: unknown): AlternationErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  const known = typeof normalized.code === 'string' && normalized.code.startsWith('ALT_');
  const message =
    normalized.details.length > 0
      ? `${normalized.message} ${normalized.details.join(' ')}`.trim()
      : normalized.message;
  return {
    forbidden: normalized.status === 403,
    notFound: normalized.status === 404,
    code: known ? normalized.code : null,
    message,
    details: normalized.details,
  };
}

/** Nom de champ à marquer en erreur pour ce code, ou `null`. */
export function fieldForAlternationCode(code: string | null): string | null {
  return code ? (ALT_FIELD_CODES[code] ?? null) : null;
}
