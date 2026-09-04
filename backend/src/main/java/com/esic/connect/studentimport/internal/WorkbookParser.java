package com.esic.connect.studentimport.internal;

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
import java.util.List;
import java.util.Locale;

/**
 * Lecture d'un classeur Excel `.xlsx` (EF-IMP-003, EF-IMP-004).
 *
 * <p>Produit exactement la même structure qu'un CSV — {@link ParsedCsv} —
 * pour que <strong>toute la chaîne de validation, de détection de
 * doublons et de confirmation reste inchangée</strong>. C'est le seul
 * choix raisonnable : dupliquer la validation métier par format
 * garantirait qu'elle diverge.
 *
 * <p><strong>Classeur multifeuille (EF-IMP-004).</strong> Toutes les
 * feuilles non masquées sont lues et concaténées, chaque feuille
 * apportant ses propres lignes. Le nom de la feuille est conservé sur
 * chaque ligne, afin qu'une anomalie puisse être située « fichier,
 * feuille, ligne, colonne » comme l'exige le cahier (docs/02 §10.7). Une
 * feuille dont l'en-tête ne correspond pas à celui de la première est
 * signalée, jamais lue de travers.
 *
 * <p><strong>Cellules.</strong> Les valeurs sont converties en texte de
 * façon prévisible : une date reste une date ISO, un nombre entier ne
 * devient pas {@code 1.0}, et une formule est lue par son résultat mis en
 * cache — jamais recalculée, ce qui éviterait toute évaluation de contenu
 * fourni par l'extérieur.
 */
final class WorkbookParser {

    /** Le séparateur n'a pas de sens pour un classeur ; valeur neutre conservée pour la traçabilité. */
    private static final char NO_SEPARATOR = ';';

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.FRANCE);

    private WorkbookParser() {
    }

    /**
     * @param content   octets du classeur, déjà validés par {@link WorkbookFileGuard}
     * @param maxDataRows plafond de lignes de données, toutes feuilles confondues
     */
    static ParsedWorkbook parse(byte[] content, int maxDataRows) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            List<SheetRows> sheets = new ArrayList<>();
            List<String> header = null;
            List<String> mismatchedSheets = new ArrayList<>();
            int totalRows = 0;
            boolean tooMany = false;

            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) {
                    // Une feuille masquée contient typiquement des listes de
                    // validation ou des calculs : la lire produirait du bruit.
                    continue;
                }
                Sheet sheet = workbook.getSheetAt(index);
                List<String> sheetHeader = readHeader(sheet);
                if (sheetHeader.isEmpty()) {
                    continue;
                }
                if (header == null) {
                    header = sheetHeader;
                } else if (!header.equals(sheetHeader)) {
                    // On ne devine pas : une feuille dont l'en-tête diffère est
                    // signalée et ignorée, jamais lue avec le mauvais mapping.
                    mismatchedSheets.add(sheet.getSheetName());
                    continue;
                }

                List<ParsedCsv.DataRow> rows = new ArrayList<>();
                int headerSize = sheetHeader.size();
                for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    List<String> cells = readCells(row, headerSize);
                    if (cells.stream().allMatch(value -> value == null || value.isBlank())) {
                        continue;
                    }
                    if (totalRows >= maxDataRows) {
                        tooMany = true;
                        break;
                    }
                    totalRows++;
                    // Numéro affiché à l'utilisateur : celui de la feuille,
                    // en base 1, en-tête compris — c'est ce qu'il voit dans Excel.
                    rows.add(new ParsedCsv.DataRow(rowIndex + 1, cells, false, sheet.getSheetName()));
                }
                if (!rows.isEmpty()) {
                    sheets.add(new SheetRows(sheet.getSheetName(), rows));
                }
                if (tooMany) {
                    break;
                }
            }

            if (header == null) {
                return ParsedWorkbook.headerless();
            }
            return new ParsedWorkbook(header, sheets, List.copyOf(mismatchedSheets), tooMany,
                    totalRows == 0);
        } catch (IOException | RuntimeException unreadable) {
            // Classeur corrompu, chiffré, ou format inattendu : refus net,
            // sans détail exploitable.
            throw new StudentImportException(StudentImportException.Kind.HEADER_UNREADABLE);
        }
    }

    private static List<String> readHeader(Sheet sheet) {
        Row row = sheet.getRow(sheet.getFirstRowNum());
        if (row == null) {
            return List.of();
        }
        List<String> header = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            header.add(text(row.getCell(cellIndex)));
        }
        while (!header.isEmpty() && (header.get(header.size() - 1) == null
                || header.get(header.size() - 1).isBlank())) {
            header.remove(header.size() - 1);
        }
        return header;
    }

    private static List<String> readCells(Row row, int headerSize) {
        List<String> cells = new ArrayList<>(headerSize);
        for (int cellIndex = 0; cellIndex < headerSize; cellIndex++) {
            cells.add(row == null ? null : text(row.getCell(cellIndex)));
        }
        return cells;
    }

    /**
     * Convertit une cellule en texte de façon <strong>prévisible</strong>.
     *
     * <p>Sans cela, un numéro étudiant saisi comme nombre reviendrait
     * « 12345.0 » et une date « 45678 ». Les deux échoueraient à la
     * validation pour une raison incompréhensible pour l'utilisateur.
     */
    private static String text(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                // Résultat mis en cache par Excel : aucune formule n'est
                // évaluée ici, donc aucun contenu externe n'est exécuté.
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
            return cell.getLocalDateTimeCellValue().toLocalDate()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        double value = cell.getNumericCellValue();
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            // Entier : `BigDecimal` évite la notation scientifique qu'un
            // `long` de grande taille produirait via `Double.toString`.
            return BigDecimal.valueOf(value).toBigInteger().toString();
        }
        return FORMATTER.formatCellValue(cell);
    }

    /**
     * Lignes d'une feuille, avec son nom — indispensable pour situer une
     * anomalie (docs/02 §10.7).
     */
    record SheetRows(String sheetName, List<ParsedCsv.DataRow> rows) {
    }

    /**
     * Classeur analysé.
     *
     * @param mismatchedSheets feuilles ignorées faute d'en-tête identique
     */
    record ParsedWorkbook(List<String> header,
                          List<SheetRows> sheets,
                          List<String> mismatchedSheets,
                          boolean tooManyRows,
                          boolean noDataRows) {

        static ParsedWorkbook headerless() {
            return new ParsedWorkbook(List.of(), List.of(), List.of(), false, true);
        }

        /**
         * Ramène le classeur à la structure commune {@link ParsedCsv}, pour
         * que la validation métier, la détection de doublons et la
         * confirmation restent <strong>strictement identiques</strong> quel
         * que soit le format d'entrée.
         */
        ParsedCsv toParsedCsv() {
            List<ParsedCsv.HeaderColumn> columns = CsvParser.buildHeader(header);
            List<ParsedCsv.DataRow> rows = sheets.stream()
                    .flatMap(sheet -> sheet.rows().stream())
                    .toList();
            List<String> ignored = columns.stream()
                    .filter(column -> column.kind() == ParsedCsv.HeaderKind.IGNORED)
                    .map(ParsedCsv.HeaderColumn::rawName)
                    .toList();
            List<String> unknown = columns.stream()
                    .filter(column -> column.kind() == ParsedCsv.HeaderKind.UNKNOWN)
                    .map(ParsedCsv.HeaderColumn::rawName)
                    .toList();
            List<String> missing = columns.isEmpty()
                    ? CsvParser.mandatoryNames()
                    : CsvParser.missingMandatory(columns);
            return new ParsedCsv(NO_SEPARATOR, columns, missing, ignored, unknown, rows,
                    tooManyRows, rows.isEmpty() && !tooManyRows);
        }

        /** Noms des feuilles effectivement lues, pour le compte rendu. */
        List<String> readSheetNames() {
            return sheets.stream().map(SheetRows::sheetName).toList();
        }
    }
}
