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
} as const;
