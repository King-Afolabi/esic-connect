package com.esic.connect.alternation.internal;

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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL de la migration V8 : unicités ({@code code},
 * {@code public_id}), clés étrangères {@code RESTRICT}, {@code CHECK} des
 * dates, verrouillage optimiste, unicité de l'affectation ACTIVE
 * « ouverte » par classe (colonne générée {@code active_open_key}) avec
 * créneau libéré par une clôture, et annulation d'une exception sans
 * suppression physique.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AlternationConstraintsTests {

    @Autowired
    private WorkStudyPatternRepository patternRepository;
    @Autowired
    private ClassWorkStudyPatternRepository assignmentRepository;
    @Autowired
    private StudentScheduleExceptionRepository exceptionRepository;
    @Autowired
    private TestEntityManager entityManager;

    private long classGroupId;
    private long otherClassGroupId;
    private long enrollmentId;

    @BeforeEach
    void seedChain() {
        long userId = insertUser();
        long siteId = insertSite();
        long programId = insertProgram();
        long levelId = insertProgramLevel(programId);
        long yearId = insertAcademicYear();
        long promotionId = insertPromotion(programId, yearId);
        classGroupId = insertClassGroup(promotionId, levelId, siteId, "C1-" + shortCode());
        otherClassGroupId = insertClassGroup(promotionId, levelId, siteId, "C2-" + shortCode());
        long profileId = insertStudentProfile(userId);
        enrollmentId = insertEnrollment(profileId, classGroupId, yearId);
    }

    // ------------------------------------------------------------------
    // work_study_pattern
    // ------------------------------------------------------------------

    @Test
    void patternCodeMustBeUnique() {
        String code = "RYT-" + shortCode();
        patternRepository.saveAndFlush(pattern(code));
        assertThrows(DataIntegrityViolationException.class,
                () -> patternRepository.saveAndFlush(pattern(code)));
    }

    @Test
    void patternPublicIdMustBeUnique() {
        WorkStudyPattern first = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        WorkStudyPattern clash = pattern("RYT-" + shortCode());
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> patternRepository.saveAndFlush(clash));
    }

    @Test
    void patternOptimisticLockingIsEnforced() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        long id = pattern.getId();
        entityManager.flush();
        entityManager.clear();

        // Charge une copie avec la version courante, puis fait vieillir
        // cette copie en incrémentant la version en base « dans son dos ».
        WorkStudyPattern stale = patternRepository.findById(id).orElseThrow();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE work_study_pattern SET version = version + 1, name = 'concurrent' "
                        + "WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();

        stale.updateDetails("neuf", null, 1, stale.getConfigurationJson(), null);
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> patternRepository.saveAndFlush(stale));
    }

    // ------------------------------------------------------------------
    // class_work_study_pattern
    // ------------------------------------------------------------------

    @Test
    void assignmentPeriodCheckRejectsValidUntilBeforeValidFrom() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        LocalDate from = LocalDate.of(2026, 9, 1);
        ClassWorkStudyPattern invalid = new ClassWorkStudyPattern(classGroupId, pattern, from, from,
                from.minusDays(1));
        assertThrows(DataAccessException.class, () -> assignmentRepository.saveAndFlush(invalid));
    }

    @Test
    void onlyOneActiveOpenAssignmentPerClass() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        assignmentRepository.saveAndFlush(openAssignment(classGroupId, pattern, LocalDate.of(2026, 9, 1)));
        // Autre classe : accepté.
        assertDoesNotThrow(() -> assignmentRepository.saveAndFlush(
                openAssignment(otherClassGroupId, pattern, LocalDate.of(2026, 9, 1))));
        // Deuxième affectation ouverte sur la même classe : rejetée.
        assertThrows(DataIntegrityViolationException.class, () -> assignmentRepository.saveAndFlush(
                openAssignment(classGroupId, pattern, LocalDate.of(2027, 1, 1))));
    }

    @Test
    void openAssignmentCollisionIsRecognisedFromARealException() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        assignmentRepository.saveAndFlush(openAssignment(classGroupId, pattern, LocalDate.of(2026, 9, 1)));
        DataIntegrityViolationException collision = assertThrows(DataIntegrityViolationException.class,
                () -> assignmentRepository.saveAndFlush(
                        openAssignment(classGroupId, pattern, LocalDate.of(2027, 1, 1))));
        assertThat(ClassAssignmentPersister.isOpenAssignmentUniqueViolation(collision)).isTrue();
    }

    @Test
    void closingAnOpenAssignmentFreesTheSlot() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        ClassWorkStudyPattern first = assignmentRepository.saveAndFlush(
                openAssignment(classGroupId, pattern, LocalDate.of(2026, 9, 1)));
        first.close("changement", null, LocalDate.of(2026, 12, 31));
        assignmentRepository.saveAndFlush(first);
        assertDoesNotThrow(() -> assignmentRepository.saveAndFlush(
                openAssignment(classGroupId, pattern, LocalDate.of(2027, 1, 1))));
    }

    @Test
    void adjacentBoundedAssignmentsAreAllowed() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        ClassWorkStudyPattern a = new ClassWorkStudyPattern(classGroupId, pattern, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        ClassWorkStudyPattern b = new ClassWorkStudyPattern(classGroupId, pattern, LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        assignmentRepository.saveAndFlush(a);
        // Les deux sont ACTIVE et bornées : la contrainte SQL ne vise que
        // les affectations « ouvertes » ; l'adjacence est donc acceptée
        // au niveau SQL (le non-chevauchement complet est vérifié par le
        // service).
        assertDoesNotThrow(() -> assignmentRepository.saveAndFlush(b));
    }

    @Test
    void assignmentClassGroupForeignKeyIsRestrict() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        assignmentRepository.saveAndFlush(openAssignment(classGroupId, pattern, LocalDate.of(2026, 9, 1)));
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM class_group WHERE id = :id")
                    .setParameter("id", classGroupId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void assignmentPatternForeignKeyIsRestrict() {
        WorkStudyPattern pattern = patternRepository.saveAndFlush(pattern("RYT-" + shortCode()));
        assignmentRepository.saveAndFlush(openAssignment(classGroupId, pattern, LocalDate.of(2026, 9, 1)));
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM work_study_pattern WHERE id = :id")
                    .setParameter("id", pattern.getId())
                    .executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------
    // student_schedule_exception
    // ------------------------------------------------------------------

    @Test
    void exceptionPeriodCheckRejectsEndBeforeOrEqualStart() {
        Instant start = Instant.parse("2026-09-10T08:00:00Z");
        StudentScheduleException invalid = new StudentScheduleException(enrollmentId,
                ScheduleExceptionType.REMOTE_ALLOWED, start, start, "Europe/Paris", "motif");
        assertThrows(DataAccessException.class, () -> exceptionRepository.saveAndFlush(invalid));
    }

    @Test
    void exceptionEnrollmentForeignKeyIsRestrict() {
        exceptionRepository.saveAndFlush(exception(enrollmentId));
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM enrollment WHERE id = :id")
                    .setParameter("id", enrollmentId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void cancellingAnExceptionKeepsTheRow() {
        StudentScheduleException exception = exceptionRepository.saveAndFlush(exception(enrollmentId));
        exception.cancel("annulée", null);
        exceptionRepository.saveAndFlush(exception);
        entityManager.clear();
        StudentScheduleException reloaded = exceptionRepository.findById(exception.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ScheduleExceptionStatus.CANCELLED);
        assertThat(reloaded.getCancelReason()).isEqualTo("annulée");
    }

    @Test
    void exceptionPublicIdMustBeUnique() {
        StudentScheduleException first = exceptionRepository.saveAndFlush(exception(enrollmentId));
        StudentScheduleException clash = exception(enrollmentId);
        ReflectionTestUtils.setField(clash, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> exceptionRepository.saveAndFlush(clash));
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static WorkStudyPattern pattern(String code) {
        return new WorkStudyPattern(code, "Rythme " + code, null,
                WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, 1,
                "{\"cycleLengthWeeks\":1,\"schoolWeeks\":[1],\"companyWeeks\":[],"
                        + "\"schoolDays\":[\"MONDAY\"],\"companyDays\":[\"FRIDAY\"]}");
    }

    private static ClassWorkStudyPattern openAssignment(long classGroupId, WorkStudyPattern pattern,
                                                        LocalDate from) {
        return new ClassWorkStudyPattern(classGroupId, pattern, from, from, null);
    }

    private static StudentScheduleException exception(long enrollmentId) {
        return new StudentScheduleException(enrollmentId, ScheduleExceptionType.REMOTE_ALLOWED,
                Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T18:00:00Z"),
                "Europe/Paris", "motif");
    }

    private long insertUser() {
        return insertReturningId("""
                INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                          created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Alt', 'Tester', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, "email", "alt-" + UUID.randomUUID() + "@esic-connect.test");
    }

    private long insertSite() {
        return insertReturningId("""
                INSERT INTO site (public_id, code, name, time_zone_id, status, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Campus', 'Europe/Paris', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, "code", "SITE-" + shortCode());
    }

    private long insertProgram() {
        return insertReturningId("""
                INSERT INTO program (public_id, code, name, program_type, status, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'BTS SIO', 'BTS', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, "code", "PRG-" + shortCode());
    }

    private long insertProgramLevel(long programId) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO program_level (public_id, program_id, code, name, sequence_number, status,
                                           created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :programId, :code, 'BTS 1', 1, 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("programId", programId).setParameter("code", "N-" + shortCode()).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private long insertAcademicYear() {
        return insertReturningId("""
                INSERT INTO academic_year (public_id, code, name, start_date, end_date, status,
                                           created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, '2026-2027', '2026-09-01', '2027-08-31',
                        'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, "code", "AY-" + shortCode());
    }

    private long insertPromotion(long programId, long yearId) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO promotion (public_id, program_id, academic_year_id, code, name, status,
                                       created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :programId, :yearId, :code, 'Promo', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("programId", programId).setParameter("yearId", yearId)
                .setParameter("code", "P-" + shortCode()).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private long insertClassGroup(long promotionId, long levelId, long siteId, String code) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO class_group (public_id, promotion_id, program_level_id, site_id, code, name, status,
                                         created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :promotionId, :levelId, :siteId, :code, 'Classe',
                        'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("promotionId", promotionId).setParameter("levelId", levelId)
                .setParameter("siteId", siteId).setParameter("code", code).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private long insertStudentProfile(long userId) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO student_profile (public_id, user_id, student_number, work_study, status,
                                             created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :userId, :number, FALSE, 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("userId", userId).setParameter("number", "STU-" + shortCode()).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private long insertEnrollment(long profileId, long classGroupId, long yearId) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                INSERT INTO enrollment (public_id, student_profile_id, class_group_id, academic_year_id,
                                        start_date, status, enrollment_source, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :profileId, :classGroupId, :yearId, '2026-09-01',
                        'ACTIVE', 'MANUAL', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """).setParameter("profileId", profileId).setParameter("classGroupId", classGroupId)
                .setParameter("yearId", yearId).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private long insertReturningId(String sql, String paramName, Object paramValue) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery(sql).setParameter(paramName, paramValue).executeUpdate();
        return ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
