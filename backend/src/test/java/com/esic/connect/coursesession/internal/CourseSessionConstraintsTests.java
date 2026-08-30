package com.esic.connect.coursesession.internal;

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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL de la migration V9 pour le module {@code coursesession} :
 * unicité {@code public_id}, {@code CHECK} de période
 * ({@code ends_at > starts_at}), unicité {@code (course_session_id,
 * class_group_id)} de {@code session_class}, unicité
 * {@code course_session_id} de {@code attendance_checkpoint}, FK
 * {@code RESTRICT} vers {@code user_account} (formateur) et
 * {@code class_group}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class CourseSessionConstraintsTests {

    @Autowired
    private CourseSessionRepository sessionRepository;
    @Autowired
    private AttendanceCheckpointRepository checkpointRepository;
    @Autowired
    private TestEntityManager entityManager;

    private long teacherId;
    private long classAId;
    private long classBId;

    @BeforeEach
    void seedChain() {
        teacherId = insertUser();
        long siteId = insertSite();
        long programId = insertProgram();
        long levelId = insertProgramLevel(programId);
        long yearId = insertAcademicYear();
        long promotionId = insertPromotion(programId, yearId);
        classAId = insertClassGroup(promotionId, levelId, siteId, "C1-" + shortCode());
        classBId = insertClassGroup(promotionId, levelId, siteId, "C2-" + shortCode());
    }

    @Test
    void sessionAndClassesAndCheckpointPersistTogether() {
        CourseSession session = planned();
        session.addClass(classAId);
        session.addClass(classBId);
        CourseSession saved = sessionRepository.saveAndFlush(session);
        checkpointRepository.saveAndFlush(new AttendanceCheckpoint(saved));
        entityManager.clear();

        CourseSession reloaded = sessionRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getClasses()).hasSize(2);
        assertThat(checkpointRepository.findByCourseSessionId(saved.getId())).isPresent();
    }

    @Test
    void periodCheckRejectsEndBeforeOrEqualStart() {
        Instant start = Instant.parse("2026-09-10T09:00:00Z");
        CourseSession invalid = new CourseSession(teacherId, "x", start, start, "Europe/Paris", "motif");
        invalid.addClass(classAId);
        assertThrows(DataAccessException.class, () -> sessionRepository.saveAndFlush(invalid));
    }

    @Test
    void duplicateClassForSameSessionIsRejected() {
        CourseSession session = planned();
        session.addClass(classAId);
        session.addClass(classAId);
        assertThrows(DataIntegrityViolationException.class, () -> sessionRepository.saveAndFlush(session));
    }

    @Test
    void twoCheckpointsForSameSessionIsRejected() {
        CourseSession session = planned();
        session.addClass(classAId);
        CourseSession saved = sessionRepository.saveAndFlush(session);
        checkpointRepository.saveAndFlush(new AttendanceCheckpoint(saved));
        assertThrows(DataIntegrityViolationException.class,
                () -> checkpointRepository.saveAndFlush(new AttendanceCheckpoint(saved)));
    }

    @Test
    void teacherForeignKeyIsRestrict() {
        CourseSession session = planned();
        session.addClass(classAId);
        sessionRepository.saveAndFlush(session);
        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM user_account WHERE id = :id")
                    .setParameter("id", teacherId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void sessionPublicIdMustBeUnique() {
        CourseSession first = planned();
        first.addClass(classAId);
        CourseSession saved = sessionRepository.saveAndFlush(first);
        CourseSession clash = planned();
        clash.addClass(classBId);
        ReflectionTestUtils.setField(clash, "publicId", saved.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> sessionRepository.saveAndFlush(clash));
    }

    @Test
    void openStateCheckAcceptsPlannedThenOpenThenClosed() {
        CourseSession session = planned();
        session.addClass(classAId);
        CourseSession saved = sessionRepository.saveAndFlush(session);
        assertDoesNotThrow(() -> {
            saved.open(Instant.parse("2026-09-10T08:00:00Z"), null);
            sessionRepository.saveAndFlush(saved);
            saved.close(Instant.parse("2026-09-10T12:00:00Z"), null);
            sessionRepository.saveAndFlush(saved);
        });
    }

    // ------------------------------------------------------------------
    // Fabriques / insertions natives
    // ------------------------------------------------------------------

    private CourseSession planned() {
        return new CourseSession(teacherId, "Séance test",
                Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T12:00:00Z"),
                "Europe/Paris", "séance exceptionnelle de test");
    }

    private long insertUser() {
        return insertReturningId("""
                INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                          created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Cs', 'Tester', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, "email", "cs-" + UUID.randomUUID() + "@esic-connect.test");
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
        return lastId();
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
        return lastId();
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
        return lastId();
    }

    private long insertReturningId(String sql, String paramName, Object paramValue) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery(sql).setParameter(paramName, paramValue).executeUpdate();
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
