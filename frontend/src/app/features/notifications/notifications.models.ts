/**
 * Types du **centre de notifications** (bloc G1-D), strictement alignés
 * sur le module back-end `com.esic.connect.notification.internal` — aucun
 * champ, endpoint, statut HTTP ni code `NOTIF_*` n'est inventé.
 *
 * Routes (`/api/v1/me/notifications`, `@PreAuthorize("isAuthenticated()")`) :
 * - `GET  /me/notifications?status=&page=&size=` → `NotificationPage`
 * - `GET  /me/notifications/unread-count`        → `{ unread: number }`
 * - `POST /me/notifications/{publicId}/read`     → 204 (idempotent)
 * - `POST /me/notifications/read-all`            → 204
 *
 * Toutes les routes portent sur les notifications de l'appelant : le
 * destinataire est le sujet du JWT, jamais un paramètre. Un utilisateur
 * ne voit ni ne modifie jamais les notifications d'un autre (`404`, pas
 * `403`, sur un identifiant qui ne lui appartient pas).
 */

export const NOTIFICATION_STATUSES = ['UNREAD', 'READ', 'ARCHIVED'] as const;
export type NotificationStatus = (typeof NOTIFICATION_STATUSES)[number];

export const NOTIFICATION_TYPES = [
  'PLANNING_PUBLISHED',
  'SESSION_CANCELLED',
  'SESSION_SUBSTITUTION_ADDED',
  'SESSION_SUBSTITUTION_ENDED',
] as const;
export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  PLANNING_PUBLISHED: 'Planning',
  SESSION_CANCELLED: 'Séance annulée',
  SESSION_SUBSTITUTION_ADDED: 'Remplaçant affecté',
  SESSION_SUBSTITUTION_ENDED: 'Remplacement terminé',
};

export function notificationTypeLabel(value: string): string {
  return (NOTIFICATION_TYPE_LABELS as Record<string, string>)[value] ?? value;
}

/** `NotificationResponses.NotificationView` — jamais d'identifiant SQL. */
export interface NotificationView {
  publicId: string;
  type: NotificationType | string;
  title: string;
  body: string;
  resourceType: string;
  resourcePublicId: string;
  status: NotificationStatus;
  /** `Instant` ISO-8601. */
  createdAt: string;
  readAt: string | null;
}

/** `NotificationResponses.NotificationPage` (miroir de `PageResponse<T>`). */
export interface NotificationPage {
  content: NotificationView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** `NotificationResponses.UnreadCount`. */
export interface UnreadCount {
  unread: number;
}

/** `Instant` ISO-8601 → `jj/mm/aaaa hh:mm UTC`. `—` si absent / illisible. */
export function formatInstantUtc(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  const day = String(date.getUTCDate()).padStart(2, '0');
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  return `${day}/${month}/${date.getUTCFullYear()} ${hh}:${mm} UTC`;
}
