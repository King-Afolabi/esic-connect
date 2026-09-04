package com.esic.connect.studentimport;

/**
 * Action du cycle de vie d'un import CSV d'apprenants tracée par l'audit
 * (cahier §30.1 : « import d'apprenants », « confirmation d'import » ;
 * rapport §10).
 *
 * <ul>
 *   <li>{@link #SIMULATED} : simulation persistée (analyse + bilan, aucune
 *       écriture métier) ;</li>
 *   <li>{@link #CONFIRMED} : confirmation appliquée (comptes / profils /
 *       inscriptions / invitations créés) ;</li>
 *   <li>{@link #CANCELLED} : simulation annulée avant confirmation ;</li>
 *   <li>{@link #EXPIRED} : simulation purgée après expiration ;</li>
 *   <li>{@link #ROW_CORRECTED} : ligne corrigée avant confirmation
 *       (EF-IMP-006, docs/02 §13.6 : « journalisation de la correction
 *       avec son auteur »).</li>
 * </ul>
 */
public enum StudentImportChangeAction {
    SIMULATED,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    ROW_CORRECTED
}
