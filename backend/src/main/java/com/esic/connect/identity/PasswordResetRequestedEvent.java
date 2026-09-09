package com.esic.connect.identity;

import java.time.Instant;

/**
 * Publié après validation de la transaction lorsqu'une demande de
 * réinitialisation aboutit à l'émission d'un jeton (EF-AUTH-005).
 *
 * <p>Porte le jeton <strong>brut</strong> : c'est la seule occasion où il
 * existe en mémoire, le stockage ne conservant que son empreinte. Il ne
 * doit donc jamais être journalisé ni recopié ailleurs que dans le
 * message envoyé à la personne concernée.
 *
 * <p>Aucun événement n'est publié lorsque l'adresse est inconnue ou que
 * le compte n'est pas activable : la réponse de l'API reste neutre dans
 * tous les cas (docs/02 §17.8).
 */
public record PasswordResetRequestedEvent(String email,
                                          String firstName,
                                          String rawToken,
                                          Instant expiresAt) {
}
