/**
 * Configuration de production (valeur par défaut).
 *
 * `apiBaseUrl` est volontairement RELATIF : le front est servi derrière
 * le même hôte que l'API (reverse proxy Nginx, docs/03-architecture.md
 * §36). Aucune URL d'hôte n'est codée en dur ici.
 *
 * Si un déploiement expose l'API sur une origine distincte, renseigner
 * ici l'URL absolue ET activer une configuration CORS restrictive côté
 * Spring Boot (docs/08-securite-rgpd.md §8) — non requis en local.
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api',
  /**
   * Expiration glissante de session côté client (Lot A).
   *
   * Le jeton d'accès vit `JWT_ACCESS_TOKEN_TTL_SECONDS` secondes (900 par
   * défaut). Le cookie de renouvellement `HttpOnly` a une inactivité
   * glissante `JWT_REFRESH_TOKEN_IDLE_TTL` (PT30M) et un plafond absolu
   * `JWT_REFRESH_TOKEN_ABSOLUTE_TTL` (PT12H), tous deux côté back-end.
   *
   * Ces valeurs pilotent le maintien PROACTIF de la session : sur
   * activité significative (clic, frappe, navigation interne, soumission),
   * et seulement quand le jeton d'accès approche de son terme, le client
   * renouvelle. Sans activité, la session expire réellement. Le plafond
   * absolu reste appliqué par le back-end ; `absoluteMaxMs` n'en est que
   * le miroir d'affichage.
   *
   * Toutes les durées sont en millisecondes.
   */
  session: {
    /** Fréquence du contrôle d'échéance. */
    pollIntervalMs: 20_000,
    /** Une activité n'est enregistrée qu'une fois par fenêtre (anti-rafale). */
    activityThrottleMs: 60_000,
    /** L'utilisateur est « actif » si une activité date de moins de cela. */
    activityWindowMs: 5 * 60_000,
    /** Renouvellement proactif quand il reste moins que cela sur le jeton. */
    renewLeadMs: 5 * 60_000,
    /** Deux renouvellements proactifs ne peuvent pas être plus rapprochés. */
    minRenewIntervalMs: 4 * 60_000,
    /** Avertissement affiché quand il reste moins que cela, sans activité. */
    warningLeadMs: 2 * 60_000,
    /** Miroir d'affichage du plafond absolu back-end (PT12H). */
    absoluteMaxMs: 12 * 60 * 60_000,
  },
} as const;
