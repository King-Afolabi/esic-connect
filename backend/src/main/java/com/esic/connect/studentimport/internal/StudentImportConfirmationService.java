package com.esic.connect.studentimport.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner.Situation;
import com.esic.connect.enrollment.StudentEnrollmentProvisioner.StudentProfileView;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.identity.StudentAccountProvisioner;
import com.esic.connect.identity.StudentAccountProvisioner.NewStudentAccount;
import com.esic.connect.identity.StudentAccountProvisioner.PreparedAccount;
import com.esic.connect.identity.StudentAccountProvisioningException;
import com.esic.connect.studentimport.internal.PlannedActionResolver.RowResolution;
import com.esic.connect.studentimport.internal.StaleRevalidationPersister.RevalidatedRow;
import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 2 de l'import — <strong>confirmation</strong> (rapport §4.4, §11).
 *
 * <p><strong>Une seule transaction</strong> ({@code @Transactional},
 * propagation {@code REQUIRED}) : verrou pessimiste sur le job,
 * re-validation complète, application via les ports
 * {@link StudentAccountProvisioner} / {@link StudentEnrollmentProvisioner}
 * (eux-mêmes {@code REQUIRED}, jamais {@code REQUIRES_NEW}), génération
 * atomique de numéro via {@link StudentNumberAllocator}, passage
 * {@code APPLIED}. Toute exception → <strong>rollback total</strong>
 * (invariant T3) : aucun compte / profil / inscription / rôle /
 * invitation, séquence non consommée, job {@code SIMULATED}. L'e-mail
 * d'invitation ne part qu'après commit
 * ({@code InvitationEmailListener}, {@code AFTER_COMMIT} — invariant T4).
 * Aucun événement d'audit publié à ce checkpoint.
 *
 * <p>Reconfirmation d'un job {@code APPLIED} → {@code 200} + bilan
 * mémorisé + {@code alreadyApplied = true} (invariant T6).
 */
@Service
class StudentImportConfirmationService {

    private final StudentImportJobRepository jobRepository;
    private final StudentImportRowRepository rowRepository;
    private final PlannedActionResolver plannedActionResolver;
    private final StaleRevalidationPersister staleRevalidationPersister;
    private final StudentAccountProvisioner accountProvisioner;
    private final StudentEnrollmentProvisioner enrollmentProvisioner;
    private final StudentNumberAllocator numberAllocator;
    private final CurrentUserResolver currentUserResolver;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final StudentImportProperties properties;
    private final Clock clock;
    private final ObjectProvider<StudentImportConfirmationService> self;

    StudentImportConfirmationService(StudentImportJobRepository jobRepository,
                                     StudentImportRowRepository rowRepository,
                                     PlannedActionResolver plannedActionResolver,
                                     StaleRevalidationPersister staleRevalidationPersister,
                                     StudentAccountProvisioner accountProvisioner,
                                     StudentEnrollmentProvisioner enrollmentProvisioner,
                                     StudentNumberAllocator numberAllocator,
                                     CurrentUserResolver currentUserResolver,
                                     AcademicScopeDirectory academicScopeDirectory,
                                     StudentImportProperties properties,
                                     Clock clock,
                                     ObjectProvider<StudentImportConfirmationService> self) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.plannedActionResolver = plannedActionResolver;
        this.staleRevalidationPersister = staleRevalidationPersister;
        this.accountProvisioner = accountProvisioner;
        this.enrollmentProvisioner = enrollmentProvisioner;
        this.numberAllocator = numberAllocator;
        this.currentUserResolver = currentUserResolver;
        this.academicScopeDirectory = academicScopeDirectory;
        this.properties = properties;
        this.clock = clock;
        this.self = self;
    }

    /**
     * Entrée publique — <strong>non transactionnelle</strong>. Délègue la
     * transaction unique verrouillée à {@link #runConfirmation}. Si la
     * re-validation a invalidé la simulation, cette transaction a
     * <em>commité sans rien appliquer</em> (job toujours {@code SIMULATED},
     * verrou relâché) ; on persiste alors les anomalies rafraîchies dans
     * une transaction propre — plus aucun verrou détenu — puis on signale
     * {@code STALE_SIMULATION}.
     */
    ConfirmationResult confirm(String jobPublicId, String callerSubject) {
        Attempt attempt = self.getObject().runConfirmation(jobPublicId, callerSubject);
        if (attempt.stale()) {
            staleRevalidationPersister.persist(attempt.jobPublicId(), attempt.refreshedRows());
            throw new StudentImportException(StudentImportException.Kind.STALE_SIMULATION);
        }
        return attempt.result();
    }

    @Transactional
    Attempt runConfirmation(String jobPublicId, String callerSubject) {
        Instant now = clock.instant();
        StudentImportJob job = jobRepository
                .findWithLockByPublicId(StudentImportWeb.parseUuid(jobPublicId,
                        StudentImportException.Kind.JOB_NOT_FOUND))
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.JOB_NOT_FOUND));

        // Idempotence : un job déjà appliqué renvoie le bilan mémorisé.
        if (job.getStatus() == StudentImportJobStatus.APPLIED) {
            return Attempt.applied(ConfirmationResult.alreadyApplied(job));
        }
        if (job.getStatus() == StudentImportJobStatus.CANCELLED) {
            throw new StudentImportException(StudentImportException.Kind.JOB_CANCELLED);
        }
        if (job.isExpiredAt(now)) {
            throw new StudentImportException(StudentImportException.Kind.SIMULATION_EXPIRED);
        }
        if (job.getStatus() != StudentImportJobStatus.SIMULATED) {
            throw new StudentImportException(StudentImportException.Kind.NOT_CONFIRMABLE);
        }
        Long actorId = requireCaller(callerSubject);
        if (!academicScopeDirectory.hasGlobalScope() && !job.getRequestedById().equals(actorId)) {
            throw new StudentImportException(StudentImportException.Kind.CONFIRM_FORBIDDEN);
        }
        if (!job.isConfirmable()) {
            throw new StudentImportException(StudentImportException.Kind.NOT_CONFIRMABLE);
        }

        List<StudentImportRow> rows = rowRepository.findByJobIdOrderByRowNumberAsc(job.getId());
        Revalidation revalidation = revalidate(rows);
        if (revalidation.stale()) {
            // La transaction courante va commiter SANS rien appliquer (job intact) ;
            // l'appelant persistera les anomalies rafraîchies hors verrou.
            return Attempt.stale(job.getPublicId(), revalidation.refreshedByRowNumber());
        }

        LocalDate today = LocalDate.now(clock);
        RunTotals totals = new RunTotals();
        Map<String, UUID> handledEmailToUser = new HashMap<>();
        try {
            for (StudentImportRow row : rows) {
                applyRow(row, revalidation.resolutions().get(row.getId()), actorId, today, handledEmailToUser,
                        totals);
            }
        } catch (StudentAccountProvisioningException | DataIntegrityViolationException abandon) {
            // Compte devenu inexploitable, ou course d'unicité (email / inscription active /
            // profil) pendant l'application : la transaction unique est rollback-only,
            // on abandonne tout (invariant T3).
            throw new StudentImportException(StudentImportException.Kind.STALE_SIMULATION);
        }

        job.markApplied(now, actorId, totals.created, totals.updated, totals.transferred, totals.invited,
                totals.ignored);
        jobRepository.save(job);
        return Attempt.applied(ConfirmationResult.applied(job));
    }

    // ------------------------------------------------------------------
    // Re-validation (rapport §4.4 : recalcule anomalies + planned_action)
    // ------------------------------------------------------------------

    private Revalidation revalidate(List<StudentImportRow> rows) {
        Map<Long, RowResolution> resolutions = new HashMap<>();
        Map<Integer, RevalidatedRow> refreshedByRowNumber = new HashMap<>();
        boolean stale = false;

        for (StudentImportRow row : rows) {
            NormalizedRow normalized = NormalizedRow.fromPersistedRow(row);
            List<RowIssueDraft> issues = new ArrayList<>(StudentImportFieldValidator.validate(normalized));
            boolean fieldError = issues.stream().anyMatch(RowIssueDraft::isError);
            RowResolution resolution = plannedActionResolver.resolve(normalized, fieldError);
            issues.addAll(resolution.issues());
            resolutions.put(row.getId(), resolution);

            StudentImportRowStatus status = StudentImportFieldValidator.statusFrom(issues);
            refreshedByRowNumber.put(row.getRowNumber(), new RevalidatedRow(
                    status, resolution.plannedAction(), resolution.resolvedClassPublicId(),
                    resolution.resolvedUserPublicId(), resolution.resolvedEnrollmentPublicId(), issues));
            if (status == StudentImportRowStatus.ERROR) {
                stale = true;
            }
        }
        return new Revalidation(stale, resolutions, refreshedByRowNumber);
    }

    private record Revalidation(boolean stale, Map<Long, RowResolution> resolutions,
                                Map<Integer, RevalidatedRow> refreshedByRowNumber) {
    }

    private record Attempt(boolean stale, ConfirmationResult result, UUID jobPublicId,
                           Map<Integer, RevalidatedRow> refreshedRows) {

        static Attempt applied(ConfirmationResult result) {
            return new Attempt(false, result, null, Map.of());
        }

        static Attempt stale(UUID jobPublicId, Map<Integer, RevalidatedRow> refreshedRows) {
            return new Attempt(true, null, jobPublicId, refreshedRows);
        }
    }

    // ------------------------------------------------------------------
    // Application d'une ligne (dans la transaction unique)
    // ------------------------------------------------------------------

    private void applyRow(StudentImportRow row, RowResolution resolution, Long actorId, LocalDate today,
                          Map<String, UUID> handledEmailToUser, RunTotals totals) {
        if (resolution.plannedAction() == StudentImportPlannedAction.NONE) {
            row.setAppliedOutcome(StudentImportRowOutcome.NOOP);
            totals.ignored++;
            return;
        }

        String email = row.getInputEmail();
        UUID userPublicId = resolution.resolvedUserPublicId();
        boolean invited = false;

        if (resolution.plannedAction() == StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL
                && !handledEmailToUser.containsKey(email)) {
            // Première ligne de cet import portant cet e-mail : création (ou réémission
            // pour un compte déjà PENDING_ACTIVATION) du compte + invitation, dans la
            // transaction unique. Une ligne ultérieure au même e-mail réutilisera ce compte.
            PreparedAccount prepared = accountProvisioner.prepareStudentAccountAndInvitation(
                    new NewStudentAccount(email, row.getInputFirstName(), row.getInputLastName(), row.getInputPhone()),
                    actorId);
            userPublicId = prepared.userPublicId();
            invited = prepared.invitationIssued();
        } else if (handledEmailToUser.containsKey(email)) {
            userPublicId = handledEmailToUser.get(email);
        }
        handledEmailToUser.put(email, userPublicId);

        UUID profilePublicId = ensureProfile(row, resolution, userPublicId, actorId);

        if (resolution.contactDivergent()) {
            enrollmentProvisioner.updateProfileAlternation(profilePublicId,
                    Boolean.TRUE.equals(row.getInputWorkStudy()), row.getInputCompanyName(), actorId);
            if (row.getInputPhone() != null) {
                accountProvisioner.updateStudentPhone(userPublicId, row.getInputPhone(), actorId);
            }
        }

        StudentImportRowOutcome outcome = applyEnrollment(row, resolution, profilePublicId, actorId, today);
        row.setAppliedOutcome(outcome);
        tally(totals, outcome, invited);
    }

    private UUID ensureProfile(StudentImportRow row, RowResolution resolution, UUID userPublicId, Long actorId) {
        Optional<StudentProfileView> existing = enrollmentProvisioner.findProfileByUser(userPublicId);
        if (existing.isPresent()) {
            return existing.get().publicId();
        }
        return provisionProfileWithRetry(row, resolution, userPublicId, actorId);
    }

    private UUID provisionProfileWithRetry(StudentImportRow row, RowResolution resolution, UUID userPublicId,
                                           Long actorId) {
        String number = row.getInputStudentNumber();
        boolean generated = number == null;
        if (generated) {
            // Le numéro est PRÉ-ALLOUÉ puis testé libre AVANT l'INSERT : une collision au flush
            // marquerait la transaction unique rollback-only et interdirait toute nouvelle
            // tentative (invariant T2 : jamais de REQUIRES_NEW sur ce chemin). La nouvelle
            // tentative bornée (§3.2) est donc faite ici, hors persistance.
            int attempt = 0;
            do {
                if (++attempt > properties.numberAllocMaxRetries()) {
                    throw new StudentImportException(StudentImportException.Kind.STUDENT_NUMBER_ALLOC_FAILED);
                }
                number = numberAllocator.allocate(resolution.academicYearStartYear());
            } while (enrollmentProvisioner.studentNumberTaken(number));
        }
        StudentProfileView profile = enrollmentProvisioner.provisionProfile(
                new StudentEnrollmentProvisioner.ProvisionProfile(userPublicId, number,
                        row.getInputBirthDate(), Boolean.TRUE.equals(row.getInputWorkStudy()),
                        row.getInputCompanyName(), generated, actorId));
        row.setStudentNumberGenerated(generated);
        return profile.publicId();
    }

    private StudentImportRowOutcome applyEnrollment(StudentImportRow row, RowResolution resolution,
                                                   UUID profilePublicId, Long actorId, LocalDate today) {
        UUID classPublicId = resolution.resolvedClassPublicId();
        if (resolution.plannedAction() == StudentImportPlannedAction.UPDATE_PROFILE) {
            return StudentImportRowOutcome.UPDATED;
        }
        Situation situation = enrollmentProvisioner.describeSituation(profilePublicId, classPublicId);
        return switch (situation.kind()) {
            case SAME_CLASS -> resolution.contactDivergent()
                    ? StudentImportRowOutcome.UPDATED : StudentImportRowOutcome.NOOP;
            case OTHER_CLASS_SAME_YEAR -> {
                enrollmentProvisioner.provisionTransfer(situation.currentEnrollmentPublicId(), classPublicId,
                        today, "import CSV apprenants", actorId);
                yield StudentImportRowOutcome.TRANSFERRED;
            }
            case NONE -> {
                enrollmentProvisioner.provisionEnrollment(profilePublicId, classPublicId, today, actorId);
                yield resolution.plannedAction() == StudentImportPlannedAction.CREATE_ACCOUNT_AND_ENROLL
                        ? StudentImportRowOutcome.CREATED : StudentImportRowOutcome.ENROLLED;
            }
        };
    }

    private static void tally(RunTotals totals, StudentImportRowOutcome outcome, boolean invited) {
        switch (outcome) {
            case CREATED -> totals.created++;
            case ENROLLED, UPDATED -> totals.updated++;
            case TRANSFERRED -> totals.transferred++;
            case NOOP -> totals.ignored++;
        }
        if (invited) {
            totals.invited++;
        }
    }

    private Long requireCaller(String callerSubject) {
        return currentUserResolver.resolveInternalId(callerSubject)
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.CONFIRM_FORBIDDEN));
    }

    private static final class RunTotals {
        int created;
        int updated;
        int transferred;
        int invited;
        int ignored;
    }

    /**
     * @param jobPublicId    identifiant public du job
     * @param alreadyApplied {@code true} si le job était déjà {@code APPLIED} (reconfirmation)
     * @param created        comptes créés + invités
     * @param updated        inscriptions d'un compte existant + mises à jour de profil
     * @param transferred    changements de classe
     * @param invited        invitations (r)émises
     * @param ignored        lignes sans changement
     */
    record ConfirmationResult(
            UUID jobPublicId,
            boolean alreadyApplied,
            int created,
            int updated,
            int transferred,
            int invited,
            int ignored) {

        static ConfirmationResult applied(StudentImportJob job) {
            return new ConfirmationResult(job.getPublicId(), false, nz(job.getAppliedCreated()),
                    nz(job.getAppliedUpdated()), nz(job.getAppliedTransferred()), nz(job.getAppliedInvited()),
                    nz(job.getAppliedIgnored()));
        }

        static ConfirmationResult alreadyApplied(StudentImportJob job) {
            return new ConfirmationResult(job.getPublicId(), true, nz(job.getAppliedCreated()),
                    nz(job.getAppliedUpdated()), nz(job.getAppliedTransferred()), nz(job.getAppliedInvited()),
                    nz(job.getAppliedIgnored()));
        }

        private static int nz(Integer value) {
            return value == null ? 0 : value;
        }
    }
}
