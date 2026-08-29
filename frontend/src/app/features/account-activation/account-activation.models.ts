/**
 * Contrat exact des endpoints publics d'invitation / activation du
 * back-end (`AccountInvitationController`).
 */

/** Réponse de `GET /api/v1/account-invitations/validate`. */
export interface InvitationValidation {
  valid: boolean;
}

/** Corps de `POST /api/v1/account-invitations/activate`. */
export interface ActivateAccountRequest {
  token: string;
  /** 12 à 200 caractères (`@Size(min = 12, max = 200)` côté back-end). */
  password: string;
}

/** Bornes de longueur du mot de passe, alignées sur `ActivateAccountRequest.java`. */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 200;

/**
 * Vue courante du parcours d'activation.
 *
 * Le back-end renvoie un **unique** code `INVITATION_INVALID` pour un
 * lien inconnu, expiré, révoqué ou déjà utilisé : ces cas partagent donc
 * l'état terminal {@link InvalidLinkView} — aucune distinction n'est
 * inventée côté client.
 */
export type ActivationView =
  | { kind: 'validating' }
  | { kind: 'form' }
  | InvalidLinkView
  | { kind: 'validation-error' }
  | { kind: 'success' };

/** Jeton absent / illisible, `valid: false`, ou `INVITATION_INVALID` à l'activation. */
export interface InvalidLinkView {
  kind: 'invalid-link';
}
