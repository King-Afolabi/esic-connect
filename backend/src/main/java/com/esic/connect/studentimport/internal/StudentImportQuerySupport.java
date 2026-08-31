package com.esic.connect.studentimport.internal;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Fabrique de {@link Pageable} pour les consultations d'import : taille
 * bornée et tri restreint à une liste blanche — toute autre valeur lève
 * {@link StudentImportException.Kind#INVALID_SORT}. Aligné sur
 * {@code enrollment.internal.EnrollmentQuerySupport}.
 */
final class StudentImportQuerySupport {

    static final Set<String> JOB_SORT_FIELDS = Set.of("createdAt");
    static final Set<String> ROW_SORT_FIELDS = Set.of("rowNumber");
    static final int MAX_JOB_PAGE_SIZE = 50;
    static final int MAX_ROW_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private StudentImportQuerySupport() {
    }

    static Pageable jobs(int page, int size, String sort) {
        return PageRequest.of(Math.max(page, 0), clamp(size, MAX_JOB_PAGE_SIZE),
                parseSort(sort, JOB_SORT_FIELDS, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    static Pageable rows(int page, int size, String sort) {
        return PageRequest.of(Math.max(page, 0), clamp(size, MAX_ROW_PAGE_SIZE),
                parseSort(sort, ROW_SORT_FIELDS, Sort.by(Sort.Direction.ASC, "rowNumber")));
    }

    private static int clamp(int size, int max) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, max);
    }

    private static Sort parseSort(String sort, Set<String> allowed, Sort fallback) {
        if (sort == null || sort.isBlank()) {
            return fallback;
        }
        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        if (!allowed.contains(field)) {
            throw new StudentImportException(StudentImportException.Kind.INVALID_SORT);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.INVALID_SORT));
        }
        return Sort.by(direction, field);
    }
}
