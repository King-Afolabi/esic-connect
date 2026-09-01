package com.esic.connect.planning.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.PlanningSessionWriter;
import com.esic.connect.coursesession.PlanningSessionWriter.PlannedSession;
import com.esic.connect.coursesession.PlanningSessionWriter.PlanningSyncCommand;
import com.esic.connect.coursesession.PlanningSessionWriter.PlanningSyncResult;
import com.esic.connect.coursesession.PlanningSessionWriter.SupersededSession;
import com.esic.connect.coursesession.PlanningSessionWriter.SyncedSession;
import com.esic.connect.planning.PlanningPublishedEvent;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Publication d'une version de planning (EF-PLAN-004 ; EF-SES-001 ;
 * AC-007/008 ; RG-030..RG-034 ; DEC-G1-001/003/004).
 *
 * <p><strong>Une seule transaction atomique</strong> (appelée par
 * {@link PlanningPublicationOrchestrator}) : verrou de ligne du job et du
 * {@code planning_schedule} ({@code SELECT … FOR UPDATE}), re-validation
 * du périmètre et des anomalies bloquantes (RG-034), création de la
 * {@code planning_version} + de ses {@code planning_entry}, matérialisation
 * des séances via le port {@link PlanningSessionWriter} (créées / réutilisées
 * / supersédées — DEC-G1-004), supersession de l'ancienne version,
 * événement {@link PlanningPublishedEvent}. Toute exception fait rollback
 * l'ensemble : aucun état partiellement publié.
 *
 * <p>Idempotence : republier un job déjà {@code PUBLISHED} renvoie la
 * version existante sans rien recréer. Un job {@code FAILED} / {@code CANCELLED}
 * / {@code EXPIRED} n'est pas republiable ({@code 409}).
 */
@Service
class PlanningPublicationService {

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportRowRepository rowRepository;
    private final PlanningScheduleRepository scheduleRepository;
    private final PlanningVersionRepository versionRepository;
    private final PlanningEntryRepository entryRepository;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final PlanningSessionWriter planningSessionWriter;
    private final PlanningChangePublisher changePublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final Clock clock;

    PlanningPublicationService(PlanningImportJobRepository jobRepository,
                               PlanningImportRowRepository rowRepository,
                               PlanningScheduleRepository scheduleRepository,
                               PlanningVersionRepository versionRepository,
                               PlanningEntryRepository entryRepository,
                               ClassGroupDirectory classGroupDirectory,
                               AcademicScopeDirectory academicScopeDirectory,
                               PlanningSessionWriter planningSessionWriter,
                               PlanningChangePublisher changePublisher,
                               ApplicationEventPublisher eventPublisher,
                               EntityManager entityManager,
                               Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.scheduleRepository = scheduleRepository;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScopeDirectory = academicScopeDirectory;
        this.planningSessionWriter = planningSessionWriter;
        this.changePublisher = changePublisher;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /** Identifiant public de la version publiée (existante si idempotent). */
    record PublicationResult(UUID versionPublicId, int versionNumber, boolean alreadyPublished) {
    }

    /**
     * Résultat idempotent si le job est déjà {@code PUBLISHED} — lecture
     * dans une transaction courte et fraîche, utilisée par
     * {@link PlanningPublicationOrchestrator} pour distinguer « course
     * concurrente gagnée par une autre requête » (⇒ résultat idempotent)
     * d'un échec réel (⇒ {@code FAILED}). Jamais {@code FAILED} pour une
     * course idempotente (audit G1-B.1).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    Optional<PublicationResult> alreadyPublishedResult(UUID jobPublicId) {
        return jobRepository.findByPublicId(jobPublicId)
                .filter(PlanningImportJob::isPublished)
                .flatMap(job -> versionRepository.findById(job.getPublishedVersionId()))
                .map(v -> new PublicationResult(v.getPublicId(), v.getVersionNumber(), true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    PublicationResult publish(UUID jobPublicId, Long requesterInternalId, boolean globalScope) {
        PlanningImportJob header = jobRepository.findByPublicId(jobPublicId)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.JOB_NOT_FOUND));
        if (!globalScope && !header.getRequestedById().equals(requesterInternalId)) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }
        PlanningImportJob job = jobRepository.findByIdForUpdate(header.getId())
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.JOB_NOT_FOUND));
        // Le verrou de ligne est acquis, mais l'instance a pu être chargée
        // (sans verrou) par `findByPublicId` avant qu'une requête
        // concurrente ne publie : rafraîchir pour lire l'état RÉELLEMENT
        // committé sous le verrou (audit G1-B.1 — sinon `isPublished()`
        // voit un instantané périmé et le perdant part en `FAILED`).
        entityManager.refresh(job);

        if (job.isPublished()) {
            PlanningVersion existing = versionRepository.findById(job.getPublishedVersionId())
                    .orElseThrow(() -> new PlanningException(PlanningException.Kind.VERSION_NOT_FOUND));
            return new PublicationResult(existing.getPublicId(), existing.getVersionNumber(), true);
        }
        if (!job.isSimulated()) {
            throw new PlanningException(PlanningException.Kind.INVALID_JOB_STATE);
        }
        if (job.getExpiresAt().isBefore(clock.instant())) {
            throw new PlanningException(PlanningException.Kind.JOB_EXPIRED);
        }

        ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory.findByInternalId(job.getClassGroupId())
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.TARGET_UNRESOLVED));
        if (!globalScope && !academicScopeDirectory.isClassInScope(classRef.publicId())) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }

        List<PlanningImportRow> rows = rowRepository.findByJob_IdOrderByRowNumberAsc(job.getId());
        boolean hasBlocking = rows.stream().anyMatch(r -> r.getRowStatus() == PlanningRowStatus.ERROR);
        if (hasBlocking) {
            throw new PlanningException(PlanningException.Kind.BLOCKING_ISSUES);
        }

        Long actorId = changePublisher.currentActorInternalId();
        Instant now = clock.instant();

        PlanningSchedule schedule = scheduleRepository
                .findByClassGroupIdAndAcademicYearId(job.getClassGroupId(), job.getAcademicYearId())
                .map(existing -> scheduleRepository.findByIdForUpdate(existing.getId()).orElse(existing))
                .orElseGet(() -> scheduleRepository.save(
                        new PlanningSchedule(job.getClassGroupId(), job.getAcademicYearId(), actorId)));

        Optional<PlanningVersion> currentPublished = versionRepository
                .findFirstBySchedule_IdAndStatusOrderByVersionNumberDesc(
                        schedule.getId(), PlanningVersionStatus.PUBLISHED);

        int newVersionNumber = schedule.getCurrentVersionNumber() + 1;
        PlanningVersion version = versionRepository.save(
                new PlanningVersion(schedule, newVersionNumber, job.getId()));

        List<PlannedSession> plannedSessions = new ArrayList<>();
        Map<UUID, PlanningEntry> entryBySlotId = new HashMap<>();
        for (PlanningImportRow row : rows) {
            if (row.getRowStatus() == PlanningRowStatus.ERROR || row.getInputSlotKey() == null
                    || row.getResolvedTeacherUserId() == null || row.getResolvedStartsAt() == null
                    || row.getResolvedEndsAt() == null) {
                continue;
            }
            UUID slotId = PlanningSlotIds.stableSlotId(schedule.getPublicId(), row.getInputSlotKey());
            UUID teacherPublicId = UUID.fromString(row.getInputTeacherPublicId().trim());
            PlanningEntry entry = new PlanningEntry(version, schedule.getId(), row.getInputSlotKey(),
                    slotId, job.getClassGroupId(), row.getResolvedTeacherUserId(), row.getInputRoomCode(),
                    row.getInputTitle(), row.getResolvedStartsAt(), row.getResolvedEndsAt(),
                    row.getInputTimeZoneId());
            entryRepository.save(entry);
            entryBySlotId.put(slotId, entry);
            plannedSessions.add(new PlannedSession(slotId, teacherPublicId, row.getInputRoomCode(),
                    row.getInputTitle(), row.getResolvedStartsAt(), row.getResolvedEndsAt(),
                    row.getInputTimeZoneId()));
        }

        PlanningSyncResult syncResult = planningSessionWriter.sync(new PlanningSyncCommand(
                version.getPublicId(), classRef.publicId(), classRef.academicYearPublicId(), plannedSessions));

        List<UUID> added = new ArrayList<>();
        List<UUID> updated = new ArrayList<>();
        for (SyncedSession created : syncResult.created()) {
            link(entryBySlotId, created);
            added.add(created.sessionPublicId());
        }
        for (SyncedSession reused : syncResult.reused()) {
            link(entryBySlotId, reused);
            updated.add(reused.sessionPublicId());
        }
        List<UUID> superseded = syncResult.superseded().stream()
                .map(SupersededSession::sessionPublicId).toList();

        String changeSummary = "%d ajout(s), %d modification(s), %d retrait(s)"
                .formatted(job.getAddedRows(), job.getModifiedRows(), superseded.size());
        version.publish(plannedSessions.size(), changeSummary, now, actorId);
        currentPublished.ifPresent(previous -> previous.supersede(version));
        schedule.markPublished(newVersionNumber, actorId);
        job.markPublished(schedule.getId(), version.getId(), now, actorId);

        eventPublisher.publishEvent(new PlanningPublishedEvent(
                schedule.getPublicId(), version.getPublicId(), newVersionNumber,
                classRef.publicId(), classRef.academicYearPublicId(), newVersionNumber == 1,
                List.copyOf(added), List.copyOf(updated), superseded, now,
                changePublisher.currentActorPublicId()));

        return new PublicationResult(version.getPublicId(), newVersionNumber, false);
    }

    private void link(Map<UUID, PlanningEntry> entryBySlotId, SyncedSession synced) {
        PlanningEntry entry = entryBySlotId.get(synced.slotPublicId());
        if (entry != null) {
            entry.linkSession(synced.sessionPublicId());
        }
    }
}
