package com.esic.connect.identity.internal;

/**
 * Échec d'un renouvellement de session par cookie (EF-AUTH-014,
 * docs/02 §17.7).
 *
 * <p>Volontairement <strong>sans nuance</strong> : cookie absent, jeton
 * inconnu, empreinte incohérente, famille révoquée, plafond absolu
 * atteint, compte suspendu, Redis injoignable — tous produisent la même
 * chose, un {@code 401} nu. Distinguer ces cas donnerait un oracle sur
 * l'état d'une session à quiconque présente un cookie forgé.
 *
 * <p>Levée uniquement par {@link RefreshService#refresh}, elle n'est
 * traitée que par {@link RefreshController}. L'émission d'un cookie à la
 * connexion ({@link RefreshService#onAuthenticated}) n'échoue jamais de
 * cette manière : une panne Redis y prive simplement la réponse du
 * cookie, sans bloquer une authentification par ailleurs réussie.
 */
class RefreshTokenException extends RuntimeException {

    RefreshTokenException() {
        super("REFRESH_TOKEN_INVALID");
    }
}
