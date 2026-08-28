package com.esic.connect.identity.internal;

/**
 * Réponse de l'endpoint public de validation d'un jeton. Volontairement
 * réduite à un booléen : aucune donnée personnelle (email, nom, rôle) ni
 * motif d'invalidité n'est exposé. Une invitation inconnue, expirée,
 * révoquée ou déjà acceptée produit toutes la même valeur {@code false}.
 */
record InvitationValidationResponse(boolean valid) {
}
