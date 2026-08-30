package com.esic.connect.attendance.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL de la migration V9 pour {@code attendance_record} :
 * unicité {@code (attendance_checkpoint_id, enrollment_id)} — autorité
 * anti-double émargement —, unicité {@code public_id}, FK {@code RESTRICT}
 * vers {@code attendance_checkpoint} et {@code enrollment}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AttendanceRecordConstraintsTests {

    @Autowired
    private AttendanceRecordRepository recordRepository;
    @Autowired
    private TestEntityManager entityManager;

    private long checkpointId;
    private long enrollmentAId;
    private long enrollmentBId;
    private long studentUserId;

    @BeforeEach
    void seedChain() {
        studentUserId = insertUser();
        long siteId = insertSite();
        long programId = insertProgram();
        long levelId = insertProgramLevel(programId);
        long yearId = insertAcademicYear();
        long promotionId = insertPromotion(programId, yearId);
        long classId = insertClassGroup(promotionId, levelId, siteId);
        long profileAId = insertStudentProfile(studentUserId);
        long profileBId = insertStudentProfile(insertUser());
        enrollmentAId = insertEnrollment(profileAId, classId, yearId);
        enrollmentBId = insertEnrollment(profileBId, classId, yearId);
        long sessionId = insertCourseSession(studentUserId);
        checkpointId = insertCheckpoint(sessionId);
    }

    @Test
    void secondRecordForSameCheckpointAndEnrollmentIsRejected() {
        recordRepository.saveAndFlush(record(enrollmentAId));
        assertThrows(DataIntegrityViolationException.class,
                () -> recordRepository.saveAndFlush(record(enrollmentAId)));
    }

    @Test
    void differentEnrollmentsForSameCheckpointAreAccepted() {
        recordRepository.saveAndFlush(record(enrollmentAId));
        AttendanceRecord second = recordRepository.saveAndFlush(record(enrollmentBId));
        assertThat(second.getId()).isNotNull();
        assertThat(recordRepository.findByAttendanceCheckpointIdOrderByRecordedAtAsc(checkpointId)).hasSize(2);
    }

    @Test
    void publicIdMustBeUnique() {
        AttendanceRecord first = recordRepository.saveAndFlush(record(enrollmentAId));
        AttendanceRecord clash = record(enrollmentBId);
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> recordRepository.saveAndFlush(clash));
    }

    @Test
    void manualSourceAndBusinessStatusAreAccepted() {
        // V10 : source MANUAL + statut ABSENT + acteur de saisie.
        AttendanceRecord manual = new AttendanceRecord(checkpointId, enrollmentAId, studentUserId, studentUserId,
                Instant.parse("2026-09-10T09:05:00Z"), AttendanceRecordSource.MANUAL,
                com.esic.connect.attendance.AttendanceStatus.ABSENT, null, "absent constaté");
        AttendanceRecord saved = recordRepository.saveAndFlush(manual);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(com.esic.connect.attendance.AttendanceStatus.ABSENT);
    }

    @Test
    void negativeLateMinutesIsRejected() {
        AttendanceRecord invalid = new AttendanceRecord(checkpointId, enrollmentAId, studentUserId, null,
                Instant.parse("2026-09-10T09:05:00Z"), AttendanceRecordSource.SHORT_CODE,
                com.esic.connect.attendance.AttendanceStatus.LATE, -3, null);
        // chk_attendance_record_late : une violation de CHECK MySQL remonte
        // en DataAccessException (JpaSystemException), pas en
        // DataIntegrityViolationException (réservée aux FK / unicité).
        assertThrows(DataAccessException.class, () -> recordRepository.saveAndFlush(invalid));
    }

    @Test
    void unknownStatusIsRejectedByCheck() {
        assertThrows(org.hibernate.exception.GenericJDBCException.class, () ->
                entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO attendance_record (public_id, attendance_checkpoint_id, enrollment_id,
                                student_user_id, recorded_at, source, status, created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :cp, :enr, :usr,
                                UTC_TIMESTAMP(6), 'SHORT_CODE', 'PARTIAL', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                        .setParameter("cp", checkpointId)
                        .setParameter("enr", enrollmentBId)
                        .setParameter("usr", studentUserId)
                        .executeUpdate());
    }

    @Test
    void enrollmentForeignKeyIsRestrict() {
        recordRepository.saveAndFlush(record(enrollmentAId));
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM enrollment WHERE id = :id")
                    .setParameter("id", enrollmentAId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------

    private AttendanceRecord record(long enrollmentId) {
        return new AttendanceRecord(checkpointId, enrollmentId, studentUserId,
                Instant.parse("2026-09-10T09:05:00Z"), AttendanceRecordSource.SHORT_CODE);
    }

    private long insertUser() {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                          created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Att', 'Tester', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("email", "att-" + UUID.randomUUID() + "@esic-connect.test").executeUpdate();
        return lastId();
    }

    private long insertSite() {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO site (public_id, code, name, time_zone_id, status, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Campus', 'Europe/Paris', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("code", "SITE-" + shortCode()).executeUpdate();
        return lastId();
    }

    private long insertProgram() {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
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
