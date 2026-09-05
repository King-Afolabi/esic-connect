package com.esic.connect.notification.internal;

/**
 * Regroupement fonctionnel servant les préférences de l'utilisateur
 * (EF-NOTIF-006 ; docs/02 §21.6 : « l'utilisateur règle ses canaux par
 * catégorie »).
 *
 * <p>Le réglage porte sur la catégorie et non sur le type : proposer une
 * case à cocher par type multiplierait les décisions sans en changer
 * aucune — personne ne veut être prévenu d'une annulation de séance mais
 * pas d'un remplacement.
 *
 * <p>{@link #SECURITY} existe pour classer, jamais pour régler : la
 * contrainte SQL de {@code notification_preference} refuse cette valeur
 * (§21.6, « les notifications critiques de sécurité restent
 * obligatoires »).
 */
enum NotificationCategory {
    PLANNING,
    SESSION,
    ATTENDANCE,
    JUSTIFICATION,
    CLAIM,
    SECURITY;

    /** @return {@code true} si l'utilisateur peut refuser un canal pour cette catégorie */
    boolean optional() {
        return this != SECURITY;
    }
}
