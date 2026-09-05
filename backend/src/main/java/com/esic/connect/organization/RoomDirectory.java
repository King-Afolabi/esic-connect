package com.esic.connect.organization;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public des salles et du contrôle de plage réseau (EF-ATT-008,
 * EF-ATT-010 ; docs/02 §16.6 et §16.7).
 *
 * <p>Consommé par {@code attendance} pour l'émargement par QR fixe. Le
 * module appelant ne connaît ni l'entité {@code Room}, ni le repository,
 * ni la représentation des plages réseau — seulement les deux questions
 * qu'il pose réellement : <em>à quelle salle correspond ce jeton ?</em> et
 * <em>cette requête vient-elle du réseau de l'établissement ?</em>
 */
public interface RoomDirectory {

    /**
     * Résout une salle par le jeton de son QR fixe.
     *
     * <p>Le QR identifie une <strong>ressource de salle</strong>, jamais
     * une séance (docs/02 §16.6) : c'est le serveur qui détermine ensuite
     * la séance applicable. Une salle archivée n'est pas renvoyée — une
     * affiche restée au mur ne doit pas rouvrir un émargement.
     *
     * @param staticQrReference jeton lu dans le QR ; peut être {@code null}
     */
    Optional<RoomRef> findActiveByStaticQrReference(String staticQrReference);

    /**
     * L'adresse est-elle dans une plage réseau active du site ?
     *
     * <p>L'adresse est utilisée <strong>pendant la décision uniquement</strong>
     * (docs/02 §16.7, RG-094) : elle n'est ni renvoyée, ni journalisée dans
     * l'audit métier, ni conservée par ce port.
     *
     * @param sitePublicId site de la salle
     * @param ipAddress    adresse d'origine de la requête ; {@code null} ou
     *                     illisible ⇒ {@code false} (refus par défaut)
     */
    boolean isWithinAuthorizedRange(UUID sitePublicId, String ipAddress);

    /**
     * Salles actives dont le code ou le nom contient {@code query}
     * (EF-USER-009). Les salles ne sont pas périmétrées : elles
     * appartiennent à l'établissement, pas à une formation.
     */
    java.util.List<RoomSearchRef> search(String query, int limit);

    /** Résultat de recherche de salle : jamais le jeton de QR fixe. */
    record RoomSearchRef(UUID publicId, String code, String name, String buildingName) {
    }

    /**
     * Référence d'une salle, strictement suffisante pour l'émargement.
     *
     * @param internalId    clé primaire SQL
     * @param publicId      identifiant public
     * @param code          code fonctionnel, celui que porte le planning
     * @param sitePublicId  site de rattachement, porteur des plages réseau
     */
    record RoomRef(long internalId, UUID publicId, String code, UUID sitePublicId) {
    }
}
