import { Role } from './role';

/**
 * Réponse de `POST /api/v1/auth/login`
 * (`com.esic.connect.identity.internal.LoginResponse`).
 */
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/**
 * Session authentifiée telle que connue du client.
 *
 * `subject` et `roles` proviennent des claims du JWT (`sub`, `roles`) et
 * ne servent QU'À l'affichage et au filtrage de la navigation. Ils ne
 * sont jamais traités comme une preuve d'autorisation : le back-end
 * revalide chaque appel (docs/07-securite-rgpd.md §7 ; consigne
 * « ne pas décoder un JWT et le traiter comme une autorisation »).
 *
 * `email` est l'adresse saisie au formulaire de connexion, conservée en
 * mémoire pour l'affichage « connecté en tant que ». Elle n'est pas
 * persistée.
 */
export interface Session {
  accessToken: string;
  /** `sub` du JWT = identifiant public du compte (UUID), jamais l'id SQL. */
  subject: string | null;
  roles: Role[];
  email: string;
  /** Expiration absolue de l'access token (ms epoch), pour l'affichage. */
  expiresAt: number;
}
