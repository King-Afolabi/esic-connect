package com.esic.connect.enrollment.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL de {@code student_profile} et {@code enrollment} (V7) :
 * unicité d'une inscription {@code ACTIVE} par apprenant et par année
 * scolaire (colonnes générées {@code active_student_key}/
 * {@code active_year_key}), libération du créneau par une clôture, année
 * distincte autorisée, unicités {@code user_id} / {@code student_number} /
 * {@code public_id}, {@code CHECK} de période, clés étrangères
 * {@code RESTRICT} (dont l'auto-référence {@code previous_enrollment_id}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class EnrollmentConstraintsTests {

    @Autowired
    private StudentProfileRepository profileRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void secondActiveEnrollmentSameYearIsRejected() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());

        enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));
        assertThrows(DataIntegrityViolationException.class,
                () -> enrollmentRepository.saveAndFlush(active(profile, chain.classB(), chain.year())));
    }

    @Test
    void activeEnrollmentCollisionIsRecognisedByThePersistenceHelper() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));

        DataIntegrityViolationException collision = assertThrows(DataIntegrityViolationException.class,
                () -> enrollmentRepository.saveAndFlush(active(profile, chain.classB(), chain.year())));
        assertThat(EnrollmentPersistence.isActiveEnrollmentUniqueViolation(collision)).isTrue();
    }

    @Test
    void unrelatedIntegrityViolationIsNotRecognisedAsActiveEnrollmentConflict() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        Enrollment first = enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));
        Enrollment duplicatePublicId = active(profile, chain.classB(), chain.year());
        ReflectionTestUtils.setField(duplicatePublicId, "status", EnrollmentStatus.COMPLETED);
        ReflectionTestUtils.setField(duplicatePublicId, "endDate", first.getStartDate());
        ReflectionTestUtils.setField(duplicatePublicId, "publicId", first.getPublicId());

        DataIntegrityViolationException other = assertThrows(DataIntegrityViolationException.class,
                () -> enrollmentRepository.saveAndFlush(duplicatePublicId));
        assertThat(EnrollmentPersistence.isActiveEnrollmentUniqueViolation(other)).isFalse();
    }

    @Test
    void closingTheActiveEnrollmentFreesTheSlot() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        Enrollment first = enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));

        first.close(EnrollmentStatus.TRANSFERRED, "mutation", first.getStartDate(), null);
        enrollmentRepository.saveAndFlush(first);

        assertDoesNotThrow(() -> enrollmentRepository.saveAndFlush(active(profile, chain.classB(), chain.year())));
    }

    @Test
    void activeEnrollmentInADifferentYearIsAllowed() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));
        assertDoesNotThrow(() ->
                enrollmentRepository.saveAndFlush(active(profile, chain.classOtherYear(), chain.otherYear())));
    }

    @Test
    void studentProfileUserIdIsUnique() {
        Chain chain = insertChain();
        newProfile(chain.userId());
        assertThrows(DataIntegrityViolationException.class, () -> {
            profileRepository.saveAndFlush(new StudentProfile(chain.userId(), "ESIC-2026-" + shortCode(),
                    null, false, null));
        });
    }

    @Test
    void studentNumberIsUnique() {
        Chain chain = insertChain();
        long secondUser = insertUser();
        String number = "ESIC-2026-" + shortCode();
        profileRepository.saveAndFlush(new StudentProfile(chain.userId(), number, null, false, null));
        assertThrows(DataIntegrityViolationException.class, () ->
                profileRepository.saveAndFlush(new StudentProfile(secondUser, number, null, false, null)));
    }

    @Test
    void enrollmentPublicIdIsUnique() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        Enrollment first = enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));
        Enrollment second = active(profile, chain.classOtherYear(), chain.otherYear());
        ReflectionTestUtils.setField(second, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class, () -> enrollmentRepository.saveAndFlush(second));
    }

    @Test
    void periodCheckRejectsEndDateBeforeStartDate() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        Enrollment enrollment = active(profile, chain.classA(), chain.year());
        ReflectionTestUtils.setField(enrollment, "status", EnrollmentStatus.WITHDRAWN);
        ReflectionTestUtils.setField(enrollment, "endDate", enrollment.getStartDate().minusDays(1));
        assertThrows(DataAccessException.class, () -> enrollmentRepository.saveAndFlush(enrollment));
    }

    @Test
    void studentProfileForeignKeyIsRestrict() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM student_profile WHERE id = :id")
                    .setParameter("id", profile.getId())
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void classGroupForeignKeyIsRestrict() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM class_group WHERE id = :id")
                    .setParameter("id", chain.classA())
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void previousEnrollmentForeignKeyIsRestrict() {
        Chain chain = insertChain();
        StudentProfile profile = newProfile(chain.userId());
        Enrollment first = enrollmentRepository.saveAndFlush(active(profile, chain.classA(), chain.year()));
        first.close(EnrollmentStatus.TRANSFERRED, "mutation", first.getStartDate(), null);
        enrollmentRepository.saveAndFlush(first);
        Enrollment next = new Enrollment(profile, chain.classB(), chain.year(), first.getStartDate(),
                EnrollmentSource.CLASS_TRANSFER, "mutation", first.getId());
        enrollmentRepository.saveAndFlush(next);

        assertThrows(ConstraintViolationException.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM enrollment WHERE id = :id")
                    .setParameter("id", first.getId())
                    .executeUpdate();
            entityManager.flush();
        });
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private StudentProfile newProfile(long userId) {
        return profileRepository.saveAndFlush(
                new StudentProfile(userId, "ESIC-2026-" + shortCode(), null, false, null));
    }

    private static Enrollment active(StudentProfile profile, long classGroupId, long academicYearId) {
        return new Enrollment(profile, classGroupId, academicYearId, LocalDate.of(2026, 9, 1),
                EnrollmentSource.MANUAL, null, null);
    }

    private record Chain(long userId, long year, long otherYear, long classA, long classB, long classOtherYear) {
    }

    private Chain insertChain() {
        long userId = insertUser();
        long siteId = insertSite();
        long programId = insertProgram();
        long levelId = insertLevel(programId);
        long year = insertYear("2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31));
        long otherYear = insertYear("2027-2028", LocalDate.of(2027, 9, 1), LocalDate.of(2028, 8, 31));
        long promo = insertPromotion(programId, year);
        long promoOther = insertPromotion(programId, otherYear);
        long classA = insertClassGroup(promo, levelId, siteId);
        long classB = insertClassGroup(promo, levelId, siteId);
        long classOtherYear = insertClassGroup(promoOther, levelId, siteId);
        return new Chain(userId, year, otherYear, classA, classB, classOtherYear);
    }

    private long insertUser() {
        EntityManager em = entityManager.getEntityManager();
        String email = "stu-" + UUID.randomUUID() + "@esic-connect.test";
        em.createNativeQuery("""
                        INSERT INTO user_account (public_id, email, first_name, last_name, status,
                                                  created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :email, 'Étu', 'Diant', 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("email", email)
                .executeUpdate();
        return id("SELECT id FROM user_account WHERE email = '" + email + "'");
    }

    private long insertSite() {
        String code = "SITE-" + shortCode();
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO site (public_id, code, name, time_zone_id, status, created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Site', 'Europe/Paris', 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("code", code)
                .executeUpdate();
        return id("SELECT id FROM site WHERE code = '" + code + "'");
    }

    private long insertProgram() {
        String code = "PRG-" + shortCode();
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO program (public_id, code, name, program_type, status,
                                             created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Programme', 'BTS', 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("code", code)
                .executeUpdate();
        return id("SELECT id FROM program WHERE code = '" + code + "'");
    }

    private long insertLevel(long programId) {
        String code = "LVL-" + shortCode();
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO program_level (public_id, program_id, code, name, sequence_number, status,
                                                   created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :programId, :code, 'Niveau', 1, 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("programId", programId)
                .setParameter("code", code)
                .executeUpdate();
        return id("SELECT id FROM program_level WHERE code = '" + code + "'");
    }

    private long insertYear(String code, LocalDate start, LocalDate end) {
        String uniqueCode = code + "-" + shortCode();
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO academic_year (public_id, code, name, start_date, end_date, status,
                                                   created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Année', :start, :end, 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("code", uniqueCode)
                .setParameter("start", start)
                .setParameter("end", end)
                .executeUpdate();
        return id("SELECT id FROM academic_year WHERE code = '" + uniqueCode + "'");
    }

    private long insertPromotion(long programId, long yearId) {
        String code = "PROMO-" + shortCode();
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO promotion (public_id, program_id, academic_year_id, code, name, status,
                                               created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :programId, :yearId, :code, 'Promotion', 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("programId", programId)
                .setParameter("yearId", yearId)
                .setParameter("code", code)
                .executeUpdate();
        return id("SELECT id FROM promotion WHERE code = '" + code + "'");
    }

    private long insertClassGroup(long promotionId, long levelId, long siteId) {
        String code = "CLS-" + shortCode();
        entityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO class_group (public_id, promotion_id, program_level_id, site_id, code, name,
                                                 status, created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :promotionId, :levelId, :siteId, :code, 'Classe',
                                'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("promotionId", promotionId)
                .setParameter("levelId", levelId)
                .setParameter("siteId", siteId)
                .setParameter("code", code)
                .executeUpdate();
        return id("SELECT id FROM class_group WHERE code = '" + code + "'");
    }

    private long id(String sql) {
        Object value = entityManager.getEntityManager().createNativeQuery(sql).getSingleResult();
        return ((Number) value).longValue();
    }

    private static String shortCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
