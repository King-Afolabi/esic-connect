package com.esic.connect.studentimport.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.academic.ClassGroupDirectory.ClassGroupRef;
import com.esic.connect.academic.ClassGroupDirectory.ClassGroupResolution;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner.Situation;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner.StudentProfileView;
import com.esic.connect.identity.StudentAccountProvisioner;
import com.esic.connect.identity.StudentAccountProvisioner.ExistingAccountView;
import com.esic.connect.identity.StudentAccountProvisioner.StatusView;
import com.esic.connect.studentimport.internal.PlannedActionResolver.RowResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Calcul de {@code planned_action} (rapport §3.3, §14.1) : les situations
 * clés, avec des ports simulés (Mockito). Aucune écriture.
 */
class PlannedActionResolverTests {

    private final ClassGroupDirectory classGroupDirectory = mock(ClassGroupDirectory.class);
    private final AcademicScopeDirectory academicScopeDirectory = mock(AcademicScopeDirectory.class);
    private final StudentAccountProvisioner accountProvisioner = mock(StudentAccountProvisioner.class);
    private final StudentEnrollmentProvisioner enrollmentProvisioner = mock(StudentEnrollmentProvisioner.class);

    private PlannedActionResolver resolver;
    private final UUID classPublicId = UUID.randomUUID();
    private final ClassGroupRef classRef = new ClassGroupRef(1L, classPublicId, "C1", UUID.randomUUID(), "BTS",
            9L, UUID.randomUUID(), "2026-2027", true);

    @BeforeEach
    void setUp() {
        resolver = new PlannedActionResolver(classGroupDirectory, academicScopeDirectory, accountProvisioner,
                enrollmentProvisioner);
        when(classGroupDirectory.resolveForImport(anyString(), anyString(), anyString()))
                .thenReturn(new ClassGroupResolution.Found(classRef, 2026));
        when(academicScopeDirectory.isClassInScope(classPublicId)).thenReturn(true);
    }

    private static NormalizedRow row(String extraHeader, String extraCells) {
        String csv = "last_name,first_name,email,formation_code,class_code,academic_year" + extraHeader + "\n"
                + "Doe,Jane,jane@x.test,BTS,C1,2026-2027" + extraCells + "\n";
        ParsedCsv parsed = CsvParser.parse(csv, 500);
        return CsvRowNormalizer.normalize(parsed, parsed.rows().get(0));
    }

    @Test
    void unknownEmailWithoutStudentNumberPlansCreationAndGeneratesNumber() {
        when(accountProvisioner.findByEmail("jane@x.test")).thenReturn(Optional.empty());
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL);
        assertThat(resolution.studentNumberGenerated()).isTrue();
        assertThat(resolution.resolvedClassPublicId()).isEqualTo(classPublicId);
        assertThat(resolution.issues()).extracting(StudentImportIssueDrafts.RowIssueDraft::code)
                .contains(StudentImportIssueCodes.STUDENT_NUMBER_WILL_BE_GENERATED);
    }

    @Test
    void aProvidedStudentNumberAlreadyTakenIsAnError() {
        when(accountProvisioner.findByEmail(anyString())).thenReturn(Optional.empty());
        when(enrollmentProvisioner.studentNumberTaken("ESIC-9")).thenReturn(true);
        RowResolution resolution = resolver.resolve(row(",student_number", ",ESIC-9"), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.NONE);
        assertThat(resolution.issues()).extracting(StudentImportIssueDrafts.RowIssueDraft::code)
                .contains(StudentImportIssueCodes.STUDENT_NUMBER_TAKEN);
    }

    @Test
    void anArchivedAccountIsNotUsable() {
        when(accountProvisioner.findByEmail(anyString())).thenReturn(Optional.of(new ExistingAccountView(
                UUID.randomUUID(), 5L, StatusView.ARCHIVED, "Jane", "Doe", null, true)));
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.NONE);
        assertThat(resolution.issues()).extracting(StudentImportIssueDrafts.RowIssueDraft::code)
                .contains(StudentImportIssueCodes.ACCOUNT_NOT_USABLE);
    }

    @Test
    void anActiveAccountWithoutProfilePlansEnrollExisting() {
        UUID userId = UUID.randomUUID();
        when(accountProvisioner.findByEmail(anyString())).thenReturn(Optional.of(new ExistingAccountView(
                userId, 5L, StatusView.ACTIVE, "Jane", "Doe", null, true)));
        when(enrollmentProvisioner.findProfileByUser(userId)).thenReturn(Optional.empty());
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.ENROLL_EXISTING);
        assertThat(resolution.studentNumberGenerated()).isTrue();
        assertThat(resolution.resolvedUserPublicId()).isEqualTo(userId);
    }

    @Test
    void anActiveEnrollmentInAnotherClassSameYearPlansTransfer() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UUID currentEnrollmentId = UUID.randomUUID();
        when(accountProvisioner.findByEmail(anyString())).thenReturn(Optional.of(new ExistingAccountView(
                userId, 5L, StatusView.ACTIVE, "Jane", "Doe", null, true)));
        when(enrollmentProvisioner.findProfileByUser(userId)).thenReturn(Optional.of(new StudentProfileView(
                profileId, userId, "ESIC-1", false, null, false)));
        when(enrollmentProvisioner.describeSituation(profileId, classPublicId))
                .thenReturn(new Situation(Situation.Kind.OTHER_CLASS_SAME_YEAR, currentEnrollmentId));
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.TRANSFER_CLASS);
        assertThat(resolution.resolvedEnrollmentPublicId()).isEqualTo(currentEnrollmentId);
    }

    @Test
    void anActiveEnrollmentInTheTargetClassWithoutDivergenceIsNoop() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(accountProvisioner.findByEmail(anyString())).thenReturn(Optional.of(new ExistingAccountView(
                userId, 5L, StatusView.ACTIVE, "Jane", "Doe", null, true)));
        when(enrollmentProvisioner.findProfileByUser(userId)).thenReturn(Optional.of(new StudentProfileView(
                profileId, userId, "ESIC-1", false, null, false)));
        when(enrollmentProvisioner.describeSituation(profileId, classPublicId))
                .thenReturn(new Situation(Situation.Kind.SAME_CLASS, null));
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.NONE);
    }

    @Test
    void anActiveEnrollmentInTheTargetClassWithDivergentAlternationPlansUpdate() {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(accountProvisioner.findByEmail(anyString())).thenReturn(Optional.of(new ExistingAccountView(
                userId, 5L, StatusView.ACTIVE, "Jane", "Doe", null, true)));
        when(enrollmentProvisioner.findProfileByUser(userId)).thenReturn(Optional.of(new StudentProfileView(
                profileId, userId, "ESIC-1", false, null, false)));
        when(enrollmentProvisioner.describeSituation(profileId, classPublicId))
                .thenReturn(new Situation(Situation.Kind.SAME_CLASS, null));
        RowResolution resolution = resolver.resolve(row(",work_study,company_name", ",oui,ACME"), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.UPDATE_PROFILE);
    }

    @Test
    void anUnknownProgramIsAnErrorAndNoAction() {
        when(classGroupDirectory.resolveForImport(anyString(), anyString(), anyString()))
                .thenReturn(ClassGroupResolution.Miss.PROGRAM_UNKNOWN);
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.NONE);
        assertThat(resolution.resolvedClassPublicId()).isNull();
        assertThat(resolution.issues()).extracting(StudentImportIssueDrafts.RowIssueDraft::code)
                .containsExactly(StudentImportIssueCodes.PROGRAM_UNKNOWN);
    }

    @Test
    void aClassOutOfScopeIsAnError() {
        when(academicScopeDirectory.isClassInScope(classPublicId)).thenReturn(false);
        RowResolution resolution = resolver.resolve(row("", ""), false);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.NONE);
        assertThat(resolution.resolvedClassPublicId()).isNull();
        assertThat(resolution.issues()).extracting(StudentImportIssueDrafts.RowIssueDraft::code)
                .containsExactly(StudentImportIssueCodes.CLASS_OUT_OF_SCOPE);
    }

    @Test
    void aRowAlreadyInFieldErrorIsResolvedForReviewButPlannedNone() {
        RowResolution resolution = resolver.resolve(row("", ""), true);
        assertThat(resolution.plannedAction()).isEqualTo(StudentImportPlannedAction.NONE);
        assertThat(resolution.resolvedClassPublicId()).isEqualTo(classPublicId);
    }
}
