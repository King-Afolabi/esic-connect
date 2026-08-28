package com.esic.connect.identity;

import java.util.Optional;

/**
 * Port public minimal du module {@code identity}.
 *
 * <p>Résout l'identifiant interne (clé primaire SQL) d'un compte à partir
 * de son identifiant public — typiquement le sujet ({@code sub}) du JWT de
 * l'appelant courant. Permet aux autres modules de renseigner leurs
 * colonnes auteur ({@code created_by_id}, {@code updated_by_id}...) sans
 * dépendre des classes internes d'{@code identity}.
 *
 * <p>Ce contrat n'expose volontairement ni l'entité {@code UserAccount},
 * ni un repository, ni aucun autre type de {@code identity.internal} :
 * uniquement des types standard.
 */
public interface CurrentUserResolver {

    /**
     * @param publicSubject identifiant public du compte (forme UUID), tel
     *                      que porté par le sujet du JWT ; peut être
     *                      {@code null} ou vide
     * @return l'identifiant interne du compte si un compte correspond,
     *         {@link Optional#empty()} sinon (sujet absent, mal formé ou
     *         inconnu)
     */
    Optional<Long> resolveInternalId(String publicSubject);
}
