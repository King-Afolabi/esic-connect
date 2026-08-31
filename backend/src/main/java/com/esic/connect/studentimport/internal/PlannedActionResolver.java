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
 * Aucune écriture. Une ligne déjà en {@code ERROR} de champ reste résolue
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

        // 1. Résolution de la classe / année par codes fonctionnels.
        ClassGroupResolution resolution =
                classGroupDirectory.resolveForImport(row.formationCode(), row.classCode(), row.academicYear());
        ClassGroupDirectory.ClassGroupRef classRef = null;
        if (resolution instanceof ClassGroupResolution.Found found) {
            classRef = found.ref();
            if (!academicScopeDirectory.isClassInScope(classRef.publicId())) {
                issues.add(RowIssueDraft.error(StudentImportIssueCodes.CLASS_OUT_OF_SCOPE,
                        "Cette classe n'est pas dans votre périmètre pédagogique.", "class_code", null));
                classRef = null;
            }
        } else {
            issues.add(missIssue((ClassGroupResolution.Miss) resolution));
        }

        // Sans classe exploitable (ou ligne déjà en erreur), aucune action.
        if (classRef == null || rowAlreadyInError || hasError(issues)) {
            return new RowResolution(StudentImportPlannedAction.NONE,
                    classRef != null ? classRef.publicId() : null, null, null, false, issues);
        }

        // 2. Rapprochement du compte par e-mail.
        Optional<StudentAccountProvisioner.ExistingAccountView> account =
                accountProvisioner.findByEmail(row.email());

        if (account.isEmpty()) {
            return resolveNewAccount(row, classRef, issues);
        }
        return resolveExistingAccount(row, classRef, account.get(), issues);
    }

    // ------------------------------------------------------------------

    private RowResolution resolveNewAccount(NormalizedRow row, ClassGroupDirectory.ClassGroupRef classRef,
                                            List<RowIssueDraft> issues) {
        boolean generated = false;
        if (row.studentNumber() == null) {
            generated = true;
            issues.add(RowIssueDraft.info(StudentImportIssueCodes.STUDENT_NUMBER_WILL_BE_GENERATED,
                    "Un numéro étudiant sera attribué automatiquement à la confirmation.", "student_number"));
        } else if (enrollmentProvisioner.studentNumberTaken(row.studentNumber())) {
            issues.add(RowIssueDraft.error(StudentImportIssueCodes.STUDENT_NUMBER_TAKEN,
                    "Ce numéro étudiant est déjà attribué à un autre compte.", "student_number",
                    CsvValueNormalizer.truncateReceivedValue(row.studentNumber())));
        }
        if (hasError(issues)) {
            return new RowResolution(StudentImportPlannedAction.NONE, classRef.publicId(), null, null, false, issues);
        }
        return new RowResolution(StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL,
                classRef.publicId(), null, null, generated, issues);
    }

    private RowResolution resolveExistingAccount(NormalizedRow row, ClassGroupDirectory.ClassGroupRef classRef,
                                                 StudentAccountProvisioner.ExistingAccountView account,
                                                 List<RowIssueDraft> issues) {
        switch (account.status()) {
            case ARCHIVED, LOCKED, SUSPENDED -> {
                issues.add(RowIssueDraft.error(StudentImportIssueCodes.ACCOUNT_NOT_USABLE,
                        "Un compte existe pour cette adresse mais n'est pas exploitable (archivé / verrouillé / suspendu).",
                        "email", null));
                return new RowResolution(StudentImportPlannedAction.NONE, classRef.publicId(),
                        account.publicId(), null, false, issues);
            }
            case PENDING_ACTIVATION, ACTIVE -> {
                // suite ci-dessous
            }
            default -> {
                // exhaustif
            }
        }

        Optional<StudentEnrollmentProvisioner.StudentProfileView> profile =
                enrollmentProvisioner.findProfileByUser(account.publicId());

        // Compte PENDING sans profil : on (ré)émettra l'invitation + créera le profil.
        if (account.status() == StudentAccountProvisioner.StatusView.PENDING_ACTIVATION && profile.isEmpty()) {
            boolean generated = flagStudentNumberForNewProfile(row, issues);
            if (hasError(issues)) {
                return new RowResolution(StudentImportPlannedAction.NONE, classRef.publicId(),
                        account.publicId(), null, false, issues);
            }
            return new RowResolution(StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL,
                    classRef.publicId(), account.publicId(), null, generated, issues);
        }

        // Compte actif (ou pending avec profil) sans profil : profil + inscription.
        if (profile.isEmpty()) {
            boolean generated = flagStudentNumberForNewProfile(row, issues);
            if (hasError(issues)) {
                return new RowResolution(StudentImportPlannedAction.NONE, classRef.publicId(),
                        account.publicId(), null, false, issues);
            }
            return new RowResolution(StudentImportPlannedAction.ENROLL_EXISTING,
                    classRef.publicId(), account.publicId(), null, generated, issues);
        }

        // Compte + profil : on ne recrée jamais le profil ; numéro du fichier divergent = ignoré.
        StudentEnrollmentProvisioner.StudentProfileView existingProfile = profile.get();
        if (row.studentNumber() != null && !row.studentNumber().equalsIgnoreCase(existingProfile.studentNumber())) {
            issues.add(RowIssueDraft.warning(StudentImportIssueCodes.STUDENT_NUMBER_TAKEN,
                    "Le profil existant conserve son numéro étudiant ; celui du fichier est ignoré.",
                    "student_number", CsvValueNormalizer.truncateReceivedValue(row.studentNumber())));
        }

        StudentEnrollmentProvisioner.Situation situation =
                enrollmentProvisioner.describeSituation(existingProfile.publicId(), classRef.publicId());

        return switch (situation.kind()) {
            case OTHER_CLASS_SAME_YEAR -> new RowResolution(StudentImportPlannedAction.TRANSFER_CLASS,
                    classRef.publicId(), account.publicId(), situation.currentEnrollmentPublicId(), false, issues);
            case NONE -> new RowResolution(StudentImportPlannedAction.ENROLL_EXISTING,
                    classRef.publicId(), account.publicId(), null, false, issues);
            case SAME_CLASS -> {
                boolean divergent = contactDivergent(row, account, existingProfile);
                yield new RowResolution(
                        divergent ? StudentImportPlannedAction.UPDATE_PROFILE : StudentImportPlannedAction.NONE,
                        classRef.publicId(), account.publicId(), null, false, issues);
            }
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

    /** Divergence de téléphone / alternance / entreprise entre le fichier et le compte + profil existants. */
    private static boolean contactDivergent(NormalizedRow row, StudentAccountProvisioner.ExistingAccountView account,
                                            StudentEnrollmentProvisioner.StudentProfileView profile) {
        boolean phoneDiff = row.phonePresent() && row.phone() != null
                && !row.phone().equals(account.phone());
        boolean workStudyDiff = row.workStudyPresent() && row.workStudy() != null
                && row.workStudy() != profile.workStudy();
        boolean companyDiff = row.companyName() != null
                && !row.companyName().equals(profile.companyName());
        return phoneDiff || workStudyDiff || companyDiff;
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
     * @param issues                      anomalies produites par la résolution
     */
    record RowResolution(
            StudentImportPlannedAction plannedAction,
            UUID resolvedClassPublicId,
            UUID resolvedUserPublicId,
            UUID resolvedEnrollmentPublicId,
            boolean studentNumberGenerated,
            List<RowIssueDraft> issues) {
    }
}
