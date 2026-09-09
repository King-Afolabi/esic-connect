/**
 * Abonnements iCalendar (EF-INT-001 ; AC-034). Miroir du DTO
 * `com.esic.connect.integration.internal.IntegrationResponses`.
 */

export interface CalendarSubscriptionSummary {
  publicId: string;
  label: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

/**
 * Abonnement fraîchement créé.
 *
 * `feedPath` est un chemin **relatif**, jeton compris, renvoyé une seule
 * fois : le jeton n'est pas conservé en clair et ne pourra pas être
 * réaffiché. Le client le préfixe de son origine — le serveur ne connaît
 * pas l'URL publique par laquelle on l'atteint.
 */
export interface CreatedCalendarSubscription {
  publicId: string;
  label: string | null;
  createdAt: string;
  feedPath: string;
}
