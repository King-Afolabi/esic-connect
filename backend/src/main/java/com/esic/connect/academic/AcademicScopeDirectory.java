package com.esic.connect.academic;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port public minimal du module {@code academic} pour le contrôle du
 * périmètre pédagogique.
 *
 * <p>Permet à un autre module (ici {@code alternation}) de savoir si
 * l'appelant courant a un accès <em>global</em> au référentiel académique
 * ou, sinon, si une classe donnée appartient à son périmètre effectif —
 * <strong>sans</strong> importer {@code AcademicScopeGuard}, qui reste
 * interne, ni dupliquer la logique de sécurité. La décision est prise
 * côté {@code academic} à partir du contexte Spring Security, jamais d'un
 * paramètre fourni par le client.
 */
public interface AcademicScopeDirectory {

    /**
     * @return {@code true} si l'appelant courant porte une autorité
     *         d'accès global ({@code ROLE_ADMIN} / {@code ROLE_SUPER_ADMIN}
     *         / {@code ROLE_SCHOOL_ADMINISTRATION}) — aucun filtrage de
     *         périmètre à appliquer
     */
    boolean hasGlobalScope();

    /**
     * @param classGroupPublicId identifiant public d'une classe ; peut
     *                           être {@code null}
     * @return {@code true} si l'appelant a l'accès global, ou si la classe
     *         existe et relève d'une formation de son périmètre effectif
     *         au jour courant ; {@code false} sinon (classe inconnue
     *         comprise)
     */
    boolean isClassInScope(UUID classGroupPublicId);

    /**
     * Identifiants internes des classes visibles par l'appelant, pour
     * filtrer une liste.
     *
     * @return {@link Optional#empty()} si l'appelant a l'accès global
     *         (aucun filtre) ; sinon l'ensemble — éventuellement vide —
     *         des identifiants internes de classes visibles
     */
    Optional<Set<Long>> visibleClassGroupIds();
}
