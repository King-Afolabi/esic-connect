import { SAFE_FALLBACK_MESSAGE, normalizeHttpError } from '../../core/models/api-error';

/**
 * Traduction d'une erreur HTTP du module `notification` (G1-D). Les
 * messages `NOTIF_*` du back-end sont des phrases françaises sûres, sans
 * donnée sensible. Un code inconnu (ou un `5xx`) retombe sur le message
 * générique.
 */
const KNOWN_CODES = new Set<string>([
  'NOTIF_NOT_FOUND',
  'NOTIF_INVALID_STATUS',
  'NOTIF_UNAUTHENTICATED',
]);

export interface NotificationErrorView {
  notFound: boolean;
  message: string;
}

export function toNotificationError(error: unknown): NotificationErrorView {
  const normalized = normalizeHttpError(error);
  const known = typeof normalized.code === 'string' && KNOWN_CODES.has(normalized.code);
  return {
    notFound: normalized.status === 404,
    message: known ? normalized.message : SAFE_FALLBACK_MESSAGE,
  };
}
