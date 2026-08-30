package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.shared.config.JpaAuditingConfig;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL de la migration V10 pour le module {@code attendance} :
 * historique append-only {@code attendance_correction} (FK {@code RESTRICT},
 * {@code CHECK action}, unicité {@code public_id}) et justificatif métier
 * {@code attendance_justification} (unicité d'un justificatif actif par
 * absence, re-dépôt après refus, {@code CHECK} de catégorie / statut /
 * cohérence d'examen, FK {@code RESTRICT}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AttendanceManagementConstraintsTests {

    @Autowired
    private AttendanceRecordRepository recordRepository;
    @Autowired
    private AttendanceCorrectionRepository correctionRepository;
    @Autowired
    private AttendanceJustificationRepository justificationRepository;
    @Autowired
    private TestEntityManager entityManager;

    private long checkpointId;
    private long enrollmentId;
    private long studentUserId;
    private long reviewerUserId;

    @BeforeEach
    void seedChain() {
        studentUserId = insertUser();
        reviewerUserId = insertUser();
        long siteId = insertSite();
        long programId = insertProgram();
        long levelId = insertProgramLevel(programId);
        long yearId = insertAcademicYear();
        long promotionId = insertPromotion(programId, yearId);
        long classId = insertClassGroup(promotionId, levelId, siteId);
        long profileId = insertStudentProfile(studentUserId);
        enrollmentId = insertEnrollment(profileId, classId, yearId);
        long sessionId = insertCourseSession(studentUserId);
        checkpointId = insertCheckpoint(sessionId);
    }

    // ------------------------------------------------------------------
    // attendance_correction
    // ------------------------------------------------------------------

    @Test
    void correctionHistoryIsAppendOnlyAndOrdered() {
        AttendanceRecord record = persistAbsentRecord();
        Instant t0 = Instant.parse("2026-09-10T10:00:00Z");
        correctionRepository.saveAndFlush(AttendanceCorrection.created(
                record.getId(), AttendanceStatus.ABSENT, null, "absent", "constat", reviewerUserId, t0));
        correctionRepository.saveAndFlush(AttendanceCorrection.statusCorrected(
                record.getId(), AttendanceStatus.ABSENT, AttendanceStatus.PRESENT, null, null,
                "absent", "présent finalement", "erreur de saisie", reviewerUserId, t0.plusSeconds(60)));
        entityManager.clear();

        assertThat(correctionRepository.findByAttendanceRecordIdOrderByOccurredAtAscIdAsc(record.getId()))
                .extracting(AttendanceCorrection::getAction)
                .containsExactly(AttendanceCorrectionAction.CREATED_MANUALLY,
                        AttendanceCorrectionAction.STATUS_CORRECTED);
    }

    @Test
    void correctionUnknownActionIsRejectedByCheck() {
        AttendanceRecord record = persistAbsentRecord();
        // chk_attendance_correction_action : violation de CHECK MySQL via
        // requête native → GenericJDBCException (pas de traduction Spring).
        assertThrows(org.hibernate.exception.GenericJDBCException.class, () ->
                entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO attendance_correction (public_id, attendance_record_id, action, reason,
                                occurred_at, created_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :rec, 'DELETED', 'x',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """).setParameter("rec", record.getId()).executeUpdate());
    }

    @Test
    void correctionRecordForeignKeyIsRestrict() {
        AttendanceRecord record = persistAbsentRecord();
        correctionRepository.saveAndFlush(AttendanceCorrection.created(
                record.getId(), AttendanceStatus.ABSENT, null, null, "constat", reviewerUserId,
                Instant.parse("2026-09-10T10:00:00Z")));
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM attendance_record WHERE id = :id")
                    .setParameter("id", record.getId()).executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------
    // attendance_justification
    // ------------------------------------------------------------------

    @Test
    void onlyOneActiveJustificationPerAbsenceIsAllowed() {
        AttendanceRecord record = persistAbsentRecord();
        justificationRepository.saveAndFlush(new AttendanceJustification(
                record.getId(), JustificationCategory.MEDICAL, null, "certificat", studentUserId,
                Instant.parse("2026-09-10T12:00:00Z")));

        AttendanceJustification second = new AttendanceJustification(record.getId(),
                JustificationCategory.TRANSPORT, null, "grève", studentUserId,
                Instant.parse("2026-09-10T12:30:00Z"));
        assertThrows(DataIntegrityViolationException.class,
                () -> justificationRepository.saveAndFlush(second));
    }

    @Test
    void newJustificationIsAllowedAfterRejection() {
        AttendanceRecord record = persistAbsentRecord();
        AttendanceJustification first = new AttendanceJustification(record.getId(),
                JustificationCategory.MEDICAL, null, "certificat", studentUserId,
                Instant.parse("2026-09-10T12:00:00Z"));
        first.reject(reviewerUserId, Instant.parse("2026-09-11T09:00:00Z"), "pièce illisible");
        justificationRepository.saveAndFlush(first);

        AttendanceJustification second = new AttendanceJustification(record.getId(),
                JustificationCategory.TRANSPORT, "REF-42", "grève confirmée", studentUserId,
                Instant.parse("2026-09-11T10:00:00Z"));
        assertDoesNotThrow(() -> justificationRepository.saveAndFlush(second));
    }

    @Test
    void acceptedJustificationCannotCoexistWithPendingOnSameAbsence() {
        AttendanceRecord record = persistAbsentRecord();
        AttendanceJustification accepted = new AttendanceJustification(record.getId(),
                JustificationCategory.FAMILY, null, "événement familial", studentUserId,
                Instant.parse("2026-09-10T12:00:00Z"));
        accepted.accept(reviewerUserId, Instant.parse("2026-09-11T09:00:00Z"));
        justificationRepository.saveAndFlush(accepted);

        AttendanceJustification another = new AttendanceJustification(record.getId(),
                JustificationCategory.OTHER, null, "autre", studentUserId,
                Instant.parse("2026-09-12T08:00:00Z"));
        assertThrows(DataIntegrityViolationException.class,
                () -> justificationRepository.saveAndFlush(another));
    }

    @Test
    void unknownCategoryIsRejectedByCheck() {
        AttendanceRecord record = persistAbsentRecord();
        assertThrows(org.hibernate.exception.GenericJDBCException.class, () ->
                entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO attendance_justification (public_id, attendance_record_id, category, comment,
                                status, submitted_at, submitted_by_id, created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :rec, 'SPORT', 'x', 'PENDING',
                                UTC_TIMESTAMP(6), :usr, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """).setParameter("rec", record.getId()).setParameter("usr", studentUserId).executeUpdate());
    }

    @Test
    void pendingJustificationCannotCarryReviewerByCheck() {
        AttendanceRecord record = persistAbsentRecord();
        assertThrows(org.hibernate.exception.GenericJDBCException.class, () ->
                entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO attendance_justification (public_id, attendance_record_id, category, comment,
                                status, submitted_at, submitted_by_id, reviewed_at, reviewed_by_id,
                                created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :rec, 'MEDICAL', 'x', 'PENDING',
                                UTC_TIMESTAMP(6), :usr, UTC_TIMESTAMP(6), :rev,
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """).setParameter("rec", record.getId()).setParameter("usr", studentUserId)
                        .setParameter("rev", reviewerUserId).executeUpdate());
    }

    @Test
    void justificationRecordForeignKeyIsRestrict() {
        AttendanceRecord record = persistAbsentRecord();
        justificationRepository.saveAndFlush(new AttendanceJustification(record.getId(),
                JustificationCategory.MEDICAL, null, "certificat", studentUserId,
                Instant.parse("2026-09-10T12:00:00Z")));
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM attendance_record WHERE id = :id")
                    .setParameter("id", record.getId()).executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------
    // Fabriques / insertions natives
    // ------------------------------------------------------------------

    private AttendanceRecord persistAbsentRecord() {
        return recordRepository.saveAndFlush(new AttendanceRecord(checkpointId, enrollmentId, studentUserId,
                studentUserId, Instant.parse("2026-09-10T09:05:00Z"), AttendanceRecordSource.MANUAL,
                AttendanceStatus.ABSENT, null, "absent constaté"));
    }

    private long insertUser() {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                          created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Att', 'Tester', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("email", "att-" + UUID.randomUUID() + "@esic-connect.test").executeUpdate();
        return lastId();
    }

    private long insertSite() {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO site (public_id, code, name, time_zone_id, status, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Campus', 'Europe/Paris', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("code", "SITE-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertProgram() {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO program (public_id, code, name, program_type, status, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'BTS SIO', 'BTS', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("code", "PRG-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertProgramLevel(long programId) {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO program_level (public_id, program_id, code, name, sequence_number, status,
                                           created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :programId, :code, 'BTS 1', 1, 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("programId", programId).setParameter("code", "N-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertAcademicYear() {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO academic_year (public_id, code, name, start_date, end_date, status,
                                           created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, '2026-2027', '2026-09-01', '2027-08-31',
                        'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("code", "AY-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertPromotion(long programId, long yearId) {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO promotion (public_id, program_id, academic_year_id, code, name, status,
                                       created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :programId, :yearId, :code, 'Promo', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("programId", programId).setParameter("yearId", yearId)
                .setParameter("code", "P-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertClassGroup(long promotionId, long levelId, long siteId) {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO class_group (public_id, promotion_id, program_level_id, site_id, code, name, status,
                                         created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :promotionId, :levelId, :siteId, :code, 'Classe',
                        'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("promotionId", promotionId).setParameter("levelId", levelId)
                .setParameter("siteId", siteId).setParameter("code", "C-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertStudentProfile(long userId) {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO student_profile (public_id, user_id, student_number, work_study, status,
                                             created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :userId, :number, FALSE, 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("userId", userId).setParameter("number", "STU-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertEnrollment(long profileId, long classId, long yearId) {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO enrollment (public_id, student_profile_id, class_group_id, academic_year_id,
                                        start_date, status, enrollment_source, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :profileId, :classId, :yearId, '2026-09-01',
                        'ACTIVE', 'MANUAL', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("profileId", profileId).setParameter("classId", classId)
                .setParameter("yearId", yearId).executeUpdate();
        return lastId();
    }

    private long insertCourseSession(long teacherId) {
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO course_session (public_id, teacher_user_id, status, starts_at, ends_at,
                                            time_zone_id, exception_reason, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :teacherId, 'PLANNED',
                        '2026-09-10 08:00:00', '2026-09-10 12:00:00', 'Europe/Paris', 'test',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("teacherId", teacherId).executeUpdate();
        return lastId();
    }

    private long insertCheckpoint(long sessionId) {
        // Insertion V9 « minimale » : les colonnes V10 s'appuient sur leur
        // DEFAULT SQL — vérifie la compatibilité ascendante de la reprise.
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO attendance_checkpoint (public_id, course_session_id, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :sessionId, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("sessionId", sessionId).executeUpdate();
        return lastId();
    }

    private long lastId() {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
