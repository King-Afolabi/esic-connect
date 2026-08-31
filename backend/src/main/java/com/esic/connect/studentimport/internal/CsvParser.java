package com.esic.connect.studentimport.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lecteur CSV maison, conforme à RFC 4180 (rapport §5) : guillemets,
 * guillemet doublé, cellules multi-lignes entre guillemets, fins de ligne
 * {@code CRLF} / {@code LF}. Le séparateur ({@code ','} ou {@code ';'})
 * est auto-détecté sur l'en-tête (celui qui reconnaît le plus de
 * colonnes ; égalité → {@code ','}). Le contenu doit déjà être décodé en
 * UTF-8 et débarrassé du BOM par {@link CsvFileGuard}.
 *
 * <p>Composant pur : n'accède à aucune base et n'évalue jamais une
 * cellule (aucune exécution de formule — rapport §5.4).
 */
final class CsvParser {

    private static final char[] CANDIDATE_SEPARATORS = {',', ';'};

    private CsvParser() {
    }

    /**
     * @param content     contenu texte du fichier (UTF-8, sans BOM)
     * @param maxDataRows nombre maximal de lignes de données admises
     */
    static ParsedCsv parse(String content, int maxDataRows) {
        char separator = detectSeparator(content);
        List<Record> records = tokenize(content, separator);

        int firstNonEmpty = indexOfFirstNonEmpty(records);
        if (firstNonEmpty < 0) {
            return new ParsedCsv(separator, List.of(), mandatoryNames(), List.of(), List.of(),
                    List.of(), false, true);
        }

        List<ParsedCsv.HeaderColumn> header = buildHeader(records.get(firstNonEmpty).fields());
        List<String> missing = missingMandatory(header);
        List<String> ignored = header.stream()
                .filter(h -> h.kind() == ParsedCsv.HeaderKind.IGNORED).map(ParsedCsv.HeaderColumn::rawName).toList();
        List<String> unknown = header.stream()
                .filter(h -> h.kind() == ParsedCsv.HeaderKind.UNKNOWN).map(ParsedCsv.HeaderColumn::rawName).toList();

        List<ParsedCsv.DataRow> rows = new ArrayList<>();
        boolean tooMany = false;
        for (int i = firstNonEmpty + 1; i < records.size(); i++) {
            Record record = records.get(i);
            if (isEmpty(record.fields())) {
                continue; // ligne entièrement vide : ignorée, non comptée
            }
            if (rows.size() >= maxDataRows) {
                tooMany = true;
                break;
            }
            boolean mismatch = record.fields().size() != header.size();
            rows.add(new ParsedCsv.DataRow(record.startLine(), List.copyOf(record.fields()), mismatch));
        }

        boolean noData = rows.isEmpty() && !tooMany;
        return new ParsedCsv(separator, header, missing, ignored, unknown, List.copyOf(rows), tooMany, noData);
    }

    // ------------------------------------------------------------------
    // Détection du séparateur
    // ------------------------------------------------------------------

    private static char detectSeparator(String content) {
        char best = CANDIDATE_SEPARATORS[0];
        int bestScore = -1;
        for (char candidate : CANDIDATE_SEPARATORS) {
            List<Record> records = tokenize(content, candidate);
            int firstNonEmpty = indexOfFirstNonEmpty(records);
            int score = firstNonEmpty < 0 ? 0 : countRecognized(records.get(firstNonEmpty).fields());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static int countRecognized(List<String> headerFields) {
        int count = 0;
        for (String field : headerFields) {
            if (RecognizedColumn.forHeader(field).isPresent()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Tokenizer RFC 4180
    // ------------------------------------------------------------------

    private static List<Record> tokenize(String content, char separator) {
        List<Record> records = new ArrayList<>();
        int i = 0;
        int line = 1;
        int n = content.length();
        while (i < n) {
            int startLine = line;
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            boolean endOfRecord = false;
            while (i < n && !endOfRecord) {
                char c = content.charAt(i);
                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < n && content.charAt(i + 1) == '"') {
                            field.append('"');
                            i += 2;
                        } else {
                            inQuotes = false;
                            i++;
                        }
                    } else {
                        if (c == '\n') {
                            line++;
                        }
                        field.append(c);
                        i++;
                    }
                    continue;
                }
                if (c == '"' && field.length() == 0) {
                    inQuotes = true;
                    i++;
                } else if (c == separator) {
                    fields.add(field.toString());
                    field.setLength(0);
                    i++;
                } else if (c == '\r') {
                    i++;
                    if (i < n && content.charAt(i) == '\n') {
                        i++;
                    }
                    line++;
                    endOfRecord = true;
                } else if (c == '\n') {
                    i++;
                    line++;
                    endOfRecord = true;
                } else {
                    field.append(c);
                    i++;
                }
            }
            fields.add(field.toString());
            records.add(new Record(startLine, fields));
        }
        return records;
    }

    private static int indexOfFirstNonEmpty(List<Record> records) {
        for (int i = 0; i < records.size(); i++) {
            if (!isEmpty(records.get(i).fields())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isEmpty(List<String> fields) {
        return fields.stream().allMatch(f -> f == null || f.isBlank());
    }

    // ------------------------------------------------------------------
    // En-tête
    // ------------------------------------------------------------------

    private static List<ParsedCsv.HeaderColumn> buildHeader(List<String> rawFields) {
        List<ParsedCsv.HeaderColumn> header = new ArrayList<>();
        List<RecognizedColumn> alreadyBound = new ArrayList<>();
        for (int index = 0; index < rawFields.size(); index++) {
            String raw = rawFields.get(index) == null ? "" : rawFields.get(index).strip();
            Optional<RecognizedColumn> recognized = RecognizedColumn.forHeader(raw);
            ParsedCsv.HeaderKind kind;
            if (recognized.isPresent() && !alreadyBound.contains(recognized.get())) {
                alreadyBound.add(recognized.get());
                kind = ParsedCsv.HeaderKind.RECOGNIZED;
            } else if (RecognizedColumn.isIgnoredHeader(raw)) {
                recognized = Optional.empty();
                kind = ParsedCsv.HeaderKind.IGNORED;
            } else {
                recognized = Optional.empty();
                kind = ParsedCsv.HeaderKind.UNKNOWN;
            }
            header.add(new ParsedCsv.HeaderColumn(raw, index, recognized, kind));
        }
        return header;
    }

    private static List<String> missingMandatory(List<ParsedCsv.HeaderColumn> header) {
        List<String> present = header.stream()
                .map(ParsedCsv.HeaderColumn::recognized)
                .filter(Optional::isPresent).map(Optional::get)
                .map(RecognizedColumn::name).toList();
        List<String> missing = new ArrayList<>();
        for (RecognizedColumn column : RecognizedColumn.values()) {
            if (column.mandatory() && !present.contains(column.name())) {
                missing.add(column.header());
            }
        }
        return missing;
    }

    private static List<String> mandatoryNames() {
        List<String> names = new ArrayList<>();
        for (RecognizedColumn column : RecognizedColumn.values()) {
            if (column.mandatory()) {
                names.add(column.header());
            }
        }
        return names;
    }

    /** Un enregistrement brut : n° de ligne de début + cellules non normalisées. */
    private record Record(int startLine, List<String> fields) {
    }
}
