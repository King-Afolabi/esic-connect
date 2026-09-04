package com.esic.connect.attendance.internal;

/**
 * Statut d'une entrée d'apprenant provisoire (EF-ATT-007 ; docs/02
 * §16.12).
 *
 * <ul>
 *   <li>{@link #UNREGISTERED_GUEST} : personne inconnue du système ;</li>
 *   <li>{@link #PENDING_REGISTRATION} : apprenant attendu dont
 *       l'inscription n'est pas encore enregistrée ;</li>
 *   <li>{@link #LINKED} : régularisée — rattachée à une inscription
 *       réelle par une décision humaine ;</li>
 *   <li>{@link #DISMISSED} : écartée avec motif (erreur de saisie,
 *       personne qui n'avait pas à être là).</li>
 * </ul>
 *
 * <p>Les deux premiers sont des <strong>signalements</strong> : ils
 * n'entrent dans aucun calcul d'assiduité. Le cahier l'exige — l'entrée
 * « ne crée pas d'inscription officielle ».
 */
enum SessionGuestStatus {
    UNREGISTERED_GUEST,
    PENDING_REGISTRATION,
    LINKED,
    DISMISSED;

    boolean isPending() {
        return this == UNREGISTERED_GUEST || this == PENDING_REGISTRATION;
    }
}
