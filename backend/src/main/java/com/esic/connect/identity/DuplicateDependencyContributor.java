package com.esic.connect.identity;

import java.util.Map;

/**
 * Point d'extension <strong>en lecture seule</strong> par lequel un autre
 * module fait connaître, à {@code identity}, le volume de données qu'il
 * détient pour un compte donné (ANO-USER-001 — comparaison de doublons).
 *
 * <p>{@code identity} ne peut pas interroger {@code enrollment},
 * {@code attendance}, {@code claim} ou {@code notification} : ces modules
 * dépendent déjà de {@code identity}, l'inverse créerait un cycle. Le
 * sens de la dépendance est donc renversé : chaque module qui possède des
 * lignes rattachées à un compte publie ici une implémentation confinée à
 * son propre {@code internal}, et {@code identity} agrège les décomptes
 * qu'on lui remonte sans rien savoir de leur origine.
 *
 * <p><strong>Contrat impératif.</strong> Une implémentation ne fait que
 * <em>compter</em> : aucune écriture, aucun effet de bord, aucune
 * modification de {@code updatedAt}, aucune trace d'audit, aucune
 * notification, aucun message d'outbox. Elle est invoquée exactement deux
 * fois par comparaison (une par compte) : le coût SQL doit être borné et
 * indépendant du volume d'historique (NFR-PERF-08).
 *
 * <p>Les clés sont des identifiants machine stables et partagés — par
 * exemple {@code "enrollments"}, {@code "attendanceRecords"},
 * {@code "justifications"}, {@code "claims"}, {@code "notifications"}.
 * Une clé absente de la {@code Map} vaut zéro. {@code identity} additionne
 * les valeurs de même clé remontées par plusieurs contributeurs.
 */
public interface DuplicateDependencyContributor {

    /**
     * @param userInternalId identifiant interne du compte (clé primaire
     *                       SQL, telle que stockée en clé étrangère par le
     *                       module appelant)
     * @return décompte, par catégorie, des lignes que ce module rattache
     *         au compte ; jamais {@code null}, éventuellement vide
     */
    Map<String, Long> countsFor(long userInternalId);

    /**
     * Attributs descriptifs — non numériques — que le module rattache au
     * compte et qui aident la revue humaine. Facultatif : par défaut,
     * aucun (le numéro étudiant, par exemple, est désormais porté
     * directement par {@code user_account} — refonte 2026-09 — et lu par
     * {@code identity} lui-même, sans passer par ce point d'extension).
     * Soumis au
     * même contrat de lecture seule que {@link #countsFor(long)} et à la
     * même règle de minimisation — jamais de secret, jamais de contenu
     * sensible, seulement ce qu'un {@code ADMIN} voit déjà sur la fiche du
     * compte.
     *
     * @param userInternalId identifiant interne du compte
     * @return attributs par clé stable ; jamais {@code null}, vide par défaut
     */
    default Map<String, String> attributesFor(long userInternalId) {
        return Map.of();
    }
}
