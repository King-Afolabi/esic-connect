package com.esic.connect.alternation.internal;

/**
 * Statut d'une affectation de rythme à une classe. Une affectation n'est
 * jamais supprimée : clôturer une affectation la fait passer en
 * {@link #CLOSED} et renseigne {@code valid_until} (docs/04 §14.2). La
 * clôture libère le créneau de l'affectation « ouverte » courante.
 */
enum ClassPatternStatus {
    ACTIVE,
    CLOSED
}
