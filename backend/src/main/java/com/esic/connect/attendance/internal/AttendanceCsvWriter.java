package com.esic.connect.attendance.internal;

import java.util.List;

/**
 * Écriture CSV des rapports d'assiduité (V10).
 *
 * <ul>
 *   <li>UTF-8 avec BOM (compatibilité Excel FR) ;</li>
 *   <li>séparateur {@code ;} ; fin de ligne {@code CRLF} ;</li>
 *   <li>une ligne d'en-tête ;</li>
 *   <li>quoting RFC 4180 (guillemets doublés) dès qu'une cellule contient
 *       {@code ;}, un guillemet ou un saut de ligne ;</li>
 *   <li><strong>neutralisation d'injection de formule</strong> : toute
 *       cellule commençant par {@code =}, {@code +}, {@code -}, {@code @},
 *       une tabulation ou un retour chariot est préfixée d'une apostrophe
 *       {@code '} ;</li>
 *   <li>aucune adresse électronique, aucun identifiant SQL (les appelants
 *       ne fournissent que des identifiants publics et des libellés).</li>
 * </ul>
 */
final class AttendanceCsvWriter {

    private static final String SEP = ";";
    private static final String EOL = "\r\n";
    private static final String BOM = "﻿";

    private AttendanceCsvWriter() {
    }

    static String write(List<String> header, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder(BOM);
        appendRow(sb, header);
        for (List<String> row : rows) {
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
        String value = raw == null ? "" : raw;
        // Neutralisation d'injection de formule.
        if (!value.isEmpty()) {
            char first = value.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                value = "'" + value;
            }
        }
        boolean mustQuote = value.contains(SEP) || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        if (mustQuote) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
