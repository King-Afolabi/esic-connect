package com.esic.connect.academic.internal;

import com.esic.connect.academic.ClassGroupDirectory.ClassGroupResolution;
import com.esic.connect.shared.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ClassGroupDirectory.resolveForImport} (rapport §4.3, §14.2) :
 * chaque {@code Miss.*} et le cas {@code Found} avec l'année civile de
 * début, sur une base MySQL réelle.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, DefaultClassGroupDirectory.class})
class ClassGroupResolveForImportTests {

    @Autowired
    private DefaultClassGroupDirectory directory;
    @Autowired
    private TestEntityManager entityManager;

    private long siteId;

    @BeforeEach
    void seedSite() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO site (public_id, code, name, time_zone_id, status, created_at, updated_at, version)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), :code, 'Campus', 'Europe/Paris', 'ACTIVE',
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """)
                .setParameter("code", "SITE-" + suffix)
                .executeUpdate();
        siteId = ((Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT id FROM site WHERE code = :code")
                .setParameter("code", "SITE-" + suffix)
                .getSingleResult()).longValue();
    }

    private record Chain(Program program, AcademicYear year, Promotion promotion, ClassGroup classGroup) {
    }

    private Chain seedChain(String programCode, String yearCode, String classCode,
                            LocalDate yearStart, boolean archiveClass) {
        Program program = persist(new Program(programCode, "BTS SIO", ProgramType.BTS, null));
        AcademicYear year = persist(new AcademicYear(yearCode, "AY", yearStart, yearStart.plusYears(1).minusDays(1)));
        ProgramLevel level = persist(new ProgramLevel(program, "N1-" + programCode, "BTS 1", (short) 1));
        Promotion promotion = persist(new Promotion(program, year, "P-" + programCode, "Promo", null, null));
        ClassGroup classGroup = new ClassGroup(promotion, level, siteId, classCode, "Classe", 30);
        if (archiveClass) {
            classGroup.archive("test", null, Instant.now());
        }
        classGroup = persist(classGroup);
        entityManager.flush();
        entityManager.clear();
        return new Chain(program, year, promotion, classGroup);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Test
    void resolvesAClassByFunctionalCodesAndReturnsTheStartYear() {
        seedChain("PRG1", "2026-2027", "C1", LocalDate.of(2026, 9, 1), false);
        ClassGroupResolution resolution = directory.resolveForImport("prg1", "c1", "2026-2027");
        assertThat(resolution).isInstanceOf(ClassGroupResolution.Found.class);
        ClassGroupResolution.Found found = (ClassGroupResolution.Found) resolution;
        assertThat(found.ref().code()).isEqualTo("C1");
        assertThat(found.ref().programCode()).isEqualTo("PRG1");
        assertThat(found.academicYearStartYear()).isEqualTo(2026);
    }

    @Test
    void unknownProgram() {
        assertThat(directory.resolveForImport("NOPE", "C1", "2026-2027"))
                .isEqualTo(ClassGroupResolution.Miss.PROGRAM_UNKNOWN);
    }

    @Test
    void unknownAcademicYear() {
        seedChain("PRG2", "2026-2027", "C1", LocalDate.of(2026, 9, 1), false);
        assertThat(directory.resolveForImport("PRG2", "C1", "9999-0000"))
                .isEqualTo(ClassGroupResolution.Miss.ACADEMIC_YEAR_UNKNOWN);
    }

    @Test
    void unknownClass() {
        seedChain("PRG3", "2026-2028", "C1", LocalDate.of(2026, 9, 1), false);
        assertThat(directory.resolveForImport("PRG3", "ZZZ", "2026-2028"))
                .isEqualTo(ClassGroupResolution.Miss.CLASS_UNKNOWN);
    }

    @Test
    void classNotInProgram() {
        seedChain("PRG4A", "2026-2029", "SHARED", LocalDate.of(2026, 9, 1), false);
        persist(new Program("PRG4B", "Autre", ProgramType.BTS, null));
        entityManager.flush();
        assertThat(directory.resolveForImport("PRG4B", "SHARED", "2026-2029"))
                .isEqualTo(ClassGroupResolution.Miss.CLASS_NOT_IN_PROGRAM);
    }

    @Test
    void classNotInYear() {
        seedChain("PRG5", "2026-2030", "C5", LocalDate.of(2026, 9, 1), false);
        persist(new AcademicYear("2030-2031", "AY2", LocalDate.of(2030, 9, 1), LocalDate.of(2031, 8, 31)));
        entityManager.flush();
        assertThat(directory.resolveForImport("PRG5", "C5", "2030-2031"))
                .isEqualTo(ClassGroupResolution.Miss.CLASS_NOT_IN_YEAR);
    }

    @Test
    void chainArchived() {
        seedChain("PRG6", "2026-2032", "C6", LocalDate.of(2026, 9, 1), true);
        assertThat(directory.resolveForImport("PRG6", "C6", "2026-2032"))
                .isEqualTo(ClassGroupResolution.Miss.CHAIN_ARCHIVED);
    }
}
