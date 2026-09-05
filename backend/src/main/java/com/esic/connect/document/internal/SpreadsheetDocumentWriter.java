package com.esic.connect.document.internal;

import com.esic.connect.document.TabularDocument;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Production d'un classeur {@code .xlsx} (EF-REP-004).
 *
 * <p>Une seule feuille : le contenu d'un rapport est une table, pas un
 * classeur multifeuille — et une feuille unique reste lisible dans les
 * tableurs qui ne gèrent pas les onglets.
 *
 * <p><strong>Toutes les cellules sont écrites en texte</strong>, jamais
 * en nombre ni en formule. Un numéro étudiant écrit comme nombre perd
 * ses zéros de tête ; une cellule écrite comme formule est une injection
 * (AC-032) — {@link CellSafety} est donc appliqué ici exactement comme
 * au CSV.
 *
 * <p>La largeur des colonnes est calculée sur le contenu et bornée :
 * l'auto-dimensionnement de POI ({@code autoSizeColumn}) charge les
 * métriques de police et coûte un temps disproportionné sur un rapport
 * de plusieurs milliers de lignes.
 */
final class SpreadsheetDocumentWriter {

    private static final int MAX_COLUMN_CHARS = 60;
    private static final int CHAR_WIDTH_UNITS = 256;
    /** Au-delà, la mesure de largeur coûte plus qu'elle ne rapporte. */
    private static final int WIDTH_SAMPLE_ROWS = 200;

    private SpreadsheetDocumentWriter() {
    }

    static byte[] write(TabularDocument document) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName(document.title()));
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle labelStyle = boldStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);

            int r = 0;
            if (!document.title().isBlank()) {
                Row row = sheet.createRow(r++);
                write(row, 0, document.title(), titleStyle);
                if (document.period() != null && !document.period().isBlank()) {
                    write(row, 1, document.period(), bodyStyle);
                }
            }
            for (TabularDocument.Fact fact : document.facts()) {
                Row row = sheet.createRow(r++);
                write(row, 0, fact.label(), labelStyle);
                write(row, 1, fact.value(), bodyStyle);
            }
            for (String note : document.notes()) {
                Row row = sheet.createRow(r++);
                write(row, 0, "Note", labelStyle);
                write(row, 1, note, bodyStyle);
            }
            if (r > 0) {
                r++;
            }

            Row header = sheet.createRow(r++);
            List<String> headers = document.headers();
            for (int c = 0; c < headers.size(); c++) {
                write(header, c, headers.get(c), headerStyle);
            }
            for (List<String> line : document.rows()) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < line.size(); c++) {
                    write(row, c, line.get(c), bodyStyle);
                }
            }
            if (!headers.isEmpty()) {
                sheet.createFreezePane(0, header.getRowNum() + 1);
            }
            applyWidths(sheet, document);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException cannotWrite) {
            // Écriture en mémoire : une IOException ici signale un défaut
            // de la bibliothèque, pas une condition métier récupérable.
            throw new UncheckedIOException(cannotWrite);
        }
    }

    private static void applyWidths(Sheet sheet, TabularDocument document) {
        List<String> headers = document.headers();
        for (int c = 0; c < headers.size(); c++) {
            int width = length(headers.get(c));
            int sampled = 0;
            for (List<String> row : document.rows()) {
                if (sampled++ >= WIDTH_SAMPLE_ROWS) {
                    break;
                }
                if (c < row.size()) {
                    width = Math.max(width, length(row.get(c)));
                }
            }
            sheet.setColumnWidth(c, Math.min(MAX_COLUMN_CHARS, width + 2) * CHAR_WIDTH_UNITS);
        }
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static void write(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(CellSafety.neutralize(value));
        cell.setCellStyle(style);
    }

    /**
     * Nom de feuille accepté par Excel : 31 caractères au plus, et aucun
     * des caractères {@code : \ / ? * [ ]}.
     */
    private static String sheetName(String title) {
        String cleaned = (title == null || title.isBlank() ? "Rapport" : title)
                .replaceAll("[\\\\/:*?\\[\\]]", " ")
                .trim();
        if (cleaned.isEmpty()) {
            cleaned = "Rapport";
        }
        return cleaned.length() <= 31 ? cleaned : cleaned.substring(0, 31);
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private static CellStyle boldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = boldStyle(workbook);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private static CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }
}
