package com.esic.connect.document.internal;

import com.esic.connect.document.TabularDocument;

import java.util.List;

/**
 * Écriture CSV d'un {@link TabularDocument} (docs/02 §22.4).
 *
 * <ul>
 *   <li>UTF-8 avec BOM — sans lui, Excel en configuration française lit
 *       l'UTF-8 comme du Latin-1 et mutile chaque accent ;</li>
 *   <li>séparateur {@code ;}, fin de ligne {@code CRLF} ;</li>
 *   <li>quoting RFC 4180 (guillemets doublés) ;</li>
 *   <li>injection de formule neutralisée ({@link CellSafety}).</li>
 * </ul>
 *
 * <p><strong>Aucun préambule</strong> : la première ligne est la ligne
 * d'en-tête, et rien d'autre. Le titre, la synthèse et les mentions du
 * document sont délibérément omis ici — un CSV est un format
 * d'interéchange, et tout ce qui précède l'en-tête casse les analyseurs
 * qui supposent, à juste titre, que la ligne 1 décrit les colonnes. Ces
 * informations restent portées par le classeur et par le PDF, qui sont
 * faits pour être lus par une personne.
 */
final class CsvDocumentWriter {

    private static final String SEP = ";";
    private static final String EOL = "\r\n";
    private static final String BOM = "﻿";

    private CsvDocumentWriter() {
    }

    static String write(TabularDocument document) {
        StringBuilder sb = new StringBuilder(BOM);
        appendRow(sb, document.headers());
        for (List<String> row : document.rows()) {
            appendRow(sb, row);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(SEP);
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append(EOL);
    }

    static String escape(String raw) {
        String value = CellSafety.neutralize(raw);
        boolean mustQuote = value.contains(SEP) || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        return mustQuote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
