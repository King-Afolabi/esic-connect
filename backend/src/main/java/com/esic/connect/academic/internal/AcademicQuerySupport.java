package com.esic.connect.academic.internal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Fabrique commune de {@link Pageable} pour les consultations du module :
 * taille bornée (max 100, défaut 20) et tri restreint à une liste blanche
 * — toute autre valeur lève {@link AcademicException.Kind#INVALID_SORT}
 * plutôt que d'être réinterprétée silencieusement. Normalise aussi le
 * filtre texte (trim, minuscules, longueur bornée). Aligné sur
 * {@code organization.internal.OrganizationQuerySupport}.
 */
final class AcademicQuerySupport {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_QUERY_LENGTH = 100;

    private AcademicQuerySupport() {
    }

    static Pageable pageable(int page, int size, String sort, Set<String> sortableFields, Sort defaultSort) {
        return PageRequest.of(Math.max(page, 0), normalizeSize(size), parseSort(sort, sortableFields, defaultSort));
    }

    private static int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static Sort parseSort(String sort, Set<String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!sortableFields.contains(field)) {
            throw new AcademicException(AcademicException.Kind.INVALID_SORT);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new AcademicException(AcademicException.Kind.INVALID_SORT));
        }
        return Sort.by(direction, field);
    }

    static Optional<String> normalizeText(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_QUERY_LENGTH);
        }
        return Optional.of(trimmed.toLowerCase(Locale.ROOT));
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static Optional<AcademicStatus> parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(AcademicStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AcademicException(AcademicException.Kind.INVALID_FILTER);
        }
    }
}
