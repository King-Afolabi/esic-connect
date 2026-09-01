package com.esic.connect.attendance.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.attendance.AttendanceChangeAction;
import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.attendance.JustificationReviewedEvent;
import com.esic.connect.attendance.internal.JustificationAttachmentResponses.Download;
import com.esic.connect.attendance.internal.JustificationAttachmentResponses.Meta;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.CurrentUserResolver;
import com.esic.connect.identity.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Cycle de vie des justificatifs d'absence (V10) — métadonnée métier
 * sans fichier. Dépôt et modification par l'apprenant (tant que
 * {@code PENDING}) ; consultation et examen par les gestionnaires
 * (périmètre pédagogique respecté ; {@code TEACHER} en lecture seule sur
 * ses séances). Un justificatif accepté fait passer la présence
 * {@code ABSENT → EXCUSED_ABSENCE} ; un refus la laisse {@code ABSENT}.
 */
@Service
class AttendanceJustificationService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceJustificationService.class);

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AcademicScopeDirectory academicScope;
    private final CurrentUserResolver currentUserResolver;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceJustificationRepository justificationRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceChangePublisher changePublisher;
    private final JustificationAttachmentStore attachmentStore;
    private final UserDirectory userDirectory;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    AttendanceJustificationService(CourseSessionDirectory courseSessionDirectory,
                                   EnrollmentDirectory enrollmentDirectory,
                                   AcademicScopeDirectory academicScope,
                                   CurrentUserResolver currentUserResolver,
                                   AttendanceRecordRepository recordRepository,
                                   AttendanceJustificationRepository justificationRepository,
                                   AttendanceCorrectionRepository correctionRepository,
                                   AttendanceChangePublisher changePublisher,
                                   JustificationAttachmentStore attachmentStore,
                                   UserDirectory userDirectory,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.academicScope = academicScope;
        this.currentUserResolver = currentUserResolver;
        this.recordRepository = recordRepository;
        this.justificationRepository = justificationRepository;
        this.correctionRepository = correctionRepository;
        this.changePublisher = changePublisher;
        this.attachmentStore = attachmentStore;
        this.userDirectory = userDirectory;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Apprenant
    // ------------------------------------------------------------------

    @Transactional
    JustificationResponse submit(JustificationRequests.Submit request, String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        UUID checkpointPublicId = parseUuid(request.checkpointPublicId(),
                AttendanceException.Kind.CHECKPOINT_NOT_FOUND);
        SessionRef session = courseSessionDirectory.findSessionByCheckpointPublicId(checkpointPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.CHECKPOINT_NOT_FOUND));
        CheckpointRef checkpoint = session.checkpoint(checkpointPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.CHECKPOINT_NOT_FOUND));

        EnrollmentDirectory.EnrollmentRef enrollment = resolveOwnEnrollment(studentSubject, session);

        Instant now = clock.instant();
        AttendanceRecord record = recordRepository
                .findByAttendanceCheckpointIdAndEnrollmentId(checkpoint.internalId(), enrollment.internalId())
                .map(existing -> {
                    if (existing.getStatus() != AttendanceStatus.ABSENT) {
                        throw new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE);
                    }
                    return existing;
                })
                .orElseGet(() -> createAbsence(checkpoint, enrollment, studentId, now));

        if (justificationRepository.existsByAttendanceRecordIdAndStatusNot(
                record.getId(), JustificationStatus.REJECTED)) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_INVALID_STATE);
        }
        AttendanceJustification justification = new AttendanceJustification(record.getId(),
                parseCategory(request.category()), trimToNull(request.externalReference()),
                request.comment().trim(), studentId, now);
        AttendanceJustification saved;
        try {
            saved = justificationRepository.saveAndFlush(justification);
        } catch (DataIntegrityViolationException duplicate) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_INVALID_STATE);
        }
        correctionRepository.save(AttendanceCorrection.justificationEvent(record.getId(),
                AttendanceCorrectionAction.JUSTIFICATION_ADDED, record.getStatus(), record.getStatus(),
                "justificatif déposé (" + saved.getCategory().name() + ")", studentId, now));
        changePublisher.publishJustification(saved.getPublicId(), studentId,
                AttendanceChangeAction.JUSTIFICATION_SUBMITTED,
                "session=" + session.publicId() + ";category=" + saved.getCategory().name());
        return toResponse(saved, session, checkpoint, enrollment, record.getStatus(), false);
    }

    @Transactional
    JustificationResponse amendOwn(String justificationPublicId, JustificationRequests.Amend request,
                                   String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        AttendanceJustification justification = requireJustification(justificationPublicId);
        if (!studentId.equals(justification.getSubmittedById())) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        if (!justification.isPending()) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_INVALID_STATE);
        }
        justification.amend(parseCategory(request.category()), trimToNull(request.externalReference()),
                request.comment().trim());
        justificationRepository.saveAndFlush(justification);
        AttendanceRecord record = recordRepository.findById(justification.getAttendanceRecordId()).orElseThrow();
        correctionRepository.save(AttendanceCorrection.justificationEvent(record.getId(),
                AttendanceCorrectionAction.JUSTIFICATION_UPDATED, record.getStatus(), record.getStatus(),
                "justificatif modifié", studentId, clock.instant()));
        changePublisher.publishJustification(justification.getPublicId(), studentId,
                AttendanceChangeAction.JUSTIFICATION_UPDATED, null);
        return describe(justification, false);
    }

    @Transactional(readOnly = true)
    List<JustificationResponse> listOwn(String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        return justificationRepository.findBySubmittedByIdOrderBySubmittedAtDesc(studentId).stream()
                .map(j -> describe(j, false))
                .toList();
    }

    @Transactional(readOnly = true)
    JustificationResponse getOwn(String justificationPublicId, String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        AttendanceJustification j = requireJustification(justificationPublicId);
        if (!studentId.equals(j.getSubmittedById())) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        return describe(j, false);
    }

    // ------------------------------------------------------------------
    // Gestion
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<JustificationResponse> listForReview(String statusFilter, String callerSubject) {
        List<JustificationStatus> statuses = parseStatusFilter(statusFilter);
        List<JustificationResponse> result = new ArrayList<>();
        for (AttendanceJustification j : justificationRepository.findByStatusInOrderBySubmittedAtAsc(statuses)) {
            JustificationResponse described = describeForStaff(j);
            if (described != null && inReadScope(described, callerSubject)) {
                result.add(described);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    JustificationResponse getForReview(String justificationPublicId, String callerSubject) {
        AttendanceJustification j = requireJustification(justificationPublicId);
        JustificationResponse described = describeForStaff(j);
        if (described == null || !inReadScope(described, callerSubject)) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        return described;
    }

    @Transactional
    JustificationResponse review(String justificationPublicId, JustificationRequests.Review request,
                                 String callerSubject) {
        AttendanceJustification j = requireJustification(justificationPublicId);
        JustificationResponse described = describeForStaff(j);
        if (described == null || !inManageScope(described, callerSubject)) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        if (!j.isPending()) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_INVALID_STATE);
        }
        boolean accept = "ACCEPTED".equalsIgnoreCase(request.decision().trim());
        String decisionReason = request.decisionReason() != null ? request.decisionReason().trim() : null;
        if (!accept && (decisionReason == null || decisionReason.isEmpty())) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_DECISION_REASON_REQUIRED);
        }
        Long reviewerId = requireCaller(callerSubject);
        Instant now = clock.instant();
        AttendanceRecord record = recordRepository.findById(j.getAttendanceRecordId()).orElseThrow();
        AttendanceStatus previous = record.getStatus();
        try {
            if (accept) {
                j.accept(reviewerId, now);
                record.applyCorrection(AttendanceStatus.EXCUSED_ABSENCE, null, record.getComment(), now, reviewerId);
                recordRepository.saveAndFlush(record);
            } else {
                j.reject(reviewerId, now, decisionReason);
                if (record.getStatus() == AttendanceStatus.EXCUSED_ABSENCE) {
                    record.applyCorrection(AttendanceStatus.ABSENT, null, record.getComment(), now, reviewerId);
                    recordRepository.saveAndFlush(record);
                }
            }
            justificationRepository.saveAndFlush(j);
        } catch (ObjectOptimisticLockingFailureException concurrent) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_INVALID_STATE);
        }
        AttendanceStatus after = record.getStatus();
        correctionRepository.save(AttendanceCorrection.justificationEvent(record.getId(),
                AttendanceCorrectionAction.JUSTIFICATION_REVIEWED, previous, after,
                (accept ? "justificatif accepté" : "justificatif refusé : " + decisionReason),
                reviewerId, now));
        changePublisher.publishJustification(j.getPublicId(), reviewerId,
                AttendanceChangeAction.JUSTIFICATION_REVIEWED,
                "decision=" + (accept ? "ACCEPTED" : "REJECTED"));
        // Notification au propriétaire (bloc G1-E) — publiée DANS la
        // transaction ; consommée après commit par `notification`. Le
        // destinataire est porté explicitement (résolu ici, pas de port
        // enrollment / academic requis).
        userDirectory.findByInternalId(j.getSubmittedById())
                .map(UserDirectory.UserRef::publicId)
                .ifPresent(ownerPublicId -> eventPublisher.publishEvent(
                        new JustificationReviewedEvent(j.getPublicId(), ownerPublicId, accept)));
        return describeForStaff(j);
    }

    // ------------------------------------------------------------------
    // Pièces jointes (bloc G1-E ; DEC-G1-008 / DEC-G1-009)
    // ------------------------------------------------------------------

    /**
     * Dépôt d'une pièce jointe par le <strong>propriétaire</strong> du
     * justificatif, tant qu'il est {@code PENDING}. Non transactionnel :
     * la séquence base/fichier avec compensation est portée par
     * {@link JustificationAttachmentStore}.
     *
     * <p>Quand {@link JustificationAttachmentStore#store} revient, la pièce
     * est <strong>durablement stockée</strong> (fichier écrit + ligne
     * {@code STORED} committée). La trace d'audit
     * {@code JUSTIFICATION_ATTACHMENT_STORED} est publiée <em>ensuite</em>,
     * hors transaction. Son échec (listener d'audit synchrone en
     * {@code REQUIRES_NEW}, comme les autres du projet — cf. G1-C.3) ne
     * doit <strong>pas</strong> être remonté à l'appelant comme un échec du
     * dépôt : la pièce est valide et consultable, un rollback n'a plus de
     * sens. L'échec est journalisé (observabilité) ; l'absence de trace est
     * une dette d'audit assumée, non un faux négatif d'API.
     */
    Meta uploadOwnAttachment(String justificationPublicId, String fileName, String declaredType,
                             byte[] content, String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        AttendanceJustification j = requireOwnedPending(justificationPublicId, studentId);
        Meta meta = attachmentStore.store(j.getId(), studentId, fileName, declaredType, content);
        try {
            changePublisher.publishJustification(j.getPublicId(), studentId,
                    AttendanceChangeAction.JUSTIFICATION_ATTACHMENT_STORED, null);
        } catch (RuntimeException auditFailure) {
            log.warn("Pièce jointe de justificatif durablement stockée mais trace d'audit "
                    + "JUSTIFICATION_ATTACHMENT_STORED non écrite (cause={}). La pièce reste valide "
                    + "et téléchargeable ; dette d'audit assumée.", auditFailure.getClass().getSimpleName());
        }
        return meta;
    }

    @Transactional(readOnly = true)
    Meta getOwnAttachment(String justificationPublicId, String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        AttendanceJustification j = requireJustification(justificationPublicId);
        if (!studentId.equals(j.getSubmittedById())) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        return attachmentStore.describeStored(j.getId());
    }

    /** Flux d'une pièce du propriétaire (le contrôleur pose les en-têtes de téléchargement). */
    Download openOwnAttachment(String justificationPublicId, String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        AttendanceJustification j = requireJustification(justificationPublicId);
        if (!studentId.equals(j.getSubmittedById())) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        return attachmentStore.open(j.getId());
    }

    /**
     * Retrait de sa propre pièce par l'apprenant, tant que le justificatif
     * est {@code PENDING} (permet de redéposer un fichier correct). Aucun
     * <em>remplacement</em> direct n'est exposé : c'est retrait puis
     * nouveau dépôt.
     */
    void deleteOwnAttachment(String justificationPublicId, String studentSubject) {
        Long studentId = requireCaller(studentSubject);
        AttendanceJustification j = requireOwnedPending(justificationPublicId, studentId);
        attachmentStore.deleteActive(j.getId());
    }

    @Transactional(readOnly = true)
    Meta getReviewAttachment(String justificationPublicId, String callerSubject) {
        AttendanceJustification j = requireJustification(justificationPublicId);
        JustificationResponse described = describeForStaff(j);
        if (described == null || !inReadScope(described, callerSubject)) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        return attachmentStore.describeStored(j.getId());
    }

    /** Flux d'une pièce pour un examinateur dans son périmètre. */
    Download openReviewAttachment(String justificationPublicId, String callerSubject) {
        AttendanceJustification j = requireJustification(justificationPublicId);
        JustificationResponse described = describeForStaff(j);
        if (described == null || !inReadScope(described, callerSubject)) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        return attachmentStore.open(j.getId());
    }

    private AttendanceJustification requireOwnedPending(String justificationPublicId, Long studentId) {
        AttendanceJustification j = requireJustification(justificationPublicId);
        if (!studentId.equals(j.getSubmittedById())) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND);
        }
        if (!j.isPending()) {
            throw new AttendanceException(AttendanceException.Kind.JUSTIFICATION_INVALID_STATE);
        }
        return j;
    }

    // ------------------------------------------------------------------
    // Helpers de résolution
    // ------------------------------------------------------------------

    private AttendanceRecord createAbsence(CheckpointRef checkpoint, EnrollmentDirectory.EnrollmentRef enrollment,
                                           Long studentId, Instant now) {
        AttendanceRecord record = new AttendanceRecord(checkpoint.internalId(), enrollment.internalId(),
                studentId, studentId, now, AttendanceRecordSource.MANUAL, AttendanceStatus.ABSENT, null,
                "absence à justifier");
        AttendanceRecord saved;
        try {
            saved = recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException concurrent) {
            return recordRepository
                    .findByAttendanceCheckpointIdAndEnrollmentId(checkpoint.internalId(), enrollment.internalId())
                    .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.RECORD_INVALID_STATE));
        }
        correctionRepository.save(AttendanceCorrection.created(saved.getId(), AttendanceStatus.ABSENT, null,
                "absence à justifier", "dépôt de justificatif", studentId, now));
        return saved;
    }

    private EnrollmentDirectory.EnrollmentRef resolveOwnEnrollment(String studentSubject, SessionRef session) {
        UUID userPublicId = parseUuid(studentSubject, AttendanceException.Kind.OPERATION_FORBIDDEN);
        // Date civile de la séance dans son fuseau persisté (et non en UTC) :
        // même convention que AttendanceService / AttendanceManagementService /
        // AttendanceReportService — une séance qui commence juste après minuit
        // local ne doit pas être rattachée à la veille.
        LocalDate sessionDay = session.startsAt().atZone(persistedZone(session.timeZoneId())).toLocalDate();
        List<EnrollmentDirectory.EnrollmentRef> matching = enrollmentDirectory
                .findActiveEnrollmentsForUserOn(userPublicId, sessionDay).stream()
                .filter(e -> session.classGroupPublicIds().contains(e.classGroupPublicId()))
                .toList();
        if (matching.isEmpty()) {
            throw new AttendanceException(AttendanceException.Kind.NOT_ENROLLED);
        }
        if (matching.size() > 1) {
            throw new AttendanceException(AttendanceException.Kind.ENROLLMENT_AMBIGUOUS);
        }
        return matching.get(0);
    }

    private JustificationResponse describe(AttendanceJustification j, boolean staff) {
        AttendanceRecord record = recordRepository.findById(j.getAttendanceRecordId()).orElse(null);
        if (record == null) {
            return null;
        }
        EnrollmentDirectory.EnrollmentRef enrollment =
                enrollmentDirectory.findByInternalId(record.getEnrollmentId()).orElse(null);
        SessionRef session = null;
        CheckpointRef checkpoint = null;
        if (enrollment != null && enrollment.classGroupPublicId() != null) {
            session = courseSessionDirectory
                    .findSessionsForClasses(Set.of(enrollment.classGroupPublicId()), null, null).stream()
                    .filter(s -> s.checkpoints().stream()
                            .anyMatch(cp -> cp.internalId() == record.getAttendanceCheckpointId()))
                    .findFirst().orElse(null);
            if (session != null) {
                checkpoint = session.checkpoints().stream()
                        .filter(cp -> cp.internalId() == record.getAttendanceCheckpointId())
                        .findFirst().orElse(null);
            }
        }
        return toResponse(j, session, checkpoint, enrollment, record.getStatus(), staff);
    }

    private JustificationResponse describeForStaff(AttendanceJustification j) {
        return describe(j, true);
    }

    private JustificationResponse toResponse(AttendanceJustification j, SessionRef session, CheckpointRef checkpoint,
                                             EnrollmentDirectory.EnrollmentRef enrollment,
                                             AttendanceStatus attendanceStatus, boolean staff) {
        EnrollmentDirectory.AttendeeRef attendee = staff && enrollment != null
                ? enrollmentDirectory.describeAttendee(enrollment.internalId()).orElse(null)
                : null;
        return new JustificationResponse(
                j.getPublicId(), j.getStatus().name(), j.getCategory().name(), j.getExternalReference(),
                j.getComment(), j.getSubmittedAt(), j.getReviewedAt(), j.getDecisionReason(),
                session != null ? session.publicId() : null,
                session != null ? session.title() : null,
                session != null ? session.startsAt() : null,
                checkpoint != null ? checkpoint.publicId() : null,
                checkpoint != null ? checkpoint.label() : null,
                enrollment != null ? enrollment.classGroupCode() : null,
                staff && attendee != null ? attendee.studentProfilePublicId() : null,
                staff && attendee != null ? attendee.studentNumber() : null,
                staff && attendee != null ? attendee.firstName() : null,
                staff && attendee != null ? attendee.lastName() : null,
                attendanceStatus.name());
    }

    private boolean inReadScope(JustificationResponse described, String callerSubject) {
        // ADMIN/SUPER_ADMIN/SCHOOL_ADMINISTRATION : global.
        // PEDAGOGICAL_MANAGER : classe dans le périmètre.
        // TEACHER : uniquement s'il est le formateur de la séance
        //   (impossible à vérifier ici sans exposer le formateur — on
        //   s'appuie sur AcademicScopeDirectory : un TEACHER seul n'a pas
        //   de périmètre académique, donc il ne verra rien via cette
        //   route ; la lecture des justificatifs de ses séances passe par
        //   GET /sessions/{id}/attendance et l'historique).
        return academicScope.hasGlobalScope()
                || (described.checkpointPublicId() != null
                        && described.sessionPublicId() != null
                        && isClassOfSessionInScope(described));
    }

    private boolean inManageScope(JustificationResponse described, String callerSubject) {
        return inReadScope(described, callerSubject);
    }

    private boolean isClassOfSessionInScope(JustificationResponse described) {
        return courseSessionDirectory.findSessionByCheckpointPublicId(described.checkpointPublicId())
                .map(s -> s.classGroupPublicIds().stream().anyMatch(academicScope::isClassInScope))
                .orElse(false);
    }

    private AttendanceJustification requireJustification(String publicId) {
        return justificationRepository
                .findByPublicId(parseUuid(publicId, AttendanceException.Kind.JUSTIFICATION_NOT_FOUND))
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.JUSTIFICATION_NOT_FOUND));
    }

    private Long requireCaller(String subject) {
        return currentUserResolver.resolveInternalId(subject)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN));
    }

    private static List<JustificationStatus> parseStatusFilter(String value) {
        if (value == null || value.isBlank()) {
            return List.of(JustificationStatus.PENDING, JustificationStatus.ACCEPTED, JustificationStatus.REJECTED);
        }
        try {
            return List.of(JustificationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException invalid) {
            throw new AttendanceException(AttendanceException.Kind.REPORT_INVALID_FILTER);
        }
    }

    private static JustificationCategory parseCategory(String value) {
        try {
            return JustificationCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AttendanceException(AttendanceException.Kind.REPORT_INVALID_FILTER);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UUID parseUuid(String value, AttendanceException.Kind kind) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AttendanceException(kind);
        }
    }

    /**
     * Fuseau IANA <em>persisté</em> d'une séance. Une valeur invalide est un
     * état interne corrompu (validé à l'écriture) : erreur interne contrôlée
     * plutôt qu'un repli silencieux sur UTC. Même convention que
     * {@code AttendanceReportService.persistedZone}.
     */
    private static ZoneId persistedZone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Fuseau horaire persisté invalide pour une séance");
        }
    }
}
