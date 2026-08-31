package com.esic.connect.studentimport.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.academic.ClassGroupDirectory.ClassGroupResolution;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner;
import com.esic.connect.identity.StudentAccountProvisioner;
import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Calcule, pour une ligne normalisée, la classe résolue, le compte
 * rapproché, la situation d'inscription et l'{@code planned_action}
 * (rapport §3.3), en s'appuyant <strong>uniquement sur les ports
 * publics</strong> ({@link ClassGroupDirectory}, {@link AcademicScopeDirectory},
 * {@link StudentAccountProvisioner}, {@link StudentEnrollmentProvisioner}).
 * Aucune écriture. Sert à la simulation <em>et</em> à la re-validation de
 * la confirmation. Une ligne déjà en {@code ERROR} de champ reste résolue
 * pour la revue, mais son action est forcée à {@code NONE}.
 */
@Component
class PlannedActionResolver {

    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final StudentAccountProvisioner accountProvisioner;
    private final StudentEnrollmentProvisioner enrollmentProvisioner;

    PlannedActionResolver(ClassGroupDirectory classGroupDirectory,
                          AcademicScopeDirectory academicScopeDirectory,
                          StudentAccountProvisioner accountProvisioner,
                          StudentEnrollmentProvisioner enrollmentProvisioner) {
        this.classGroupDirectory = classGroupDirectory;
        this.academicScopeDirectory = academicScopeDirectory;
        this.accountProvisioner = accountProvisioner;
        this.enrollmentProvisioner = enrollmentProvisioner;
    }

    RowResolution resolve(NormalizedRow row, boolean rowAlreadyInError) {
        List<RowIssueDraft> issues = new ArrayList<>();

        ClassGroupResolution resolution =
                classGroupDirectory.resolveForImport(row.formationCode(), row.classCode(), row.academicYear());
        ClassGroupDirectory.ClassGroupRef classRef = null;
        int startYear = 0;
        if (resolution instanceof ClassGroupResolution.Found found) {
            classRef = found.ref();
            startYear = found.academicYearStartYear();
            if (!academicScopeDirectory.isClassInScope(classRef.publicId())) {
                issues.add(RowIssueDraft.error(StudentImportIssueCodes.CLASS_OUT_OF_SCOPE,
                        "Cette classe n'est pas dans votre périmètre pédagogique.", "class_code", null));
                classRef = null;
            }
        } else {
            issues.add(missIssue((ClassGroupResolution.Miss) resolution));
        }

        if (classRef == null || rowAlreadyInError || hasError(issues)) {
            return none(classRef, null, null, issues, startYear);
        }

        Optional<StudentAccountProvisioner.ExistingAccountView> account =
                accountProvisioner.findByEmail(row.email());
        if (account.isEmpty()) {
            return resolveNewAccount(row, classRef, startYear, issues);
        }
        return resolveExistingAccount(row, account.get(), classRef, startYear, issues);
    }

    // ------------------------------------------------------------------

    private RowResolution resolveNewAccount(NormalizedRow row, ClassGroupDirectory.ClassGroupRef classRef,
                                            int startYear, List<RowIssueDraft> issues) {
        boolean generated = flagStudentNumberForNewProfile(row, issues);
        if (hasError(issues)) {
            return none(classRef, null, null, issues, startYear);
        }
        return new RowResolution(StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL,
                classRef.publicId(), null, null, generated, false, startYear, issues);
    }

    private RowResolution resolveExistingAccount(NormalizedRow row,
                                                 StudentAccountProvisioner.ExistingAccountView account,
                                                 ClassGroupDirectory.ClassGroupRef classRef, int startYear,
                                                 List<RowIssueDraft> issues) {
        if (account.status() == StudentAccountProvisioner.StatusView.ARCHIVED
                || account.status() == StudentAccountProvisioner.StatusView.LOCKED
                || account.status() == StudentAccountProvisioner.StatusView.SUSPENDED) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.ACCOUNT_NOT_USABLE,
                    "Un compte existe pour cette adresse mais n'est pas exploitable (archivé / verrouillé / suspendu).",
                    "email", null));
            return none(classRef, account.publicId(), null, issues, startYear);
        }

        Optional<StudentEnrollmentProvisioner.StudentProfileView> profile =
                enrollmentProvisioner.findProfileByUser(account.publicId());

        if (profile.isEmpty()) {
            boolean generated = flagStudentNumberForNewProfile(row, issues);
            if (hasError(issues)) {
                return none(classRef, account.publicId(), null, issues, startYear);
            }
            // Compte PENDING sans profil : (ré)émission d'invitation à la confirmation.
            StudentImportPlannedAction action =
                    account.status() == StudentAccountProvisioner.StatusView.PENDING_ACTIVATION
                            ? StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL
                            : StudentImportPlannedAction.ENROLL_EXISTING;
            return new RowResolution(action, classRef.publicId(), account.publicId(), null, generated, false,
                    startYear, issues);
        }

        StudentEnrollmentProvisioner.StudentProfileView existingProfile = profile.get();
        if (row.studentNumber() != null && !row.studentNumber().equalsIgnoreCase(existingProfile.studentNumber())) {
            issues.add(RowIssueDraft.warning(StudentImportIssueCodes.STUDENT_NUMBER_TAKEN,
                    "Le profil existant conserve son numéro étudiant ; celui du fichier est ignoré.",
                    "student_number", CsvValueNormalizer.truncateReceivedValue(row.studentNumber())));
        }
        boolean divergent = contactDivergent(row, account, existingProfile);

        StudentEnrollmentProvisioner.Situation situation =
                enrollmentProvisioner.describeSituation(existingProfile.publicId(), classRef.publicId());
        return switch (situation.kind()) {
            case OTHER_CLASS_SAME_YEAR -> new RowResolution(StudentImportPlannedAction.TRANSFER_CLASS,
                    classRef.publicId(), account.publicId(), situation.currentEnrollmentPublicId(), false,
                    divergent, startYear, issues);
            case NONE -> new RowResolution(StudentImportPlannedAction.ENROLL_EXISTING,
                    classRef.publicId(), account.publicId(), null, false, divergent, startYear, issues);
            case SAME_CLASS -> new RowResolution(
                    divergent ? StudentImportPlannedAction.UPDATE_PROFILE : StudentImportPlannedAction.NONE,
                    classRef.publicId(), account.publicId(), null, false, divergent, startYear, issues);
        };
    }

    private boolean flagStudentNumberForNewProfile(NormalizedRow row, List<RowIssueDraft> issues) {
        if (row.studentNumber() == null) {
            issues.add(RowIssueDraft.info(StudentImportIssueCodes.STUDENT_NUMBER_WILL_BE_GENERATED,
                    "Un numéro étudiant sera attribué automatiquement à la confirmation.", "student_number"));
            return true;
        }
        if (enrollmentProvisioner.studentNumberTaken(row.studentNumber())) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.STUDENT_NUMBER_TAKEN,
                    "Ce numéro étudiant est déjà attribué à un autre compte.", "student_number",
                    CsvValueNormalizer.truncateReceivedValue(row.studentNumber())));
        }
        return false;
    }

    private static boolean contactDivergent(NormalizedRow row, StudentAccountProvisioner.ExistingAccountView account,
                                            StudentEnrollmentProvisioner.StudentProfileView profile) {
        boolean phoneDiff = row.phonePresent() && row.phone() != null && !row.phone().equals(account.phone());
        boolean workStudyDiff = row.workStudyPresent() && row.workStudy() != null
                && row.workStudy() != profile.workStudy();
        boolean companyDiff = row.companyName() != null && !row.companyName().equals(profile.companyName());
        return phoneDiff || workStudyDiff || companyDiff;
    }

    private static RowResolution none(ClassGroupDirectory.ClassGroupRef classRef, UUID userPublicId,
                                     UUID enrollmentPublicId, List<RowIssueDraft> issues, int startYear) {
        return new RowResolution(StudentImportPlannedAction.NONE,
                classRef != null ? classRef.publicId() : null, userPublicId, enrollmentPublicId, false, false,
                startYear, issues);
    }

    private static RowIssueDraft missIssue(ClassGroupResolution.Miss miss) {
        return switch (miss) {
            case PROGRAM_UNKNOWN -> RowIssueDraft.error(StudentImportIssueCodes.PROGRAM_UNKNOWN,
                    "Aucune formation ne porte ce code.", "formation_code", null);
            case ACADEMIC_YEAR_UNKNOWN -> RowIssueDraft.error(StudentImportIssueCodes.ACADEMIC_YEAR_UNKNOWN,
                    "Aucune année scolaire ne porte ce code.", "academic_year", null);
            case CLASS_UNKNOWN -> RowIssueDraft.error(StudentImportIssueCodes.CLASS_UNKNOWN,
                    "Aucune classe ne porte ce code.", "class_code", null);
            case CLASS_NOT_IN_PROGRAM -> RowIssueDraft.error(StudentImportIssueCodes.CLASS_NOT_IN_PROGRAM,
                    "Cette classe n'appartient pas à la formation indiquée.", "class_code", null);
            case CLASS_NOT_IN_YEAR -> RowIssueDraft.error(StudentImportIssueCodes.CLASS_NOT_IN_YEAR,
                    "Cette classe n'est pas rattachée à l'année scolaire indiquée.", "class_code", null);
            case CHAIN_ARCHIVED -> RowIssueDraft.error(StudentImportIssueCodes.CHAIN_ARCHIVED,
                    "La classe visée ou un élément parent (promotion, formation, année) est archivé.", "class_code", null);
        };
    }

    private static boolean hasError(List<RowIssueDraft> issues) {
        return issues.stream().anyMatch(RowIssueDraft::isError);
    }

    /**
     * @param plannedAction               action calculée
     * @param resolvedClassPublicId       classe résolue ({@code null} si non résolue)
     * @param resolvedUserPublicId        compte rapproché ({@code null} si aucun)
     * @param resolvedEnrollmentPublicId  inscription courante (pour {@code TRANSFER_CLASS})
     * @param studentNumberGenerated      {@code true} si le numéro sera généré à la confirmation
     * @param contactDivergent            téléphone / alternance / entreprise divergents d'un profil existant
     * @param academicYearStartYear       année civile de début (pour la génération de numéro)
     * @param issues                      anomalies produites par la résolution
     */
    record RowResolution(
            StudentImportPlannedAction plannedAction,
            UUID resolvedClassPublicId,
            UUID resolvedUserPublicId,
            UUID resolvedEnrollmentPublicId,
            boolean studentNumberGenerated,
            boolean contactDivergent,
            int academicYearStartYear,
            List<RowIssueDraft> issues) {
    }
}
