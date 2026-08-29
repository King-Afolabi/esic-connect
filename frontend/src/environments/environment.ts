/**
 * Configuration de production (valeur par défaut).
 *
 * `apiBaseUrl` est volontairement RELATIF : le front est servi derrière
 * le même hôte que l'API (reverse proxy Nginx, docs/03-architecture.md
 * §36). Aucune URL d'hôte n'est codée en dur ici.
 *
 * Si un déploiement expose l'API sur une origine distincte, renseigner
 * ici l'URL absolue ET activer une configuration CORS restrictive côté
 * Spring Boot (docs/07-securite-rgpd.md §8) — non requis en local.
 */
export const environment = {
  production: true,
  apiBaseUrl: '/api',
} as const;
