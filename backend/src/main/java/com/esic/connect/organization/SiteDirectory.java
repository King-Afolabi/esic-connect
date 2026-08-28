package com.esic.connect.organization;

import java.util.Optional;
import java.util.UUID;

/**
 * Port public minimal du module {@code organization}.
 *
 * <p>Permet à un autre module (ici {@code academic}, pour rattacher une
 * classe à un site) de résoudre une référence technique de site sans
 * dépendre des classes internes d'{@code organization}. Ne renvoie ni
 * l'entité {@code Site}, ni un repository, ni aucun type de
 * {@code organization.internal} : uniquement le {@link SiteRef} ci-dessous,
 * composé de types standard.
 */
public interface SiteDirectory {

    /**
     * @param sitePublicId identifiant public du site (forme UUID) ; peut
     *                     être {@code null}
     * @return la référence du site si un site correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<SiteRef> findByPublicId(UUID sitePublicId);

    /**
     * @param siteInternalId identifiant interne du site
     * @return la référence du site si un site correspond,
     *         {@link Optional#empty()} sinon
     */
    Optional<SiteRef> findByInternalId(long siteInternalId);

    /**
     * Référence technique d'un site, strictement suffisante pour qu'un
     * autre module stocke la clé étrangère {@code site_id}, réaffiche
     * l'identifiant public et refuse un rattachement sous un site archivé.
     *
     * @param internalId clé primaire SQL du site (valeur de {@code site_id})
     * @param publicId   identifiant public du site
     * @param archived   {@code true} si le site est archivé
     */
    record SiteRef(long internalId, UUID publicId, boolean archived) {
    }
}
