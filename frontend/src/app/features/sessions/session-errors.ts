import {
  NormalizedError,
  SAFE_FALLBACK_MESSAGE,
  normalizeHttpError,
} from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP des modules `coursesession` / `attendance`
 * en éléments d'affichage. S'appuie sur {@link normalizeHttpError} : les
 * messages `SESSION_*` / `ATT_*` du back-end sont déjà des phrases
 * françaises sûres, sans donnée sensible, sans jeton ni code court. Un
 * code inconnu (ou un `5xx`) retombe sur le message générique — le
 * message brut du serveur n'est jamais affiché dans ce cas.
 */
export interface SessionErrorView {
  /** `403` — accès refusé / hors périmètre. */
  forbidden: boolean;
  /** `404` — ressource absente. */
  notFound: boolean;
  /** `503` — backend de jetons (Redis) indisponible. */
  backendUnavailable: boolean;
  /** Code métier connu (`SESSION_*` / `ATT_*`) ou `null`. */
  code: string | null;
  /** Message présentable à l'utilisateur. */
  message: string;
}

/**
 * Liste blanche **explicite** des codes métier attendus. On ne fait pas
 * un simple `startsWith('SESSION_' | 'ATT_')` : un code futur ou
 * inattendu doit retomber sur la vue générique.
 */
const KNOWN_CODES = new Set<string>([
  'SESSION_NOT_FOUND',
  'SESSION_INVALID_STATE',
  'SESSION_INVALID_PERIOD',
  'SESSION_INVALID_TIME_ZONE',
  'SESSION_NO_CLASS',
  'SESSION_INVALID_SORT',
  'SESSION_INVALID_FILTER',
  'SESSION_TEACHER_NOT_FOUND',
  'SESSION_TEACHER_NOT_ELIGIBLE',
  'SESSION_CLASS_NOT_FOUND',
  'SESSION_CLASS_INACTIVE',
  'SESSION_SCOPE_FORBIDDEN',
  'SESSION_OPERATION_FORBIDDEN',
  // G1-C — annulation d'une séance et remplacements de formateur.
  'SESSION_CANCEL_REASON_REQUIRED',
  'SESSION_SUBSTITUTE_NOT_ELIGIBLE',
  'SESSION_SUBSTITUTE_IS_ORIGINAL',
  'SESSION_SUBSTITUTION_PERIOD_INVALID',
  'SESSION_SUBSTITUTION_OUTSIDE_SESSION',
  'SESSION_SUBSTITUTION_OVERLAP',
  'SESSION_SUBSTITUTION_NOT_FOUND',
  'SESSION_SUBSTITUTION_ALREADY_ENDED',
  'ATT_INVALID_SUBMISSION',
  'ATT_TOKEN_INVALID',
  'ATT_SESSION_CLOSED',
  'ATT_NOT_ENROLLED',
  'ATT_ENROLLMENT_AMBIGUOUS',
  'ATT_ALREADY_RECORDED',
  'ATT_TOKEN_BACKEND_UNAVAILABLE',
  'ATT_OPERATION_FORBIDDEN',
  // V10 — points de contrôle, présence manuelle, correction, justificatifs, rapports.
  'ATT_CHECKPOINT_NOT_FOUND',
  'ATT_CHECKPOINT_INVALID_STATE',
  'ATT_CHECKPOINT_INVALID_TYPE',
  'ATT_CHECKPOINT_ORDER_CONFLICT',
  'ATT_RECORD_NOT_FOUND',
  'ATT_RECORD_INVALID_STATE',
  'ATT_MANUAL_REASON_REQUIRED',
  'ATT_CORRECTION_REASON_REQUIRED',
  'ATT_JUSTIFICATION_NOT_FOUND',
  'ATT_JUSTIFICATION_INVALID_STATE',
  'ATT_JUSTIFICATION_DECISION_REASON_REQUIRED',
  'ATT_REPORT_INVALID_FILTER',
  'ATT_REPORT_INVALID_SORT',
  'VALIDATION_ERROR',
]);

/** Message client contrôlé pour une indisponibilité du backend de jetons. */
const BACKEND_UNAVAILABLE_MESSAGE =
  "Le service d'émargement est momentanément indisponible. Réessayez dans un instant.";

export function toSessionError(error: unknown): SessionErrorView {
  const normalized: NormalizedError = normalizeHttpError(error);
  const known = typeof normalized.code === 'string' && KNOWN_CODES.has(normalized.code);
  const backendUnavailable =
    normalized.status === 503 || normalized.code === 'ATT_TOKEN_BACKEND_UNAVAILABLE';
  let message: string;
  if (backendUnavailable) {
    // `normalizeHttpError` masque le message d'un 5xx/503 : on substitue
    // un message client contrôlé et parlant.
    message = BACKEND_UNAVAILABLE_MESSAGE;
  } else if (known) {
    // Message serveur d'un code métier reconnu — phrase française sûre.
    message = normalized.message;
  } else {
    // Code inconnu (ou 5xx déjà masqué) : jamais de contenu arbitraire.
    message = SAFE_FALLBACK_MESSAGE;
  }
  return {
    forbidden: normalized.status === 403,
    notFound: normalized.status === 404,
    backendUnavailable,
    code: known ? normalized.code : null,
    message,
  };
}
