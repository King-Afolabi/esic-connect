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
 *   <li>{@link #EXPIRED} : simulation purgée après expiration.</li>
 * </ul>
 */
public enum StudentImportChangeAction {
    SIMULATED,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}
