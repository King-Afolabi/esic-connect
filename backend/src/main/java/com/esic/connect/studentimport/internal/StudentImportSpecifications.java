package com.esic.connect.studentimport.internal;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Prédicats de consultation des imports (rapport §8). Toute valeur de
 * filtre hors énumération lève {@link StudentImportException.Kind#INVALID_FILTER}
 * plutôt que d'être ignorée silencieusement. Aligné sur
 * {@code enrollment.internal.EnrollmentSpecifications}.
 */
final class StudentImportSpecifications {

    private StudentImportSpecifications() {
    }

    // --- Jobs ---

    static Specification<StudentImportJob> jobRequestedBy(Long requesterInternalId) {
        return (root, query, cb) -> cb.equal(root.get("requestedById"), requesterInternalId);
    }

    static Specification<StudentImportJob> jobStatus(String status) {
        StudentImportJobStatus parsed = parseEnum(StudentImportJobStatus.class, status);
        return parsed == null ? null : (root, query, cb) -> cb.equal(root.get("status"), parsed);
    }

    // --- Rows ---

    static Specification<StudentImportRow> rowInJob(Long jobInternalId) {
        return (root, query, cb) -> cb.equal(root.get("job").get("id"), jobInternalId);
    }

    static Specification<StudentImportRow> rowStatus(String rowStatus) {
        StudentImportRowStatus parsed = parseEnum(StudentImportRowStatus.class, rowStatus);
        return parsed == null ? null : (root, query, cb) -> cb.equal(root.get("rowStatus"), parsed);
    }

    static Specification<StudentImportRow> rowPlannedAction(String action) {
        StudentImportPlannedAction parsed = parseEnum(StudentImportPlannedAction.class, action);
        return parsed == null ? null : (root, query, cb) -> cb.equal(root.get("plannedAction"), parsed);
    }

    static Specification<StudentImportRow> rowHasIssueOfSeverity(String severity) {
        StudentImportIssueSeverity parsed = parseEnum(StudentImportIssueSeverity.class, severity);
        if (parsed == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<StudentImportRowIssue> issue = subquery.from(StudentImportRowIssue.class);
            subquery.select(issue.get("id"));
            subquery.where(
                    cb.equal(issue.get("row").get("id"), root.get("id")),
                    cb.equal(issue.get("severity"), parsed));
            return cb.exists(subquery);
        };
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new StudentImportException(StudentImportException.Kind.INVALID_FILTER);
        }
    }
}
