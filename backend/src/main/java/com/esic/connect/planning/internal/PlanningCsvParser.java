package com.esic.connect.planning.internal;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lecteur CSV maison conforme à RFC 4180 (tokenizer aligné sur
 * {@code studentimport.internal.CsvParser} — guillemets, guillemet doublé,
 * cellules multi-lignes, {@code CRLF} / {@code LF}). Séparateur
 * ({@code ','} ou {@code ';'}) auto-détecté sur l'en-tête (celui qui
 * reconnaît le plus de colonnes ; égalité → {@code ','}). Le contenu doit
 * déjà être décodé UTF-8 sans BOM par {@link PlanningCsvGuard}.
 *
 * <p>Composant pur : n'accède à aucune base, n'évalue jamais une cellule.
 */
final class PlanningCsvParser {

    private static final char[] CANDIDATE_SEPARATORS = {',', ';'};

    private PlanningCsvParser() {
    }

    static ParsedPlanningCsv parse(String content, int maxDataRows) {
        char separator = detectSeparator(content);
        List<Record> records = tokenize(content, separator);

        int firstNonEmpty = indexOfFirstNonEmpty(records);
        if (firstNonEmpty < 0) {
            return new ParsedPlanningCsv(separator, ParsedPlanningCsv.emptyIndex(),
                    mandatoryHeaders(), List.of(), List.of(), false, true);
        }

        List<String> headerFields = records.get(firstNonEmpty).fields();
        Map<PlanningColumn, Integer> index = new EnumMap<>(PlanningColumn.class);
        List<String> unknown = new ArrayList<>();
        for (int i = 0; i < headerFields.size(); i++) {
            String raw = headerFields.get(i) == null ? "" : headerFields.get(i).strip();
            Optional<PlanningColumn> recognized = PlanningColumn.forHeader(raw);
            if (recognized.isPresent()) {
                index.putIfAbsent(recognized.get(), i);
            } else if (!raw.isEmpty()) {
                unknown.add(raw);
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
        for (int i = firstNonEmpty + 1; i < records.size(); i++) {
            Record record = records.get(i);
            if (isEmpty(record.fields())) {
                continue;
            }
            if (rows.size() >= maxDataRows) {
                tooMany = true;
                break;
            }
            boolean mismatch = record.fields().size() != headerFields.size();
            rows.add(new ParsedPlanningCsv.DataRow(record.startLine(), List.copyOf(record.fields()), mismatch));
        }

        boolean noData = rows.isEmpty() && !tooMany;
        return new ParsedPlanningCsv(separator, index, missing, List.copyOf(unknown),
                List.copyOf(rows), tooMany, noData);
    }

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
            if (PlanningColumn.forHeader(field).isPresent()) {
                count++;
            }
        }
        return count;
    }

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

    private static List<String> mandatoryHeaders() {
        List<String> names = new ArrayList<>();
        for (PlanningColumn column : PlanningColumn.values()) {
            if (column.mandatory()) {
                names.add(column.header());
            }
        }
        return names;
    }

    private record Record(int startLine, List<String> fields) {
    }
}
