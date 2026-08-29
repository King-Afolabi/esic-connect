package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicChangeAction;
import com.esic.connect.academic.AcademicResourceType;
import com.esic.connect.organization.SiteDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validations métier du référentiel académique, isolées des I/O : période
 * incohérente, période de promotion hors année, unicité, type de
 * formation, niveau étranger à la formation, enfants actifs, tri hors
 * liste blanche. Aligné sur
 * {@code organization.internal.OrganizationServiceTests}.
 */
@ExtendWith(MockitoExtension.class)
class AcademicServiceTests {

    @Mock
    private AcademicYearRepository academicYearRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private ProgramLevelRepository programLevelRepository;
    @Mock
    private PromotionRepository promotionRepository;
    @Mock
    private ClassGroupRepository classGroupRepository;
    @Mock
    private AcademicScopeGuard scopeGuard;
    @Mock
    private SiteDirectory siteDirectory;
    @Mock
    private AcademicChangePublisher changePublisher;

    private AcademicYearService academicYearService() {
        return new AcademicYearService(academicYearRepository, promotionRepository, changePublisher);
    }

    private ProgramService programService() {
        return new ProgramService(programRepository, programLevelRepository, promotionRepository,
                scopeGuard, changePublisher);
    }

    private PromotionService promotionService() {
        return new PromotionService(promotionRepository, programRepository, academicYearRepository,
                classGroupRepository, scopeGuard, changePublisher);
    }

    private ProgramLevelService programLevelService() {
        return new ProgramLevelService(programLevelRepository, programRepository, classGroupRepository,
                scopeGuard, changePublisher);
    }

    private ClassGroupService classGroupService() {
        return new ClassGroupService(classGroupRepository, promotionRepository, programLevelRepository,
                siteDirectory, scopeGuard, changePublisher);
    }

    // ------------------------------------------------------------------
    // AcademicYear
    // ------------------------------------------------------------------

    @Test
    void createAcademicYearRejectsInvertedPeriod() {
        AcademicYearRequests.Create request = new AcademicYearRequests.Create(
                "2026-2027", "2026-2027", LocalDate.of(2027, 8, 31), LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> academicYearService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.INVALID_PERIOD);
    }

    @Test
    void createAcademicYearRejectsDuplicateCode() {
        when(academicYearRepository.existsByCode("2026-2027")).thenReturn(true);
        AcademicYearRequests.Create request = new AcademicYearRequests.Create(
                "2026-2027", "2026-2027", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31));
        assertThatThrownBy(() -> academicYearService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.DUPLICATE_CODE);
    }

    @Test
    void listAcademicYearRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> academicYearService().list(null, null, 0, 20, "password,asc"))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.INVALID_SORT);
    }

    @Test
    void listAcademicYearRejectsInvalidSortDirection() {
        assertThatThrownBy(() -> academicYearService().list(null, null, 0, 20, "code,upwards"))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.INVALID_SORT);
    }

    @Test
    void archiveAcademicYearRefusedWhileActivePromotionsRemain() {
        AcademicYear year = academicYear(10L, false);
        when(academicYearRepository.findByPublicId(year.getPublicId())).thenReturn(Optional.of(year));
        when(promotionRepository.existsByAcademicYearIdAndStatus(10L, AcademicStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> academicYearService().archive(year.getPublicId(), "clôture", null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
    }

    @Test
    void updateAcademicYearRefusedWhenNewPeriodExcludesExistingPromotion() {
        AcademicYear year = academicYear(10L, false);
        when(academicYearRepository.findByPublicId(year.getPublicId())).thenReturn(Optional.of(year));
        // Une promotion démarre avant la nouvelle date de début proposée.
        when(promotionRepository.existsByAcademicYearIdAndStartDateBefore(eq(10L), any())).thenReturn(true);

        AcademicYearRequests.Update request = new AcademicYearRequests.Update(
                "2026-2027", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 8, 31));
        assertThatThrownBy(() -> academicYearService().update(year.getPublicId(), request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ACADEMIC_YEAR_PERIOD_CONFLICT);
    }

    // ------------------------------------------------------------------
    // Program
    // ------------------------------------------------------------------

    @Test
    void createProgramRejectsUnknownType() {
        ProgramRequests.Create request = new ProgramRequests.Create("BTS-SIO", "BTS SIO", "LICENCE", null);
        assertThatThrownBy(() -> programService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.INVALID_PROGRAM_TYPE);
    }

    @Test
    void archiveProgramRefusedWhileActiveChildrenRemain() {
        Program program = program(5L, false);
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(programLevelRepository.existsByProgramIdAndStatus(5L, AcademicStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> programService().archive(program.getPublicId(), "fin", null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
    }

    @Test
    void archiveProgramRefusedWhileActivePromotionRemainsEvenWithoutLevels() {
        Program program = program(5L, false);
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(programLevelRepository.existsByProgramIdAndStatus(5L, AcademicStatus.ACTIVE)).thenReturn(false);
        when(promotionRepository.existsByProgramIdAndStatus(5L, AcademicStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> programService().archive(program.getPublicId(), "fin", null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
    }

    @Test
    void archiveProgramLevelRefusedWhileActiveClassRemains() {
        ProgramLevel level = level(program(1L, false), 30L);
        when(programLevelRepository.findByPublicId(level.getPublicId())).thenReturn(Optional.of(level));
        when(classGroupRepository.existsByProgramLevelIdAndStatus(30L, AcademicStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> programLevelService().archive(level.getPublicId(), "révision", null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
    }

    // ------------------------------------------------------------------
    // Promotion
    // ------------------------------------------------------------------

    @Test
    void createPromotionRejectsPeriodOutsideAcademicYear() {
        Program program = program(1L, false);
        AcademicYear year = academicYear(2L, false); // 2026-09-01 .. 2027-08-31
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(academicYearRepository.findByPublicId(year.getPublicId())).thenReturn(Optional.of(year));

        PromotionRequests.Create request = new PromotionRequests.Create(
                program.getPublicId().toString(), year.getPublicId().toString(), "P1", "Promo 1",
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 6, 30));
        assertThatThrownBy(() -> promotionService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.PROMOTION_PERIOD_OUT_OF_YEAR);
    }

    @Test
    void createPromotionRejectedUnderArchivedProgram() {
        Program program = program(1L, true);
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));

        PromotionRequests.Create request = new PromotionRequests.Create(
                program.getPublicId().toString(), UUID.randomUUID().toString(), "P1", "Promo 1", null, null);
        assertThatThrownBy(() -> promotionService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void archivePromotionRefusedWhileActiveClassRemains() {
        Promotion promotion = promotion(program(1L, false), academicYear(2L, false), 40L);
        when(promotionRepository.findByPublicId(promotion.getPublicId())).thenReturn(Optional.of(promotion));
        when(classGroupRepository.existsByPromotionIdAndStatus(40L, AcademicStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> promotionService().archive(promotion.getPublicId(), "gel", null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.HAS_ACTIVE_CHILDREN);
    }

    @Test
    void restorePromotionRefusedWhenProgramArchived() {
        Promotion promotion = archivedPromotion(program(1L, true), academicYear(2L, false), 41L);
        when(promotionRepository.findByPublicId(promotion.getPublicId())).thenReturn(Optional.of(promotion));

        assertThatThrownBy(() -> promotionService().restore(promotion.getPublicId(), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void restorePromotionRefusedWhenAcademicYearArchived() {
        Promotion promotion = archivedPromotion(program(1L, false), academicYear(2L, true), 42L);
        when(promotionRepository.findByPublicId(promotion.getPublicId())).thenReturn(Optional.of(promotion));

        assertThatThrownBy(() -> promotionService().restore(promotion.getPublicId(), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void restorePromotionSucceedsAndIsAuditedWhenParentsActive() {
        Promotion promotion = archivedPromotion(program(1L, false), academicYear(2L, false), 43L);
        when(promotionRepository.findByPublicId(promotion.getPublicId())).thenReturn(Optional.of(promotion));
        lenient().when(changePublisher.actorId(any())).thenReturn(9L);

        promotionService().restore(promotion.getPublicId(), "subject");

        assertThat(promotion.isArchived()).isFalse();
        verify(changePublisher).publish(eq(AcademicResourceType.PROMOTION), eq(promotion.getPublicId()),
                eq(AcademicChangeAction.RESTORED), eq(9L), any());
    }

    // ------------------------------------------------------------------
    // ClassGroup
    // ------------------------------------------------------------------

    @Test
    void createClassGroupRejectsLevelFromAnotherProgram() {
        Program programA = program(1L, false);
        Program programB = program(2L, false);
        AcademicYear year = academicYear(3L, false);
        Promotion promotion = new Promotion(programA, year, "P1", "Promo 1", null, null);
        ReflectionTestUtils.setField(promotion, "id", 100L);
        ReflectionTestUtils.setField(promotion, "publicId", UUID.randomUUID());
        ProgramLevel foreignLevel = new ProgramLevel(programB, "N1", "Niveau 1", (short) 1);
        ReflectionTestUtils.setField(foreignLevel, "id", 200L);
        ReflectionTestUtils.setField(foreignLevel, "publicId", UUID.randomUUID());

        when(promotionRepository.findByPublicId(promotion.getPublicId())).thenReturn(Optional.of(promotion));
        when(programLevelRepository.findByPublicId(foreignLevel.getPublicId()))
                .thenReturn(Optional.of(foreignLevel));

        ClassGroupRequests.Create request = new ClassGroupRequests.Create(
                promotion.getPublicId().toString(), foreignLevel.getPublicId().toString(),
                UUID.randomUUID().toString(), "C1", "Classe 1", 24);
        assertThatThrownBy(() -> classGroupService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.PROGRAM_LEVEL_MISMATCH);
    }

    @Test
    void createClassGroupRejectsUnknownSite() {
        Program program = program(1L, false);
        AcademicYear year = academicYear(2L, false);
        Promotion promotion = new Promotion(program, year, "P1", "Promo 1", null, null);
        ReflectionTestUtils.setField(promotion, "id", 100L);
        ReflectionTestUtils.setField(promotion, "publicId", UUID.randomUUID());
        ProgramLevel level = new ProgramLevel(program, "N1", "Niveau 1", (short) 1);
        ReflectionTestUtils.setField(level, "id", 200L);
        ReflectionTestUtils.setField(level, "publicId", UUID.randomUUID());
        UUID siteId = UUID.randomUUID();

        when(promotionRepository.findByPublicId(promotion.getPublicId())).thenReturn(Optional.of(promotion));
        when(programLevelRepository.findByPublicId(level.getPublicId())).thenReturn(Optional.of(level));
        when(siteDirectory.findByPublicId(siteId)).thenReturn(Optional.empty());
        lenient().when(changePublisher.actorId(any())).thenReturn(null);

        ClassGroupRequests.Create request = new ClassGroupRequests.Create(
                promotion.getPublicId().toString(), level.getPublicId().toString(),
                siteId.toString(), "C1", "Classe 1", 24);
        assertThatThrownBy(() -> classGroupService().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.SITE_NOT_FOUND);
    }

    @Test
    void restoreClassGroupRefusedWhenAcademicYearArchived() {
        Program program = program(1L, false);
        Promotion promotion = promotion(program, academicYear(2L, true), 100L);
        ProgramLevel level = level(program, 200L);
        ClassGroup classGroup = archivedClassGroup(promotion, level, 7L);
        when(classGroupRepository.findByPublicId(classGroup.getPublicId())).thenReturn(Optional.of(classGroup));

        assertThatThrownBy(() -> classGroupService().restore(classGroup.getPublicId(), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void restoreClassGroupRefusedWhenProgramOfPromotionArchived() {
        Program program = program(1L, true);
        Promotion promotion = promotion(program, academicYear(2L, false), 100L);
        ProgramLevel level = level(program, 200L);
        ClassGroup classGroup = archivedClassGroup(promotion, level, 7L);
        when(classGroupRepository.findByPublicId(classGroup.getPublicId())).thenReturn(Optional.of(classGroup));

        assertThatThrownBy(() -> classGroupService().restore(classGroup.getPublicId(), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void restoreClassGroupRefusedWhenSiteArchived() {
        Program program = program(1L, false);
        Promotion promotion = promotion(program, academicYear(2L, false), 100L);
        ProgramLevel level = level(program, 200L);
        ClassGroup classGroup = archivedClassGroup(promotion, level, 7L);
        when(classGroupRepository.findByPublicId(classGroup.getPublicId())).thenReturn(Optional.of(classGroup));
        when(siteDirectory.findByInternalId(7L))
                .thenReturn(Optional.of(new SiteDirectory.SiteRef(7L, UUID.randomUUID(), true)));

        assertThatThrownBy(() -> classGroupService().restore(classGroup.getPublicId(), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void restoreClassGroupRefusedWhenSiteMissing() {
        Program program = program(1L, false);
        Promotion promotion = promotion(program, academicYear(2L, false), 100L);
        ProgramLevel level = level(program, 200L);
        ClassGroup classGroup = archivedClassGroup(promotion, level, 7L);
        when(classGroupRepository.findByPublicId(classGroup.getPublicId())).thenReturn(Optional.of(classGroup));
        when(siteDirectory.findByInternalId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classGroupService().restore(classGroup.getPublicId(), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.SITE_NOT_FOUND);
    }

    // ------------------------------------------------------------------

    private static AcademicYear academicYear(long id, boolean archived) {
        AcademicYear year = new AcademicYear("2026-2027", "2026-2027",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31));
        ReflectionTestUtils.setField(year, "id", id);
        ReflectionTestUtils.setField(year, "publicId", UUID.randomUUID());
        if (archived) {
            year.archive("test", null, java.time.Instant.now());
        }
        return year;
    }

    private static Program program(long id, boolean archived) {
        Program program = new Program("PRG-" + id, "Programme " + id, ProgramType.BTS, null);
        ReflectionTestUtils.setField(program, "id", id);
        ReflectionTestUtils.setField(program, "publicId", UUID.randomUUID());
        if (archived) {
            program.archive("test", null, Instant.now());
        }
        return program;
    }

    private static ProgramLevel level(Program program, long id) {
        ProgramLevel level = new ProgramLevel(program, "N1", "Niveau 1", (short) 1);
        ReflectionTestUtils.setField(level, "id", id);
        ReflectionTestUtils.setField(level, "publicId", UUID.randomUUID());
        return level;
    }

    private static Promotion promotion(Program program, AcademicYear year, long id) {
        Promotion promotion = new Promotion(program, year, "P1", "Promo 1", null, null);
        ReflectionTestUtils.setField(promotion, "id", id);
        ReflectionTestUtils.setField(promotion, "publicId", UUID.randomUUID());
        return promotion;
    }

    private static Promotion archivedPromotion(Program program, AcademicYear year, long id) {
        Promotion promotion = promotion(program, year, id);
        promotion.archive("test", null, Instant.now());
        return promotion;
    }

    private static ClassGroup archivedClassGroup(Promotion promotion, ProgramLevel level, long siteId) {
        ClassGroup classGroup = new ClassGroup(promotion, level, siteId, "C1", "Classe 1", 24);
        ReflectionTestUtils.setField(classGroup, "id", 500L);
        ReflectionTestUtils.setField(classGroup, "publicId", UUID.randomUUID());
        classGroup.archive("test", null, Instant.now());
        return classGroup;
    }
}
