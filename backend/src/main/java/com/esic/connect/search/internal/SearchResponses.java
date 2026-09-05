package com.esic.connect.search.internal;

import java.util.List;

/**
 * Résultats de la recherche globale (EF-USER-009).
 *
 * <p>Une forme unique quel que soit le type trouvé : le client affiche
 * une liste homogène et navigue par {@code type} + {@code publicId},
 * sans avoir à connaître la structure de chaque module.
 */
final class SearchResponses {

    private SearchResponses() {
    }

    /**
     * @param query      fragment effectivement recherché, après nettoyage
     * @param truncated  {@code true} si au moins une catégorie a atteint sa borne
     * @param results    résultats, groupés par type puis par pertinence d'ordre naturel
     * @param notes      mentions honnêtes (périmètre appliqué, critères écartés)
     */
    record GlobalSearch(String query, boolean truncated, List<Hit> results, List<String> notes) {
    }

    /**
     * @param type      {@code STUDENT} / {@code TEACHER} / {@code CLASS_GROUP} /
     *                  {@code PROGRAM} / {@code ROOM} / {@code SESSION}
     * @param publicId  identifiant public de la ressource trouvée
     * @param label     libellé principal
     * @param secondary complément (classe, bâtiment, date…) ; {@code null} possible
     */
    record Hit(String type, String publicId, String label, String secondary) {
    }
}
