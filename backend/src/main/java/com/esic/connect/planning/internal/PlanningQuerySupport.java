package com.esic.connect.planning.internal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Construit un {@link Pageable} borné avec tri en liste blanche — toute
 * autre valeur lève {@link PlanningException.Kind#INVALID_SORT}. Aligné
 * sur {@code studentimport.internal.StudentImportQuerySupport}.
 */
final class PlanningQuerySupport {

    private static final int MAX_SIZE = 200;

    private PlanningQuerySupport() {
    }

    static Pageable pageable(int page, int size, String sort, Set<String> sortableFields, Sort defaultSort) {
        return PageRequest.of(Math.max(page, 0), normalizeSize(size), parseSort(sort, sortableFields, defaultSort));
    }

    private static int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static Sort parseSort(String sort, Set<String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!sortableFields.contains(field)) {
            throw new PlanningException(PlanningException.Kind.INVALID_SORT);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new PlanningException(PlanningException.Kind.INVALID_SORT));
        }
        return Sort.by(direction, field);
    }
}
