package com.esic.connect.academic.internal;

import com.esic.connect.academic.AcademicChangeAction;
import com.esic.connect.academic.AcademicResourceType;
import com.esic.connect.identity.UserDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validations métier des affectations de responsable pédagogique, isolées
 * des I/O : formation inconnue / archivée, {@code type} invalide, cible
 * inéligible, unicité du {@code PRIMARY_MANAGER} actif (pré-contrôle
 * <em>et</em> retraduction — ciblée sur la seule contrainte
 * {@code uq_pedagogical_assignment_active_primary} — d'une collision de
 * persistance), validité {@link LocalDate} sur horloge injectée, clôture.
 */
@ExtendWith(MockitoExtension.class)
class PedagogicalAssignmentServiceTests {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    private static final String ACTIVE_PRIMARY_DUPLICATE_MESSAGE =
            "could not execute statement [Duplicate entry '5' for key "
                    + "'pedagogical_assignment.uq_pedagogical_assignment_active_primary']";
    private static final String FOREIGN_KEY_MESSAGE =
            "could not execute statement [Cannot add or update a child row: a foreign key constraint "
                    + "fails (`esic_connect`.`pedagogical_assignment`, CONSTRAINT "
                    + "`fk_pedagogical_assignment_manager`)]";

    @Mock
    private PedagogicalAssignmentRepository assignmentRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private UserDirectory userDirectory;
    @Mock
    private AssignmentPersister assignmentPersister;
    @Mock
    private AcademicChangePublisher changePublisher;

    private PedagogicalAssignmentService service() {
        return new PedagogicalAssignmentService(assignmentRepository, programRepository, userDirectory,
                assignmentPersister, changePublisher, FIXED_CLOCK);
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    @Test
    void createRejectsUnknownProgram() {
        UUID programId = UUID.randomUUID();
        when(programRepository.findByPublicId(programId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create(create(programId, UUID.randomUUID(), "PRIMARY_MANAGER"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.PROGRAM_NOT_FOUND);
    }

    @Test
    void createRejectedUnderArchivedProgram() {
        Program program = program(5L, true);
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        assertThatThrownBy(() -> service().create(
                create(program.getPublicId(), UUID.randomUUID(), "PRIMARY_MANAGER"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ARCHIVED_PARENT);
    }

    @Test
    void createRejectsUnknownType() {
        Program program = program(5L, false);
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        assertThatThrownBy(() -> service().create(
                create(program.getPublicId(), UUID.randomUUID(), "OWNER"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.INVALID_ASSIGNMENT_ROLE);
    }

    @Test
    void createRejectsUnknownTargetAsNotEligible() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().create(create(program.getPublicId(), targetId, "DELEGATE"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE);
    }

    @Test
    void createRejectsArchivedTargetAsNotEligible() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, targetId, true, Set.of("PEDAGOGICAL_MANAGER"))));
        assertThatThrownBy(() -> service().create(create(program.getPublicId(), targetId, "DELEGATE"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE);
    }

    @Test
    void createRejectsTargetWithoutPedagogicalManagerRoleAsNotEligible() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, targetId, false, Set.of("TEACHER"))));
        assertThatThrownBy(() -> service().create(create(program.getPublicId(), targetId, "DELEGATE"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ASSIGNMENT_TARGET_NOT_ELIGIBLE);
    }

    @Test
    void createRejectsValidUntilBeforeValidFrom() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, targetId, false, Set.of("PEDAGOGICAL_MANAGER"))));
        PedagogicalAssignmentRequests.Create request = new PedagogicalAssignmentRequests.Create(
                program.getPublicId().toString(), targetId.toString(), "DELEGATE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 20), null);
        assertThatThrownBy(() -> service().create(request, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ASSIGNMENT_DATE_INVALID);
    }

    @Test
    void createRejectsSecondActivePrimaryManagerViaPrecheck() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, targetId, false, Set.of("PEDAGOGICAL_MANAGER"))));
        when(assignmentRepository.existsByProgramIdAndAssignmentRoleAndStatus(5L,
                PedagogicalAssignmentRole.PRIMARY_MANAGER, PedagogicalAssignmentStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> service().create(create(program.getPublicId(), targetId, "PRIMARY_MANAGER"), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.PRIMARY_MANAGER_ALREADY_ASSIGNED);
        verify(assignmentPersister, never()).persist(any());
    }

    @Test
    void createTranslatesActivePrimaryConstraintCollisionToConflict() {
        stubEligiblePrimaryCreate();
        when(assignmentPersister.persist(any()))
                .thenThrow(new DataIntegrityViolationException(ACTIVE_PRIMARY_DUPLICATE_MESSAGE));

        assertThatThrownBy(() -> service().create(create(UUID.randomUUID(), UUID.randomUUID(),
                "PRIMARY_MANAGER"), "subject"))
                .isInstanceOf(AcademicException.class)
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.PRIMARY_MANAGER_ALREADY_ASSIGNED);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void createRethrowsUnrelatedIntegrityViolationUnchanged() {
        stubEligiblePrimaryCreate();
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException(FOREIGN_KEY_MESSAGE);
        when(assignmentPersister.persist(any())).thenThrow(unrelated);

        assertThatThrownBy(() -> service().create(create(UUID.randomUUID(), UUID.randomUUID(),
                "PRIMARY_MANAGER"), "subject"))
                .isSameAs(unrelated);
        verify(changePublisher, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void createDelegateIsAllowedDespiteExistingPrimaryAndIsAudited() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, targetId, false, Set.of("PEDAGOGICAL_MANAGER"))));
        when(assignmentPersister.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(changePublisher.actorId(any())).thenReturn(3L);

        service().create(new PedagogicalAssignmentRequests.Create(program.getPublicId().toString(),
                targetId.toString(), "delegate", null, null, " remplacement congé "), "subject");

        verify(assignmentRepository, never())
                .existsByProgramIdAndAssignmentRoleAndStatus(any(), any(), any());
        ArgumentCaptor<PedagogicalAssignment> saved = ArgumentCaptor.forClass(PedagogicalAssignment.class);
        verify(assignmentPersister).persist(saved.capture());
        assertThat(saved.getValue().getAssignmentRole()).isEqualTo(PedagogicalAssignmentRole.DELEGATE);
        assertThat(saved.getValue().getStatus()).isEqualTo(PedagogicalAssignmentStatus.ACTIVE);
        assertThat(saved.getValue().getManagerUserId()).isEqualTo(9L);
        assertThat(saved.getValue().getReason()).isEqualTo("remplacement congé");
        assertThat(saved.getValue().getDelegatedById()).isEqualTo(3L);
        verify(changePublisher).publish(eq(AcademicResourceType.PEDAGOGICAL_ASSIGNMENT), any(),
                eq(AcademicChangeAction.CREATED), eq(3L), any());
    }

    @Test
    void createUsesInjectedClockForDefaultValidFrom() {
        Program program = program(5L, false);
        UUID targetId = UUID.randomUUID();
        when(programRepository.findByPublicId(program.getPublicId())).thenReturn(Optional.of(program));
        when(userDirectory.findByPublicId(targetId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, targetId, false, Set.of("PEDAGOGICAL_MANAGER"))));
        when(assignmentPersister.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service().create(create(program.getPublicId(), targetId, "DELEGATE"), "subject");

        ArgumentCaptor<PedagogicalAssignment> saved = ArgumentCaptor.forClass(PedagogicalAssignment.class);
        verify(assignmentPersister).persist(saved.capture());
        assertThat(saved.getValue().getValidFrom()).isEqualTo(TODAY);
    }

    // ------------------------------------------------------------------
    // close
    // ------------------------------------------------------------------

    @Test
    void closeRejectsAlreadyClosedAssignment() {
        PedagogicalAssignment assignment = assignment(PedagogicalAssignmentRole.DELEGATE, LocalDate.of(2026, 1, 1));
        assignment.close("motif initial", 1L, LocalDate.of(2026, 2, 1));
        UUID publicId = stubbedPublicId(assignment);

        assertThatThrownBy(() -> service().close(publicId, "encore", null, null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ASSIGNMENT_ALREADY_CLOSED);
    }

    @Test
    void closeRejectsEffectiveDateBeforeValidFrom() {
        PedagogicalAssignment assignment = assignment(PedagogicalAssignmentRole.PRIMARY_MANAGER,
                LocalDate.of(2026, 9, 1));
        UUID publicId = stubbedPublicId(assignment);

        assertThatThrownBy(() -> service().close(publicId, "trop tôt", LocalDate.of(2026, 8, 15), null))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.ASSIGNMENT_DATE_INVALID);
    }

    @Test
    void closeDefaultsEffectiveDateToInjectedClockAndPublishesEvent() {
        PedagogicalAssignment assignment = assignment(PedagogicalAssignmentRole.PRIMARY_MANAGER,
                LocalDate.of(2026, 5, 1));
        UUID publicId = stubbedPublicId(assignment);
        lenient().when(changePublisher.actorId(any())).thenReturn(7L);

        service().close(publicId, "changement de responsable", null, "subject");

        assertThat(assignment.getStatus()).isEqualTo(PedagogicalAssignmentStatus.CLOSED);
        assertThat(assignment.getValidUntil()).isEqualTo(TODAY);
        assertThat(assignment.getCloseReason()).isEqualTo("changement de responsable");
        verify(changePublisher).publish(eq(AcademicResourceType.PEDAGOGICAL_ASSIGNMENT), eq(publicId),
                eq(AcademicChangeAction.CLOSED), eq(7L), any());
    }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    @Test
    void listRejectsSortFieldOutsideWhitelist() {
        assertThatThrownBy(() -> service().list(null, null, null, null, null, 0, 20, "managerUserId,asc"))
                .extracting(ex -> ((AcademicException) ex).kind())
                .isEqualTo(AcademicException.Kind.INVALID_SORT);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Common stubbing for a create() that reaches persist() with an eligible PRIMARY_MANAGER target. */
    private void stubEligiblePrimaryCreate() {
        when(programRepository.findByPublicId(any())).thenReturn(Optional.of(program(5L, false)));
        when(userDirectory.findByPublicId(any())).thenReturn(Optional.of(
                new UserDirectory.UserRef(9L, UUID.randomUUID(), false, Set.of("PEDAGOGICAL_MANAGER"))));
        when(assignmentRepository.existsByProgramIdAndAssignmentRoleAndStatus(any(), any(), any()))
                .thenReturn(false);
        lenient().when(changePublisher.actorId(any())).thenReturn(3L);
    }

    private UUID stubbedPublicId(PedagogicalAssignment assignment) {
        UUID publicId = UUID.randomUUID();
        ReflectionTestUtils.setField(assignment, "publicId", publicId);
        when(assignmentRepository.findByPublicId(publicId)).thenReturn(Optional.of(assignment));
        return publicId;
    }

    private static PedagogicalAssignment assignment(PedagogicalAssignmentRole role, LocalDate validFrom) {
        return new PedagogicalAssignment(program(5L, false), 9L, role, validFrom, null, null, 1L);
    }

    private static PedagogicalAssignmentRequests.Create create(UUID programId, UUID targetId, String type) {
        return new PedagogicalAssignmentRequests.Create(programId.toString(), targetId.toString(), type,
                null, null, null);
    }

    private static Program program(long id, boolean archived) {
        Program program = new Program("PRG-" + id, "Formation " + id, ProgramType.BTS, null);
        ReflectionTestUtils.setField(program, "id", id);
        ReflectionTestUtils.setField(program, "publicId", UUID.randomUUID());
        if (archived) {
            program.archive("x", 1L, Instant.now());
        }
        return program;
    }
}
