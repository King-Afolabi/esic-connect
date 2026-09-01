package com.esic.connect.planning.internal;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Résultat structuré de l'analyse d'un CSV de planning. Purement
 * descriptif ; les contrôles métier appartiennent à
 * {@link PlanningSimulationService}.
 *
 * @param separator             séparateur retenu ({@code ','} ou {@code ';'})
 * @param columnIndex           position 0-based de chaque colonne reconnue
 * @param missingMandatoryNames en-têtes des colonnes obligatoires absentes
 * @param unknownColumnNames    en-têtes présents mais non reconnus
 * @param rows                  lignes de données (lignes vides ignorées)
 * @param tooManyRows           {@code true} si le fichier dépasse la limite
 * @param noDataRows            {@code true} si aucune ligne de données
 */
record ParsedPlanningCsv(
        char separator,
        Map<PlanningColumn, Integer> columnIndex,
        List<String> missingMandatoryNames,
        List<String> unknownColumnNames,
        List<DataRow> rows,
        boolean tooManyRows,
        boolean noDataRows) {

    boolean hasBlockingStructure() {
        return tooManyRows || noDataRows || !missingMandatoryNames.isEmpty();
    }

    String cell(DataRow row, PlanningColumn column) {
        Integer index = columnIndex.get(column);
        return index == null ? null : row.cell(index);
    }

    static Map<PlanningColumn, Integer> emptyIndex() {
        return new EnumMap<>(PlanningColumn.class);
    }

    /**
     * @param rowNumber           n° de ligne dans le fichier (en-tête = 1)
     * @param cells               cellules brutes
     * @param columnCountMismatch {@code true} si le nombre de cellules diffère de l'en-tête
     */
    record DataRow(int rowNumber, List<String> cells, boolean columnCountMismatch) {

        String cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index) : null;
        }
    }
}
