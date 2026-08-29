import { HttpErrorResponse } from '@angular/common/http';

/**
 * Format d'erreur commun de l'API (docs/03-architecture.md §10.3 ;
 * `com.esic.connect.shared.web.ApiError`).
 */
export interface ApiError {
  timestamp: string;
  status: number;
  /** Code métier stable, à utiliser pour les décisions côté front. */
  code: string;
  message: string;
  path: string;
  correlationId: string | null;
  details: string[];
}

/** Erreur normalisée exploitable par l'interface. */
export interface NormalizedError {
  status: number;
  code: string;
  /** Message sûr, présentable à l'utilisateur (jamais une trace serveur). */
  message: string;
  correlationId: string | null;
  details: string[];
}

const SAFE_FALLBACK_MESSAGE =
  'Une erreur est survenue. Veuillez réessayer ; si le problème persiste, contactez le support.';

const NETWORK_MESSAGE =
  "Impossible de joindre le serveur. Vérifiez votre connexion, puis réessayez.";

function looksLikeApiError(body: unknown): body is ApiError {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as Record<string, unknown>)['code'] === 'string' &&
    typeof (body as Record<string, unknown>)['status'] === 'number'
  );
}

/**
 * Convertit une erreur HTTP en {@link NormalizedError}.
 *
 * - conserve le `code` métier renvoyé par le back-end quand il existe ;
 * - n'expose jamais de trace ni de message interne : pour un 5xx, le
 *   message du corps est ignoré au profit d'un texte générique ;
 * - distingue l'absence de réseau (statut 0) ;
 * - toute valeur qui n'est pas une {@link HttpErrorResponse} retombe sur
 *   un message générique.
 */
export function normalizeHttpError(error: unknown): NormalizedError {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      status: 0,
      code: 'UNEXPECTED_ERROR',
      message: SAFE_FALLBACK_MESSAGE,
      correlationId: null,
      details: [],
    };
  }

  if (error.status === 0) {
    return {
      status: 0,
      code: 'NETWORK_UNAVAILABLE',
      message: NETWORK_MESSAGE,
      correlationId: null,
      details: [],
    };
  }

  const body: unknown = error.error;

  if (looksLikeApiError(body)) {
    const serverProvidedMessage =
      typeof body.message === 'string' && body.message.trim().length > 0
        ? body.message.trim()
        : SAFE_FALLBACK_MESSAGE;

    return {
      status: body.status,
      code: body.code,
      // Un message applicatif contrôlé (4xx) peut être montré ; pour un
      // 5xx on retombe sur le texte générique même si le corps en fournit un.
      message: body.status >= 500 ? SAFE_FALLBACK_MESSAGE : serverProvidedMessage,
      correlationId: typeof body.correlationId === 'string' ? body.correlationId : null,
      details: Array.isArray(body.details) ? body.details.filter((d) => typeof d === 'string') : [],
    };
  }

  return {
    status: error.status,
    code: 'UNEXPECTED_ERROR',
    message: SAFE_FALLBACK_MESSAGE,
    correlationId: null,
    details: [],
  };
}
