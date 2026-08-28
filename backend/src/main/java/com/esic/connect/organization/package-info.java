/**
 * Module « organization ».
 *
 * <p>Élargit et remplace le module {@code room} prévu initialement dans
 * l'architecture (docs/03-architecture.md §7.6) : il couvre l'ensemble du
 * référentiel organisationnel — hiérarchie site → bâtiment → salle
 * (docs/04-modele-donnees.md §9) — ainsi que les plages réseau autorisées
 * par site (cahier §17.9), réservées au {@code SUPER_ADMIN}, consultation
 * comprise.
 *
 * <p>Ne dépend du module {@code identity} qu'à travers le port public
 * {@link com.esic.connect.identity.CurrentUserResolver} (aucun accès à
 * {@code identity.internal}). Publie
 * {@link com.esic.connect.organization.OrganizationChangeEvent}, consommé
 * par le module {@code audit}. Les types d'implémentation résident dans
 * {@code organization.internal} et ne sont pas visibles des autres
 * modules.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organization")
package com.esic.connect.organization;
