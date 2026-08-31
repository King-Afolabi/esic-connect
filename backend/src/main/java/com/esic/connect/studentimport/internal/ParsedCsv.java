package com.esic.connect.studentimport.internal;

import java.util.List;
import java.util.Optional;

/**
 * Résultat structuré de l'analyse d'un fichier CSV d'import (rapport §5).
 * Purement descriptif : la construction des anomalies persistées et les
 * contrôles métier appartiennent au checkpoint de validation.
 *
 * @param separator             séparateur retenu ({@code ','} ou {@code ';'})
 * @param header                colonnes de l'en-tête, dans l'ordre du fichier
 * @param missingMandatoryNames noms canoniques des colonnes obligatoires absentes
 * @param ignoredColumnNames    en-têtes présents mais volontairement ignorés (§12.A)
 * @param unknownColumnNames    en-têtes présents mais non reconnus
 * @param rows                  lignes de données (lignes vides ignorées, non comptées)
 * @param tooManyRows           {@code true} si le fichier dépasse la limite de lignes
 * @param noDataRows            {@code true} si aucune ligne de données exploitable
 */
record ParsedCsv(
        char separator,
        List<HeaderColumn> header,
        List<String> missingMandatoryNames,
        List<String> ignoredColumnNames,
        List<String> unknownColumnNames,
        List<DataRow> rows,
        boolean tooManyRows,
        boolean noDataRows) {

    /** Index (0-based) de la colonne reconnue dans une ligne, si présente dans l'en-tête. */
    Optional<Integer> indexOf(RecognizedColumn column) {
        return header.stream()
                .filter(h -> h.recognized().map(r -> r == column).orElse(false))
                .map(HeaderColumn::index)
                .findFirst();
    }

    boolean hasBlockingStructure() {
        return tooManyRows || noDataRows || !missingMandatoryNames.isEmpty();
    }

    enum HeaderKind { RECOGNIZED, IGNORED, UNKNOWN }

    /**
     * @param rawName    en-tête tel qu'écrit dans le fichier (rogné)
     * @param index      position 0-based dans chaque ligne
     * @param recognized colonne métier correspondante, si reconnue
     * @param kind       classification de l'en-tête
     */
    record HeaderColumn(String rawName, int index, Optional<RecognizedColumn> recognized, HeaderKind kind) {
    }

    /**
     * @param rowNumber           n° de la ligne dans le fichier (en-tête = 1)
     * @param cells               cellules brutes (avant normalisation)
     * @param columnCountMismatch {@code true} si le nombre de cellules diffère de l'en-tête
     */
    record DataRow(int rowNumber, List<String> cells, boolean columnCountMismatch) {

        String cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index) : null;
        }
    }
}
