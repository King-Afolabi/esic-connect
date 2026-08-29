package com.esic.connect.alternation.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.alternation.AlternationChangeAction;
import com.esic.connect.alternation.AlternationResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validations métier des affectations de rythme à une classe, isolées des
 * I/O : rythme archivé non affectable, classe inconnue / non affectable,
 * période invalide, chevauchement refusé, adjacence acceptée, collision
 * concurrente sur l'affectation ouverte retraduite en 409, périmètre
 * pédagogique, clôture.
 */
@ExtendWith(MockitoExtension.class)
class ClassWorkStudyPatternServiceTests {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);
    private static final String OPEN_ASSIGNMENT_SQL_MESSAGE =
            "Duplicate entry '42' for key 'class_work_study_pattern.uq_class_work_study_pattern_active_open'";

    @Mock
    private ClassWorkStudyPatternRepository assignmentRepository;
    @Mock
    private WorkStudyPatternService patternService;
    @Mock
    private ClassAssignmentPersister persister;
    @Mock
    private ClassGroupDirectory classGroupDirectory;
    @Mock
    private AcademicScopeDirectory academicScope;
    @Mock
    private AlternationChangePublisher changePublisher;

    private ClassWorkStudyPatternService service() {
        return new ClassWorkStudyPatternService(assignmentRepository, patternService, persister,
                classGroupDirectory, academicScope, changePublisher, FIXED_CLOCK);
    }

    private final UUID classPublicId = UUID.randomUUID();
    private final UUID patternPublicId = UUID.randomUUID();

    private ClassGroupDirectory.ClassGroupRef classRef(boolean open) {
        return new ClassGroupDirectory.ClassGroupRef(42L, classPublicId, "C1", UUID.randomUUID(), "PRG",
                7L, UUID.randomUUID(), "2026-2027", open);
    }

    private WorkStudyPattern pattern(boolean archived) {
        WorkStudyPattern pattern = new WorkStudyPattern("RYT-1", "Rythme", null,
                WorkStudyPatternType.THREE_DAYS_SCHOOL_TWO_DAYS_COMPANY, 1,
                "{\"cycleLengthWeeks\":1,\"schoolWeeks\":[1],\"companyWeeks\":[],"
                        + "\"schoolDays\":[\"MONDAY\"],\"companyDays\":[\"FRIDAY\"]}");
        ReflectionTestUtils.setField(pattern, "publicId", patternPublicId);
        if (archived) {
            pattern.archive("stop", null, Instant.now());
        }
        return pattern;
    }

    private ClassAssignmentRequests.Assign assign(LocalDate from, LocalDate until) {
        return new ClassAssignmentRequests.Assign(classPublicId.toString(), patternPublicId.toString(),
                LocalDate.of(2026, 9, 1), from, until);
    }

    @Test
    void assignRejectsArchivedPattern() {
        when(patternService.require(patternPublicId)).thenReturn(pattern(true));
        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.PATTERN_ARCHIVED);
    }

    @Test
    void assignRejectsUnknownClass() {
        when(patternService.require(patternPublicId)).thenReturn(pattern(false));
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.CLASS_GROUP_NOT_FOUND);
    }

    @Test
    void assignRejectsArchivedClassChain() {
        when(patternService.require(patternPublicId)).thenReturn(pattern(false));
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(Optional.of(classRef(false)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.CLASS_NOT_ASSIGNABLE);
    }

    @Test
    void assignRejectsScopedCallerOutsideItsPerimeter() {
        when(patternService.require(patternPublicId)).thenReturn(pattern(false));
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(Optional.of(classRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(false);
        when(academicScope.isClassInScope(classPublicId)).thenReturn(false);
        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.OUT_OF_SCOPE);
    }

    @Test
    void assignRejectsInvalidPeriod() {
        stubValidPatternAndClass();
        assertThatThrownBy(() -> service().assign(
                assign(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_PERIOD);
    }

    @Test
    void assignRejectsOverlap() {
        stubValidPatternAndClass();
        when(assignmentRepository.findActiveOverlapping(eq(42L), any(), any()))
                .thenReturn(List.of(new ClassWorkStudyPattern(42L, pattern(false), LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 1), null)));
        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.ASSIGNMENT_OVERLAP);
        verify(persister, never()).persist(any());
    }

    @Test
    void assignSucceedsWhenNoOverlapAndPublishesAssignedEvent() {
        stubValidPatternAndClass();
        when(assignmentRepository.findActiveOverlapping(eq(42L), any(), any())).thenReturn(List.of());
        when(persister.persist(any(ClassWorkStudyPattern.class))).thenAnswer(inv -> {
            ClassWorkStudyPattern a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "publicId", UUID.randomUUID());
            return a;
        });
        when(changePublisher.actorId("sub")).thenReturn(5L);

        ClassAssignmentResponse response = service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub");

        assertThat(response.status()).isEqualTo(ClassPatternStatus.ACTIVE);
        assertThat(response.classGroupPublicId()).isEqualTo(classPublicId);
        verify(changePublisher).publish(eq(AlternationResourceType.CLASS_WORK_STUDY_PATTERN), any(),
                eq(AlternationChangeAction.ASSIGNED), eq(5L), any());
    }

    @Test
    void assignTranslatesOpenAssignmentCollisionInto409() {
        stubValidPatternAndClass();
        when(assignmentRepository.findActiveOverlapping(eq(42L), any(), any())).thenReturn(List.of());
        when(changePublisher.actorId(any())).thenReturn(5L);
        when(persister.persist(any())).thenThrow(new DataIntegrityViolationException("insert",
                new org.hibernate.exception.ConstraintViolationException(OPEN_ASSIGNMENT_SQL_MESSAGE,
                        new java.sql.SQLException(), "uq_class_work_study_pattern_active_open")));

        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.OPEN_ASSIGNMENT_EXISTS);
    }

    @Test
    void assignRethrowsUnrelatedIntegrityViolation() {
        stubValidPatternAndClass();
        when(assignmentRepository.findActiveOverlapping(eq(42L), any(), any())).thenReturn(List.of());
        when(changePublisher.actorId(any())).thenReturn(5L);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException("insert",
                new org.hibernate.exception.ConstraintViolationException("foreign key fails",
                        new java.sql.SQLException(), "fk_class_work_study_pattern_pattern"));
        when(persister.persist(any())).thenThrow(fkViolation);

        assertThatThrownBy(() -> service().assign(assign(LocalDate.of(2026, 9, 1), null), "sub"))
                .isSameAs(fkViolation);
    }

    @Test
    void closeRejectsAlreadyClosedAssignment() {
        ClassWorkStudyPattern assignment = new ClassWorkStudyPattern(42L, pattern(false),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null);
        assignment.close("done", null, LocalDate.of(2026, 12, 31));
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(assignment, "publicId", id);
        when(assignmentRepository.findByPublicId(id)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service().close(id, new ClassAssignmentRequests.Close("x", null), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.ASSIGNMENT_ALREADY_CLOSED);
    }

    @Test
    void closeRejectsEffectiveDateBeforeValidFrom() {
        ClassWorkStudyPattern assignment = new ClassWorkStudyPattern(42L, pattern(false),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(assignment, "publicId", id);
        when(assignmentRepository.findByPublicId(id)).thenReturn(Optional.of(assignment));
        when(classGroupDirectory.findByInternalId(42L)).thenReturn(Optional.of(classRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);

        assertThatThrownBy(() -> service().close(id,
                new ClassAssignmentRequests.Close("x", LocalDate.of(2026, 1, 1)), "sub"))
                .extracting(ex -> ((AlternationException) ex).kind())
                .isEqualTo(AlternationException.Kind.INVALID_PERIOD);
    }

    @Test
    void closeUsesInjectedClockByDefaultAndPublishesClosedEvent() {
        ClassWorkStudyPattern assignment = new ClassWorkStudyPattern(42L, pattern(false),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(assignment, "publicId", id);
        when(assignmentRepository.findByPublicId(id)).thenReturn(Optional.of(assignment));
        when(classGroupDirectory.findByInternalId(42L)).thenReturn(Optional.of(classRef(true)));
        when(academicScope.hasGlobalScope()).thenReturn(true);
        when(changePublisher.actorId("sub")).thenReturn(3L);

        service().close(id, new ClassAssignmentRequests.Close("réorganisation", null), "sub");

        assertThat(assignment.getStatus()).isEqualTo(ClassPatternStatus.CLOSED);
        assertThat(assignment.getValidUntil()).isEqualTo(LocalDate.of(2026, 6, 15));
        verify(changePublisher).publish(eq(AlternationResourceType.CLASS_WORK_STUDY_PATTERN), eq(id),
                eq(AlternationChangeAction.CLOSED), eq(3L), any());
    }

    private void stubValidPatternAndClass() {
        when(patternService.require(patternPublicId)).thenReturn(pattern(false));
        when(classGroupDirectory.findByPublicId(classPublicId)).thenReturn(Optional.of(classRef(true)));
        lenient().when(academicScope.hasGlobalScope()).thenReturn(true);
        lenient().when(classGroupDirectory.findByInternalId(anyLong())).thenReturn(Optional.of(classRef(true)));
    }
}
