package com.esic.connect.planning.internal;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lecture d'un planning au format Excel `.xlsx` (EF-PLAN-011).
 *
 * <p>Produit exactement la même structure qu'un CSV —
 * {@link ParsedPlanningCsv} — de sorte que la validation métier, la
 * détection de conflits et la publication restent inchangées. Dupliquer
 * ces règles par format garantirait qu'elles divergent.
 *
 * <p><strong>Une seule feuille est lue</strong>, la première non masquée.
 * Contrairement à l'import d'apprenants, un planning porte sur une classe
 * unique, désignée par l'appelant : concaténer plusieurs feuilles
 * mélangerait des plannings de classes différentes sous un seul travail,
 * et les conflits calculés seraient faux. Les feuilles suivantes sont
 * <em>signalées</em>, jamais lues en silence.
 *
 * <p><strong>Conversion des cellules.</strong> Une date reste une date
 * ISO et une heure un {@code HH:mm} : sans cela, Excel rendrait « 45908 »
 * et « 0.375 », que la validation refuserait pour une raison
 * incompréhensible.
 */
final class PlanningWorkbookParser {

    /** Le séparateur n'a pas de sens pour un classeur ; valeur neutre conservée pour la traçabilité. */
    private static final char NO_SEPARATOR = ';';

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.FRANCE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private PlanningWorkbookParser() {
    }

    /** Vrai si le nom de fichier annonce un classeur `.xlsx`. */
    static boolean looksLikeWorkbook(String fileName) {
        return PlanningCsvValues.sanitizeFileName(fileName)
                .toLowerCase(Locale.ROOT)
                .endsWith(".xlsx");
    }

    /**
     * @return la structure analysée et les feuilles ignorées
     */
    static ParsedWorkbook parse(byte[] content, int maxDataRows) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = null;
            List<String> ignoredSheets = new ArrayList<>();
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) {
                    continue;
                }
                if (sheet == null) {
                    sheet = workbook.getSheetAt(index);
                } else {
                    ignoredSheets.add(workbook.getSheetName(index));
                }
            }
            if (sheet == null) {
                return new ParsedWorkbook(emptyResult(), List.of());
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return new ParsedWorkbook(emptyResult(), ignoredSheets);
            }

            Map<PlanningColumn, Integer> index = new EnumMap<>(PlanningColumn.class);
            List<String> unknown = new ArrayList<>();
            int headerSize = headerRow.getLastCellNum();
            for (int cellIndex = 0; cellIndex < headerSize; cellIndex++) {
                String raw = text(headerRow.getCell(cellIndex));
                String header = raw == null ? "" : raw.strip();
                Optional<PlanningColumn> recognized = PlanningColumn.forHeader(header);
                if (recognized.isPresent()) {
                    index.putIfAbsent(recognized.get(), cellIndex);
                } else if (!header.isEmpty()) {
                    unknown.add(header);
                }
            }

            List<String> missing = new ArrayList<>();
            for (PlanningColumn column : PlanningColumn.values()) {
                if (column.mandatory() && !index.containsKey(column)) {
                    missing.add(column.header());
                }
            }

            List<ParsedPlanningCsv.DataRow> rows = new ArrayList<>();
            boolean tooMany = false;
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> cells = new ArrayList<>(headerSize);
                for (int cellIndex = 0; cellIndex < headerSize; cellIndex++) {
                    cells.add(row == null ? null : text(row.getCell(cellIndex)));
                }
                if (cells.stream().allMatch(value -> value == null || value.isBlank())) {
                    continue;
                }
                if (rows.size() >= maxDataRows) {
                    tooMany = true;
                    break;
                }
                // Numéro affiché à l'utilisateur : celui qu'il voit dans Excel.
                rows.add(new ParsedPlanningCsv.DataRow(rowIndex + 1, cells, false));
            }

            return new ParsedWorkbook(new ParsedPlanningCsv(NO_SEPARATOR, index, missing, unknown,
                    List.copyOf(rows), tooMany, rows.isEmpty() && !tooMany), ignoredSheets);
        } catch (IOException | RuntimeException unreadable) {
            throw new PlanningException(PlanningException.Kind.FILE_UNREADABLE);
        }
    }

    private static ParsedPlanningCsv emptyResult() {
        List<String> mandatory = new ArrayList<>();
        for (PlanningColumn column : PlanningColumn.values()) {
            if (column.mandatory()) {
                mandatory.add(column.header());
            }
        }
        return new ParsedPlanningCsv(NO_SEPARATOR, ParsedPlanningCsv.emptyIndex(), mandatory,
                List.of(), List.of(), false, true);
    }

    /**
     * Conversion prévisible d'une cellule.
     *
     * <p>Une formule est lue par son résultat mis en cache : aucune n'est
     * évaluée, donc aucun contenu externe n'est exécuté.
     */
    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> numericText(cell);
            case BLANK, ERROR, _NONE, FORMULA -> null;
        };
    }

    private static String numericText(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            java.time.LocalDateTime value = cell.getLocalDateTimeCellValue();
            // Une cellule d'heure pure a une partie date au 31/12/1899 :
            // la rendre en date produirait une valeur absurde.
            boolean timeOnly = value.toLocalDate().getYear() <= 1900;
            return timeOnly
                    ? value.toLocalTime().format(TIME)
                    : value.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        double value = cell.getNumericCellValue();
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return BigDecimal.valueOf(value).toBigInteger().toString();
        }
        return FORMATTER.formatCellValue(cell);
    }

    /** @param ignoredSheets feuilles présentes mais non lues (une seule classe par planning) */
    record ParsedWorkbook(ParsedPlanningCsv parsed, List<String> ignoredSheets) {
    }
}
