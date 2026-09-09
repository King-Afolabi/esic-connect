package com.esic.connect.planning.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.academic.ClassGroupDirectory;
import com.esic.connect.coursesession.PlanningSessionWriter;
import com.esic.connect.coursesession.PlanningSessionWriter.PlannedSession;
import com.esic.connect.coursesession.PlanningSessionWriter.PlanningSyncCommand;
import com.esic.connect.coursesession.PlanningSessionWriter.PlanningSyncResult;
import com.esic.connect.coursesession.PlanningSessionWriter.SupersededSession;
import com.esic.connect.coursesession.PlanningSessionWriter.SyncedSession;
import com.esic.connect.identity.TeacherDirectory;
import com.esic.connect.planning.PlanningPublishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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
 * Retour à une version antérieure de planning (EF-PLAN-008 ; critère
 * AC-009 ; docs/02 §13.9).
 *
 * <p><strong>Un retour arrière n'efface rien.</strong> Le cahier est
 * explicite : « l'opération crée une nouvelle version dont le contenu
 * est celui de la version choisie, et n'efface jamais l'historique »
 * (RG-046). On ne réactive donc pas la version choisie : on en
 * <em>recopie</em> le contenu dans une version N+1, la version courante
 * passant en {@code SUPERSEDED} comme lors d'une publication ordinaire.
 *
 * <p><strong>L'identité des créneaux est conservée.</strong> Le
 * {@code slot_key} d'origine produit le même identifiant stable : les
 * séances existantes sont donc <em>réutilisées</em>, pas recréées
 * (RG-047). Un retour arrière ne fait pas perdre les présences déjà
 * enregistrées sur une séance qui existait déjà.
 *
 * <p><strong>Un formateur devenu inéligible bloque l'opération.</strong>
 * Restaurer une version dont le formateur a quitté l'établissement
 * produirait des séances sans titulaire : le refus est explicite, plutôt
 * qu'un remplacement silencieux.
 */
@Service
public class PlanningRollbackService {

    private final PlanningVersionRepository versionRepository;
    private final PlanningEntryRepository entryRepository;
    private final PlanningScheduleRepository scheduleRepository;
    private final PlanningSessionWriter planningSessionWriter;
    private final ClassGroupDirectory classGroupDirectory;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final TeacherDirectory teacherDirectory;
    private final PlanningChangePublisher changePublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    PlanningRollbackService(PlanningVersionRepository versionRepository,
                            PlanningEntryRepository entryRepository,
                            PlanningScheduleRepository scheduleRepository,
                            PlanningSessionWriter planningSessionWriter,
                            ClassGroupDirectory classGroupDirectory,
                            AcademicScopeDirectory academicScopeDirectory,
                            TeacherDirectory teacherDirectory,
                            PlanningChangePublisher changePublisher,
                            ApplicationEventPublisher eventPublisher,
                            Clock clock) {
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.scheduleRepository = scheduleRepository;
        this.planningSessionWriter = planningSessionWriter;
        this.classGroupDirectory = classGroupDirectory;
        this.academicScopeDirectory = academicScopeDirectory;
        this.teacherDirectory = teacherDirectory;
        this.changePublisher = changePublisher;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Crée une version N+1 reprenant le contenu de {@code targetVersionPublicId}.
     *
     * @return l'identité de la version créée
     */
    @Transactional
    public RollbackResult rollback(UUID targetVersionPublicId, Long actorInternalId) {
        PlanningVersion target = versionRepository.findByPublicId(targetVersionPublicId)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.VERSION_NOT_FOUND));
        if (target.getStatus() == PlanningVersionStatus.DRAFT) {
            // Un brouillon n'a jamais été publié : il n'y a rien à
            // « retrouver », et le restaurer publierait du non-validé.
            throw new PlanningException(PlanningException.Kind.INVALID_JOB_STATE);
        }

        PlanningSchedule schedule = scheduleRepository.findByIdForUpdate(target.getSchedule().getId())
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.SCHEDULE_NOT_FOUND));
        ClassGroupDirectory.ClassGroupRef classRef = classGroupDirectory
                .findByInternalId(schedule.getClassGroupId())
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.TARGET_UNRESOLVED));
        if (!academicScopeDirectory.isClassInScope(classRef.publicId())) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }

        List<PlanningEntry> sourceEntries = entryRepository.findByPlanningVersion_IdOrderByStartsAtAsc(target.getId());
        if (sourceEntries.isEmpty()) {
            throw new PlanningException(PlanningException.Kind.VERSION_HAS_NO_ENTRY);
        }

        Instant now = clock.instant();
        Optional<PlanningVersion> currentPublished = versionRepository
                .findFirstBySchedule_IdAndStatusOrderByVersionNumberDesc(
                        schedule.getId(), PlanningVersionStatus.PUBLISHED);
        int newVersionNumber = schedule.getCurrentVersionNumber() + 1;
        PlanningVersion restored = versionRepository.save(
                new PlanningVersion(schedule, newVersionNumber, null));

        List<PlannedSession> plannedSessions = new ArrayList<>();
        Map<UUID, PlanningEntry> entryBySlotId = new HashMap<>();
        for (PlanningEntry source : sourceEntries) {
            TeacherDirectory.TeacherRef teacher = teacherDirectory
                    .findEligibleTeacherByInternalId(source.getTeacherUserId())
                    .orElseThrow(() -> new PlanningException(
                            PlanningException.Kind.ROLLBACK_TEACHER_UNAVAILABLE));
            PlanningEntry copy = new PlanningEntry(restored, schedule.getId(), source.getSlotKey(),
                    // Identité de créneau conservée : la séance existante est
                    // réutilisée, jamais recréée (RG-047).
                    source.getSlotPublicId(), schedule.getClassGroupId(), source.getTeacherUserId(),
                    source.getRoomCode(), source.getTitle(), source.getStartsAt(), source.getEndsAt(),
                    source.getTimeZoneId());
            entryRepository.save(copy);
            entryBySlotId.put(source.getSlotPublicId(), copy);
            plannedSessions.add(new PlannedSession(source.getSlotPublicId(), teacher.publicId(),
                    source.getRoomCode(), source.getTitle(), source.getStartsAt(), source.getEndsAt(),
                    source.getTimeZoneId()));
        }

        PlanningSyncResult syncResult = planningSessionWriter.sync(new PlanningSyncCommand(
                restored.getPublicId(), classRef.publicId(), classRef.academicYearPublicId(),
                plannedSessions));

        List<UUID> added = new ArrayList<>();
        List<UUID> updated = new ArrayList<>();
        syncResult.created().forEach(created -> {
            link(entryBySlotId, created);
            added.add(created.sessionPublicId());
        });
        syncResult.reused().forEach(reused -> {
            link(entryBySlotId, reused);
            updated.add(reused.sessionPublicId());
        });
        List<UUID> superseded = syncResult.superseded().stream()
                .map(SupersededSession::sessionPublicId).toList();

        String summary = "Retour à la version %d (%d créneau(x) restauré(s), %d retrait(s))"
                .formatted(target.getVersionNumber(), plannedSessions.size(), superseded.size());
        restored.publish(plannedSessions.size(), summary, now, actorInternalId);
        currentPublished.ifPresent(previous -> previous.supersede(restored));
        schedule.markPublished(newVersionNumber, actorInternalId);

        eventPublisher.publishEvent(new PlanningPublishedEvent(
                schedule.getPublicId(), restored.getPublicId(), newVersionNumber,
                classRef.publicId(), classRef.academicYearPublicId(), false,
                List.copyOf(added), List.copyOf(updated), superseded, now,
                changePublisher.currentActorPublicId()));

        return new RollbackResult(restored.getPublicId(), newVersionNumber,
                target.getPublicId(), target.getVersionNumber(), plannedSessions.size());
    }

    private void link(Map<UUID, PlanningEntry> entryBySlotId, SyncedSession synced) {
        PlanningEntry entry = entryBySlotId.get(synced.slotPublicId());
        if (entry != null) {
            entry.linkSession(synced.sessionPublicId());
        }
    }

    /**
     * @param versionPublicId       version créée par le retour arrière
     * @param versionNumber         son numéro (N+1)
     * @param restoredFromPublicId  version dont le contenu a été repris
     * @param restoredFromNumber    son numéro
     * @param entryCount            nombre de créneaux restaurés
     */
    public record RollbackResult(UUID versionPublicId, int versionNumber,
                                 UUID restoredFromPublicId, int restoredFromNumber,
                                 int entryCount) {
    }
}
