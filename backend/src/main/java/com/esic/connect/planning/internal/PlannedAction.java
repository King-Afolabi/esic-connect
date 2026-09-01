package com.esic.connect.planning.internal;

/**
 * Effet prévu d'une ligne d'import sur la prochaine version publiée
 * (DEC-G1-002, DEC-G1-004).
 */
enum PlannedAction {
    /** {@code slot_key} nouveau ⇒ création d'un créneau (et d'une séance). */
    ADDED,
    /** {@code slot_key} connu, au moins une propriété change ⇒ mise à jour. */
    MODIFIED,
    /** {@code slot_key} connu, propriétés identiques ⇒ rien à faire. */
    UNCHANGED,
    /** {@code slot_key} d'une version publiée absent du nouveau fichier ⇒ retrait. */
    REMOVED,
    /** Ligne en conflit (formateur / classe / salle) ⇒ non publiable. */
    CONFLICT
}
