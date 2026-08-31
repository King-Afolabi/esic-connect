import { NormalizedError, normalizeHttpError } from '../../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP de l'import CSV des apprenants en éléments
 * d'affichage sûrs. Les messages `IMP_*` du back-end
 * (`StudentImportExceptionHandler`) sont déjà des phrases françaises sans
 * donnée sensible.
 *
 * On énumère les codes connus par une **liste blanche explicite** (jamais
 * un `startsWith('IMP_')`, qui laisserait passer un code futur ou
 * inattendu). Tout code hors liste **et** tout `5xx` retombent sur le
 * message générique, sans jamais afficher le corps arbitraire de la
 * réponse.
 */
export interface StudentImportErrorView {
  /** `403` — hors périmètre / accès refusé (`IMP_JOB_FORBIDDEN`, `IMP_CONFIRM_FORBIDDEN`, `IMP_SCOPE_FORBIDDEN`). */
  forbidden: boolean;
  /** `404 IMP_JOB_NOT_FOUND` — import absent. */
  notFound: boolean;
  /** La simulation n'est plus à jour : recharger les lignes avant de reconfirmer. */
  stale: boolean;
  /** La simulation a expiré : relancer un import. */
  expired: boolean;
  /** Code métier (`IMP_*`) connu, sinon `null`. */
  code: string | null;
  /** Message présentable (message serveur contrôlé + précisions non sensibles). */
  message: string;
  /** Précisions non sensibles (`details[]`, ex. colonnes obligatoires manquantes). */
  details: string[];
}

/** Codes `IMP_*` explicitement pris en charge (liste blanche). */
export const KNOWN_IMP_CODES: readonly string[] = [
  'IMP_UNSUPPORTED_MEDIA_TYPE',
  'IMP_FILE_TOO_LARGE',
  'IMP_ENCODING_INVALID',
  'IMP_MISSING_COLUMN',
  'IMP_TOO_MANY_ROWS',
  'IMP_NO_DATA_ROWS',
  'IMP_HEADER_UNREADABLE',
  'IMP_JOB_NOT_FOUND',
  'IMP_JOB_FORBIDDEN',
  'IMP_INVALID_SORT',
  'IMP_INVALID_FILTER',
  'IMP_SCOPE_FORBIDDEN',
  'IMP_NOT_CONFIRMABLE',
  'IMP_STALE_SIMULATION',
  'IMP_SIMULATION_EXPIRED',
  'IMP_JOB_CANCELLED',
  'IMP_CONFIRM_FORBIDDEN',
  'IMP_JOB_NOT_CANCELLABLE',
  'IMP_STUDENT_NUMBER_ALLOC_FAILED',
  'IMP_STUDENT_NUMBER_EXHAUSTED',
];

export function toStudentImportError(error: unknown): StudentImportErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  const known =
    typeof normalized.code === 'string' &&
    normalized.status < 500 &&
    KNOWN_IMP_CODES.includes(normalized.code);
  const code = known ? normalized.code : null;
  const message =
    known && normalized.details.length > 0
      ? `${normalized.message} ${normalized.details.join(' ')}`.trim()
      : normalized.message;
  return {
    forbidden: normalized.status === 403,
    notFound: code === 'IMP_JOB_NOT_FOUND' || (normalized.status === 404 && known),
    stale: code === 'IMP_STALE_SIMULATION',
    expired: code === 'IMP_SIMULATION_EXPIRED',
    code,
    message,
    details: known ? normalized.details : [],
  };
}
