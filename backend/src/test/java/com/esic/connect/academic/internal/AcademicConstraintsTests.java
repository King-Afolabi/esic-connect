package com.esic.connect.academic.internal;

import com.esic.connect.shared.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
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

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contraintes SQL du référentiel académique (docs/04 §12, §28.1) :
 * unicités ({@code academic_year.code}, {@code program.code},
 * {@code (program_id, code)}, {@code (program_id, academic_year_id, code)},
 * {@code (promotion_id, code)}, {@code public_id}) et FK RESTRICT.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class AcademicConstraintsTests {

    @Autowired
    private AcademicYearRepository academicYearRepository;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramLevelRepository programLevelRepository;
    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private ClassGroupRepository classGroupRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void academicYearCodeMustBeUnique() {
        String code = shortCode();
        academicYearRepository.saveAndFlush(year(code));
        assertThrows(DataIntegrityViolationException.class,
                () -> academicYearRepository.saveAndFlush(year(code)));
    }

    @Test
    void programCodeMustBeUnique() {
        String code = shortCode();
        programRepository.saveAndFlush(program(code));
        assertThrows(DataIntegrityViolationException.class,
                () -> programRepository.saveAndFlush(program(code)));
    }

    @Test
    void programPublicIdMustBeUnique() {
        Program first = programRepository.saveAndFlush(program(shortCode()));
        Program second = program(shortCode());
        ReflectionTestUtils.setField(second, "publicId", first.getPublicId());
        assertThrows(DataIntegrityViolationException.class,
                () -> programRepository.saveAndFlush(second));
    }

    @Test
    void levelCodeMustBeUniquePerProgramButFreeAcrossPrograms() {
        Program programA = programRepository.saveAndFlush(program(shortCode()));
        Program programB = programRepository.saveAndFlush(program(shortCode()));
        programLevelRepository.saveAndFlush(new ProgramLevel(programA, "N1", "Niveau 1", (short) 1));

        // Même code, autre formation : accepté.
        programLevelRepository.saveAndFlush(new ProgramLevel(programB, "N1", "Niveau 1", (short) 1));
        // Même code, même formation : rejeté.
        assertThrows(DataIntegrityViolationException.class,
                () -> programLevelRepository.saveAndFlush(new ProgramLevel(programA, "N1", "Niveau 1 bis", (short) 2)));
    }

    @Test
    void promotionCodeMustBeUniquePerProgramAndYear() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        promotionRepository.saveAndFlush(new Promotion(program, year, "P1", "Promo 1", null, null));

        assertThrows(DataIntegrityViolationException.class,
                () -> promotionRepository.saveAndFlush(new Promotion(program, year, "P1", "Promo 1 bis", null, null)));
    }

    @Test
    void classGroupCodeMustBeUniquePerPromotion() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        Promotion promotion = promotionRepository.saveAndFlush(
                new Promotion(program, year, "P1", "Promo 1", null, null));
        ProgramLevel level = programLevelRepository.saveAndFlush(
                new ProgramLevel(program, "N1", "Niveau 1", (short) 1));
        long siteId = insertSite(shortCode());

        classGroupRepository.saveAndFlush(new ClassGroup(promotion, level, siteId, "C1", "Classe 1", 24));
        assertThrows(DataIntegrityViolationException.class,
                () -> classGroupRepository.saveAndFlush(
                        new ClassGroup(promotion, level, siteId, "C1", "Classe 1 bis", 12)));
    }

    @Test
    void deletingProgramReferencedByPromotionIsRejected() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        promotionRepository.saveAndFlush(new Promotion(program, year, "P1", "Promo 1", null, null));
        Long programId = program.getId();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            programRepository.deleteById(programId);
            programRepository.flush();
        });
    }

    @Test
    void deletingPromotionReferencedByClassGroupIsRejected() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        Promotion promotion = promotionRepository.saveAndFlush(
                new Promotion(program, year, "P1", "Promo 1", null, null));
        ProgramLevel level = programLevelRepository.saveAndFlush(
                new ProgramLevel(program, "N1", "Niveau 1", (short) 1));
        long siteId = insertSite(shortCode());
        classGroupRepository.saveAndFlush(new ClassGroup(promotion, level, siteId, "C1", "Classe 1", 24));
        Long promotionId = promotion.getId();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            promotionRepository.deleteById(promotionId);
            promotionRepository.flush();
        });
    }

    @Test
    void deletingAcademicYearReferencedByPromotionIsRejected() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        promotionRepository.saveAndFlush(new Promotion(program, year, "P1", "Promo 1", null, null));
        Long yearId = year.getId();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            academicYearRepository.deleteById(yearId);
            academicYearRepository.flush();
        });
    }

    @Test
    void deletingProgramLevelReferencedByClassGroupIsRejected() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        Promotion promotion = promotionRepository.saveAndFlush(
                new Promotion(program, year, "P1", "Promo 1", null, null));
        ProgramLevel level = programLevelRepository.saveAndFlush(
                new ProgramLevel(program, "N1", "Niveau 1", (short) 1));
        long siteId = insertSite(shortCode());
        classGroupRepository.saveAndFlush(new ClassGroup(promotion, level, siteId, "C1", "Classe 1", 24));
        Long levelId = level.getId();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> {
            programLevelRepository.deleteById(levelId);
            programLevelRepository.flush();
        });
    }

    @Test
    void deletingSiteReferencedByClassGroupIsRejected() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        Promotion promotion = promotionRepository.saveAndFlush(
                new Promotion(program, year, "P1", "Promo 1", null, null));
        ProgramLevel level = programLevelRepository.saveAndFlush(
                new ProgramLevel(program, "N1", "Niveau 1", (short) 1));
        long siteId = insertSite(shortCode());
        classGroupRepository.saveAndFlush(new ClassGroup(promotion, level, siteId, "C1", "Classe 1", 24));
        entityManager.flush();
        entityManager.clear();

        // FK RESTRICT `fk_class_group_site` : la suppression physique du
        // site est refusée par MySQL (pas de relation JPA, requête native).
        assertThrows(Exception.class, () -> {
            entityManager.getEntityManager()
                    .createNativeQuery("DELETE FROM site WHERE id = :id")
                    .setParameter("id", siteId)
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    void academicYearRejectsInvertedPeriodAtDatabaseLevel() {
        AcademicYear inverted = new AcademicYear(shortCode(), "période inversée",
                LocalDate.of(2027, 9, 1), LocalDate.of(2026, 8, 31));
        // CHECK (end_date > start_date) : MySQL renvoie une violation de
        // contrainte CHECK, traduite par Spring en DataAccessException
        // (DataIntegrityViolationException ou JpaSystemException selon le
        // code d'erreur SQL).
        assertThrows(DataAccessException.class,
                () -> academicYearRepository.saveAndFlush(inverted));
    }

    @Test
    void classGroupRejectsNonPositiveCapacityAtDatabaseLevel() {
        Program program = programRepository.saveAndFlush(program(shortCode()));
        AcademicYear year = academicYearRepository.saveAndFlush(year(shortCode()));
        Promotion promotion = promotionRepository.saveAndFlush(
                new Promotion(program, year, "P1", "Promo 1", null, null));
        ProgramLevel level = programLevelRepository.saveAndFlush(
                new ProgramLevel(program, "N1", "Niveau 1", (short) 1));
        long siteId = insertSite(shortCode());

        // CHECK (capacity IS NULL OR capacity > 0)
        assertThrows(DataAccessException.class,
                () -> classGroupRepository.saveAndFlush(
                        new ClassGroup(promotion, level, siteId, "C1", "Classe 1", 0)));
    }

    // ------------------------------------------------------------------

    private long insertSite(String code) {
        EntityManager em = entityManager.getEntityManager();
        em.createNativeQuery("""
                        INSERT INTO site (public_id, code, name, time_zone_id, status, created_at, updated_at, version)
                        VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, :code, 'Europe/Paris', 'ACTIVE',
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                        """)
                .setParameter("code", code)
                .executeUpdate();
        Object id = em.createNativeQuery("SELECT id FROM site WHERE code = :code")
                .setParameter("code", code)
                .getSingleResult();
        return ((Number) id).longValue();
    }

    private static AcademicYear year(String code) {
        return new AcademicYear(code, code, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31));
    }

    private static Program program(String code) {
        return new Program(code, code, ProgramType.BTS, null);
    }

    private static String shortCode() {
        // <= 30 caractères (contrainte la plus stricte : academic_year.code).
        return "AC-" + UUID.randomUUID().toString().substring(0, 20);
    }
}
