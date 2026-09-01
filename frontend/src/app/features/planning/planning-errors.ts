import {
  NormalizedError,
  SAFE_FALLBACK_MESSAGE,
  normalizeHttpError,
} from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `planning`
 * (`PlanningExceptionHandler`) en éléments d'affichage. Les messages
 * `PLAN_*` du back-end sont déjà des phrases françaises sûres (aucune
 * donnée personnelle, aucun contenu de cellule). Seuls les codes de la
 * {@link KNOWN_CODES liste blanche} sont conservés ; tout autre code ou
 * `5xx` retombe sur un message générique.
 *
 * Codes réels : `PLAN_JOB_NOT_FOUND` (404), `PLAN_SCHEDULE_NOT_FOUND` (404),
 * `PLAN_VERSION_NOT_FOUND` (404), `PLAN_TARGET_UNRESOLVED` (400),
 * `PLAN_UNSUPPORTED_FILE` (413/415/400), `PLAN_FILE_UNREADABLE` (400),
 * `PLAN_MISSING_COLUMNS` (400), `PLAN_TOO_MANY_ROWS` (400),
 * `PLAN_SCOPE_FORBIDDEN` (403), `PLAN_BLOCKING_ISSUES` (409),
 * `PLAN_INVALID_JOB_STATE` (409), `PLAN_JOB_EXPIRED` (409),
 * `PLAN_PUBLICATION_FAILED` (409), `PLAN_INVALID_SORT` (400),
 * `PLAN_INVALID_FILTER` (400).
 */
export interface PlanningErrorView {
  status: number;
  code: string | null;
  message: string;
  notFound: boolean;
  forbidden: boolean;
}

const KNOWN_CODES: ReadonlySet<string> = new Set([
  'PLAN_JOB_NOT_FOUND',
  'PLAN_SCHEDULE_NOT_FOUND',
  'PLAN_VERSION_NOT_FOUND',
  'PLAN_TARGET_UNRESOLVED',
  'PLAN_UNSUPPORTED_FILE',
  'PLAN_FILE_UNREADABLE',
  'PLAN_MISSING_COLUMNS',
  'PLAN_TOO_MANY_ROWS',
  'PLAN_SCOPE_FORBIDDEN',
  'PLAN_BLOCKING_ISSUES',
  'PLAN_INVALID_JOB_STATE',
  'PLAN_JOB_EXPIRED',
  'PLAN_PUBLICATION_FAILED',
  'PLAN_INVALID_SORT',
  'PLAN_INVALID_FILTER',
]);

const NOT_FOUND_CODES: ReadonlySet<string> = new Set([
  'PLAN_JOB_NOT_FOUND',
  'PLAN_SCHEDULE_NOT_FOUND',
  'PLAN_VERSION_NOT_FOUND',
]);

export function toPlanningError(error: unknown): PlanningErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  const known = normalized.status < 500 && KNOWN_CODES.has(normalized.code);
  return {
    status: normalized.status,
    code: known ? normalized.code : null,
    message: known ? normalized.message : SAFE_FALLBACK_MESSAGE,
    notFound: known && normalized.status === 404 && NOT_FOUND_CODES.has(normalized.code),
    forbidden: normalized.status === 403,
  };
}
