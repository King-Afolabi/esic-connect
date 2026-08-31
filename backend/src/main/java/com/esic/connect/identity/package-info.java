/**
 * Module « identité et accès » (docs/03-architecture.md §7.1).
 * Gère les comptes utilisateurs, les rôles et leurs affectations.
 * Les types d'implémentation résident dans {@code identity.internal} et ne
 * sont pas accessibles depuis les autres modules.
 *
 * <p>Ports publics exposés (records / interfaces sans entité JPA) :
 * {@link com.esic.connect.identity.CurrentUserResolver} (identifiant
 * interne de l'appelant courant),
 * {@link com.esic.connect.identity.UserDirectory} (référence d'un compte
 * et ses rôles actifs) et
 * {@link com.esic.connect.identity.TeacherDirectory} (comptes éligibles
 * comme formateur d'une séance, consommé par {@code coursesession} sans
 * élargir {@code GET /api/v1/users}) et
 * {@link com.esic.connect.identity.StudentAccountProvisioner} (consommé
 * par {@code studentimport} : détection de doublon en simulation, puis
 * création de compte {@code PENDING_ACTIVATION} + rôle {@code STUDENT} +
 * invitation <em>dans la transaction unique</em> de la confirmation
 * d'import — propagation {@code REQUIRED}, publie
 * {@code AccountInvitationIssuedEvent} mais jamais
 * {@code AccountLifecycleEvent}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.esic.connect.identity;
