package com.esic.connect.attendance.internal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Frontière transactionnelle dédiée à l'insertion d'une présence.
 *
 * <p>{@code saveAndFlush} est isolé dans une transaction
 * {@link Propagation#REQUIRES_NEW} : si la contrainte
 * {@code uq_attendance_record_checkpoint_enrollment} est violée (course
 * entre deux émargements du même apprenant pour le même point de
 * contrôle), <em>cette</em> transaction est annulée sans contaminer
 * l'appelant. Le service ({@link AttendanceService}) reçoit alors la
 * {@link DataIntegrityViolationException} <em>hors</em> de toute
 * transaction en échec et la retraduit en 409
 * ({@code ATT_ALREADY_RECORDED}) si c'est bien cette contrainte, sinon la
 * relance telle quelle. Même approche que
 * {@code alternation.internal.ClassAssignmentPersister}.
 */
@Component
class AttendanceRecordPersister {

    static final String UNIQUE_CONSTRAINT = "uq_attendance_record_checkpoint_enrollment";

    private final AttendanceRecordRepository repository;

    AttendanceRecordPersister(AttendanceRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    AttendanceRecord persist(AttendanceRecord record) {
        return repository.saveAndFlush(record);
    }

    static boolean isDuplicateAttendanceViolation(DataIntegrityViolationException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint) {
                String name = constraint.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(UNIQUE_CONSTRAINT)) {
                    return true;
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains(UNIQUE_CONSTRAINT)
                        && (lower.contains("duplicate entry") || lower.contains("unique"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
