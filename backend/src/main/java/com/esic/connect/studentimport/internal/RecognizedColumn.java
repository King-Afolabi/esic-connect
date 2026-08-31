package com.esic.connect.studentimport.internal;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Colonnes reconnues du modèle CSV d'import des apprenants (rapport §5.2 ;
 * docs/01 §8.1 / docs/02 §10.4, jeu réduit — décision §12.A : 6
 * obligatoires + 5 optionnelles). Les noms d'en-tête sont comparés en
 * minuscules après {@code trim} ; l'ordre des colonnes est libre.
 *
 * <p>{@link #IGNORED_HEADERS} liste les en-têtes de docs/02 §10.4 non
 * repris par cette tranche ({@code level_code} / {@code promotion_code} /
 * {@code work_study_pattern}) : présents, ils produisent un
 * {@code WARNING IMP_COLUMN_IGNORED} sans bloquer.
 */
enum RecognizedColumn {

    LAST_NAME("last_name", true),
    FIRST_NAME("first_name", true),
    EMAIL("email", true),
    FORMATION_CODE("formation_code", true),
    CLASS_CODE("class_code", true),
    ACADEMIC_YEAR("academic_year", true),
    PHONE("phone", false),
    STUDENT_NUMBER("student_number", false),
    BIRTH_DATE("birth_date", false),
    WORK_STUDY("work_study", false),
    COMPANY_NAME("company_name", false);

    /** En-têtes de docs/02 §10.4 volontairement ignorés dans cette tranche (rapport §12.A). */
    static final Set<String> IGNORED_HEADERS = Set.of("level_code", "promotion_code", "work_study_pattern");

    private final String header;
    private final boolean mandatory;

    RecognizedColumn(String header, boolean mandatory) {
        this.header = header;
        this.mandatory = mandatory;
    }

    String header() {
        return header;
    }

    boolean mandatory() {
        return mandatory;
    }

    static Optional<RecognizedColumn> forHeader(String rawHeader) {
        if (rawHeader == null) {
            return Optional.empty();
        }
        String normalized = rawHeader.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.header.equals(normalized)).findFirst();
    }

    static boolean isIgnoredHeader(String rawHeader) {
        return rawHeader != null && IGNORED_HEADERS.contains(rawHeader.trim().toLowerCase(Locale.ROOT));
    }
}
