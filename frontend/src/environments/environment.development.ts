/**
 * Configuration de développement local.
 *
 * `apiBaseUrl` reste relatif (`/api`) : `ng serve` proxifie `/api` vers
 * `http://localhost:8080` via `proxy.conf.json`, ce qui évite toute
 * requête cross-origin et donc toute dépendance à une configuration CORS
 * du back-end en local (docs/03-architecture.md §36).
 */
export const environment = {
  production: false,
  apiBaseUrl: '/api',
  /** Voir `environment.ts`. Vide = origine du navigateur (`localhost:4200`). */
  publicBaseUrl: '',
  /**
   * Voir `environment.ts` pour le rôle de chaque durée. Valeurs identiques
   * à la production : le comportement d'expiration glissante se teste donc
   * à l'identique en local. Pour l'éprouver rapidement, réduire
   * `JWT_ACCESS_TOKEN_TTL_SECONDS` côté back-end et `warningLeadMs` ici.
   */
  session: {
    pollIntervalMs: 20_000,
    activityThrottleMs: 60_000,
    activityWindowMs: 5 * 60_000,
    renewLeadMs: 5 * 60_000,
    minRenewIntervalMs: 4 * 60_000,
    warningLeadMs: 2 * 60_000,
    absoluteMaxMs: 12 * 60 * 60_000,
  },
} as const;
