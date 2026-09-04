package com.esic.connect.claim;

/**
 * Destinataire fonctionnel d'une réclamation (docs/02 §20.1 et §20.3).
 *
 * <p>Ce n'est pas une personne mais un <strong>guichet</strong> : le
 * cahier prévoit qu'une réclamation soit adressée au formateur, au
 * responsable pédagogique ou à l'administration scolaire, et qu'elle
 * puisse être transférée de l'un à l'autre. Désigner une personne
 * nommément rendrait le transfert impossible dès qu'elle est absente.
 */
public enum ClaimAudience {
    TEACHER,
    PEDAGOGICAL_MANAGER,
    SCHOOL_ADMINISTRATION;

    /** Rôle attendu pour intervenir sur ce guichet. */
    public String roleCode() {
        return name();
    }
}
