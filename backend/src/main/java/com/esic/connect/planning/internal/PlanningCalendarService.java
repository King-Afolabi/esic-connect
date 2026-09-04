package com.esic.connect.planning.internal;

import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Construction directe d'un planning dans un calendrier interactif
 * (EF-PLAN-006 ; docs/02 §13.7).
 *
 * <p><strong>Choix structurant</strong> : un planning construit à la main
 * n'est <em>pas</em> un second modèle. C'est un travail d'import ordinaire
 * dont les lignes sont saisies au lieu d'être lues dans un fichier. Le
 * cahier l'impose d'ailleurs — « les mêmes contrôles de conflit
 * s'appliquent » (§13.7) : dupliquer le moteur de conflits pour le
 * calendrier reviendrait à garantir qu'un jour les deux divergeront.
 *
 * <p>Chaque mutation rejoue donc {@link PlanningSimulationService#revalidate}
 * sur le lot entier, exactement comme une correction de ligne : les
 * conflits de planning sont croisés — déplacer un créneau peut lever le
 * conflit d'un autre, ou en créer un ailleurs.
 *
 * <p>Le brouillon est le travail lui-même ({@code SIMULATED}) ; la
 * publication reste {@code POST /planning-imports/{id}/publish}, atomique
 * et versionnée (RG-045). Rien n'est propre au calendrier de ce côté-là.
 */
@Service
public class PlanningCalendarService {

    /** Nom porté par un travail né du calendrier : aucun fichier n'existe. */
    static final String CALENDAR_SOURCE_NAME = "calendrier-interactif";

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportRowRepository rowRepository;
    private final PlanningImportRowIssueRepository rowIssueRepository;
    private final PlanningScheduleRepository scheduleRepository;
    private final PlanningVersionRepository versionRepository;
    private final PlanningEntryRepository entryRepository;
    private final PlanningReferenceResolver referenceResolver;
    private final PlanningSimulationService simulationService;
    private final PlanningProperties properties;
    private final UserDirectory userDirectory;
    private final Clock clock;

    PlanningCalendarService(PlanningImportJobRepository jobRepository,
                            PlanningImportRowRepository rowRepository,
                            PlanningImportRowIssueRepository rowIssueRepository,
                            PlanningScheduleRepository scheduleRepository,
                            PlanningVersionRepository versionRepository,
                            PlanningEntryRepository entryRepository,
                            PlanningReferenceResolver referenceResolver,
                            PlanningSimulationService simulationService,
                            PlanningProperties properties,
                            UserDirectory userDirectory,
                            Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.scheduleRepository = scheduleRepository;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.referenceResolver = referenceResolver;
        this.simulationService = simulationService;
        this.properties = properties;
        this.userDirectory = userDirectory;
        this.clock = clock;
    }

    /**
     * Valeurs d'un créneau saisi au calendrier. Volontairement des
     * chaînes : elles traversent le <em>même</em> analyseur que les
     * colonnes d'un fichier, donc une date mal formée produit la même
     * anomalie, au même endroit, avec le même message.
     */
    public record SlotValues(
            String slotKey,
            String sessionDate,
            String startTime,
            String endTime,
            String timeZoneId,
            String title,
            String teacherPublicId,
            String roomCode) {
    }

    /**
     * Ajoute un créneau au brouillon de la classe, en le créant si aucun
     * n'est ouvert. Renvoie le travail réanalysé.
     */
    @Transactional
    public PlanningImportJob addSlot(UUID classGroupPublicId, SlotValues values,
                                     Long actorInternalId) {
        PlanningImportJob job = openDraft(classGroupPublicId, actorInternalId);
        appendRow(job, values);
        return simulationService.revalidate(job);
    }

    /**
     * Déplace ou modifie un créneau du brouillon. Le déplacement dans un
     * calendrier n'est rien d'autre qu'un changement de date et d'heure :
     * il passe par le même chemin qu'une correction (EF-PLAN-003).
     */
    @Transactional
    public PlanningImportJob updateSlot(UUID jobPublicId, UUID rowPublicId, SlotValues values,
                                        Long actorInternalId, boolean globalScope) {
        PlanningImportJob job = editableJob(jobPublicId, actorInternalId, globalScope);
        PlanningImportRow row = rowOf(job, rowPublicId);
        row.setInputs(values.slotKey() != null ? values.slotKey() : row.getInputSlotKey(),
                values.sessionDate(), values.startTime(), values.endTime(),
                values.timeZoneId(), values.title(), values.teacherPublicId(), values.roomCode());
        rowRepository.saveAndFlush(row);
        return simulationService.revalidate(job);
    }

    /**
     * Retire un créneau du brouillon.
     *
     * <p>Suppression <strong>physique</strong> assumée, et seulement ici :
     * une ligne de brouillon jamais publiée n'est pas un fait produit. Un
     * créneau déjà publié, lui, n'est pas supprimé — il disparaît de la
     * version suivante, l'historique conservant la précédente (RG-046).
     */
    @Transactional
    public PlanningImportJob removeSlot(UUID jobPublicId, UUID rowPublicId, Long actorInternalId,
                                        boolean globalScope) {
        PlanningImportJob job = editableJob(jobPublicId, actorInternalId, globalScope);
        PlanningImportRow row = rowOf(job, rowPublicId);
        rowIssueRepository.deleteByRowId(row.getId());
        rowRepository.delete(row);
        rowRepository.flush();
        return simulationService.revalidate(job);
    }

    /**
     * Duplique une semaine entière vers une autre (docs/02 §13.7).
     *
     * <p>Le décalage est calculé en <em>jours</em> entre les deux lundis,
     * jamais en semaines calendaires : c'est ce qui garde le mardi sur un
     * mardi lors d'un changement d'heure.
     */
    @Transactional
    public PlanningImportJob duplicateWeek(UUID jobPublicId, LocalDate sourceWeekStart,
                                           LocalDate targetWeekStart, Long actorInternalId,
                                           boolean globalScope) {
        PlanningImportJob job = editableJob(jobPublicId, actorInternalId, globalScope);
        LocalDate source = mondayOf(sourceWeekStart);
        LocalDate target = mondayOf(targetWeekStart);
        if (source.equals(target)) {
            throw new PlanningException(PlanningException.Kind.CALENDAR_INVALID_RANGE);
        }
        long shiftDays = java.time.temporal.ChronoUnit.DAYS.between(source, target);

        List<PlanningImportRow> rows = rowRepository.findByJob_IdOrderByRowNumberAsc(job.getId());
        List<SlotValues> copies = new ArrayList<>();
        for (PlanningImportRow row : rows) {
            LocalDate day = parseDate(row.getInputSessionDate());
            if (day == null || day.isBefore(source) || !day.isBefore(source.plusDays(7))) {
                continue;
            }
            LocalDate moved = day.plusDays(shiftDays);
            copies.add(new SlotValues(null, moved.toString(), row.getInputStartTime(),
                    row.getInputEndTime(), row.getInputTimeZoneId(), row.getInputTitle(),
                    row.getInputTeacherPublicId(), row.getInputRoomCode()));
        }
        if (copies.isEmpty()) {
            throw new PlanningException(PlanningException.Kind.CALENDAR_NOTHING_TO_COPY);
        }
        for (SlotValues copy : copies) {
            appendRow(job, copy);
        }
        return simulationService.revalidate(job);
    }

    /**
     * Répète un créneau {@code occurrences} fois, tous les
     * {@code everyDays} jours (docs/02 §13.7 : « répétition d'une
     * séance »).
     */
    @Transactional
    public PlanningImportJob repeatSlot(UUID jobPublicId, UUID rowPublicId, int occurrences,
                                        int everyDays, Long actorInternalId, boolean globalScope) {
        if (occurrences < 1 || occurrences > properties.maxRows() || everyDays < 1) {
            throw new PlanningException(PlanningException.Kind.CALENDAR_INVALID_RANGE);
        }
        PlanningImportJob job = editableJob(jobPublicId, actorInternalId, globalScope);
        PlanningImportRow row = rowOf(job, rowPublicId);
        LocalDate day = parseDate(row.getInputSessionDate());
        if (day == null) {
            // Répéter une ligne dont la date est illisible produirait N
            // lignes fausses au lieu d'une : on refuse, la ligne doit
            // d'abord être corrigée.
            throw new PlanningException(PlanningException.Kind.CALENDAR_INVALID_RANGE);
        }
        for (int index = 1; index <= occurrences; index++) {
            LocalDate repeated = day.plusDays((long) index * everyDays);
            appendRow(job, new SlotValues(null, repeated.toString(), row.getInputStartTime(),
                    row.getInputEndTime(), row.getInputTimeZoneId(), row.getInputTitle(),
                    row.getInputTeacherPublicId(), row.getInputRoomCode()));
        }
        return simulationService.revalidate(job);
    }

    /**
     * Vue calendaire d'une classe sur une fenêtre : les créneaux
     * <strong>publiés</strong> de la version courante, et ceux du
     * brouillon en cours s'il en existe un — distingués par leur origine,
     * jamais mélangés.
     */
    @Transactional(readOnly = true)
    public CalendarView calendar(UUID classGroupPublicId, LocalDate from, LocalDate to,
                                 Long actorInternalId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new PlanningException(PlanningException.Kind.CALENDAR_INVALID_RANGE);
        }
        PlanningReferenceResolver.ResolvedTarget target =
                referenceResolver.resolveTarget(classGroupPublicId);

        List<CalendarSlot> published = new ArrayList<>();
        Optional<PlanningSchedule> schedule = scheduleRepository
                .findByClassGroupIdAndAcademicYearId(target.classInternalId(),
                        target.academicYearInternalId());
        Optional<PlanningVersion> current = schedule.flatMap(found -> versionRepository
                .findFirstBySchedule_IdAndStatusOrderByVersionNumberDesc(found.getId(),
                        PlanningVersionStatus.PUBLISHED));
        current.ifPresent(version -> {
            for (PlanningEntry entry : entryRepository
                    .findByPlanningVersion_IdOrderByStartsAtAsc(version.getId())) {
                LocalDate day = entry.getStartsAt().atZone(ZoneId.of(entry.getTimeZoneId()))
                        .toLocalDate();
                if (day.isBefore(from) || day.isAfter(to)) {
                    continue;
                }
                published.add(new CalendarSlot(entry.getPublicId(), entry.getSlotKey(), day,
                        entry.getStartsAt(), entry.getEndsAt(), entry.getTimeZoneId(),
                        entry.getTitle(), teacherPublicId(entry.getTeacherUserId()),
                        entry.getRoomCode(), "PUBLISHED", null));
            }
        });

        List<CalendarSlot> draft = new ArrayList<>();
        Optional<PlanningImportJob> openDraft = findOpenDraft(target, actorInternalId);
        UUID draftJobPublicId = openDraft.map(PlanningImportJob::getPublicId).orElse(null);
        openDraft.ifPresent(job -> {
            for (PlanningImportRow row : rowRepository.findByJob_IdOrderByRowNumberAsc(job.getId())) {
                LocalDate day = parseDate(row.getInputSessionDate());
                if (day == null || day.isBefore(from) || day.isAfter(to)) {
                    continue;
                }
                draft.add(new CalendarSlot(row.getPublicId(), row.getInputSlotKey(), day,
                        row.getResolvedStartsAt(), row.getResolvedEndsAt(),
                        row.getInputTimeZoneId(), row.getInputTitle(),
                        row.getInputTeacherPublicId() == null ? null
                                : parseUuidOrNull(row.getInputTeacherPublicId()),
                        row.getInputRoomCode(), "DRAFT", row.getRowStatus().name()));
            }
        });

        published.sort(Comparator.comparing(CalendarSlot::day)
                .thenComparing(slot -> slot.startsAt() == null ? Instant.EPOCH : slot.startsAt()));
        draft.sort(Comparator.comparing(CalendarSlot::day)
                .thenComparing(slot -> slot.startsAt() == null ? Instant.EPOCH : slot.startsAt()));
        return new CalendarView(classGroupPublicId, from, to,
                current.map(PlanningVersion::getVersionNumber).orElse(null),
                draftJobPublicId, published, draft);
    }

    /** Un créneau du calendrier, publié ou en brouillon. */
    public record CalendarSlot(
            UUID publicId,
            String slotKey,
            LocalDate day,
            Instant startsAt,
            Instant endsAt,
            String timeZoneId,
            String title,
            UUID teacherPublicId,
            String roomCode,
            String origin,
            String rowStatus) {
    }

    /** Fenêtre calendaire d'une classe. */
    public record CalendarView(
            UUID classGroupPublicId,
            LocalDate from,
            LocalDate to,
            Integer publishedVersionNumber,
            UUID draftJobPublicId,
            List<CalendarSlot> published,
            List<CalendarSlot> draft) {
    }

    // ------------------------------------------------------------------

    /**
     * Brouillon ouvert de la classe pour cet auteur, ou un nouveau.
     *
     * <p>Un seul brouillon par classe et par auteur : deux brouillons
     * concurrents publieraient deux versions contradictoires du même
     * planning sans que personne ne voie le désaccord.
     */
    private PlanningImportJob openDraft(UUID classGroupPublicId, Long actorInternalId) {
        PlanningReferenceResolver.ResolvedTarget target =
                referenceResolver.resolveTarget(classGroupPublicId);
        return findOpenDraft(target, actorInternalId).orElseGet(() -> {
            Instant now = clock.instant();
            PlanningImportJob job = new PlanningImportJob(
                    target.classInternalId(), target.academicYearInternalId(),
                    CALENDAR_SOURCE_NAME,
                    // Aucun fichier : l'empreinte est celle du vide, elle ne
                    // sert qu'à honorer la colonne de traçabilité.
                    PlanningCsvValues.sha256Hex(new byte[0]), 0, ',',
                    actorInternalId, now, now.plus(properties.simulationTtl()));
            return jobRepository.saveAndFlush(job);
        });
    }

    private Optional<PlanningImportJob> findOpenDraft(PlanningReferenceResolver.ResolvedTarget target,
                                                      Long actorInternalId) {
        return jobRepository
                .findFirstByClassGroupIdAndAcademicYearIdAndOriginalFileNameAndRequestedByIdAndStatusOrderByIdDesc(
                        target.classInternalId(), target.academicYearInternalId(),
                        CALENDAR_SOURCE_NAME, actorInternalId, PlanningImportJobStatus.SIMULATED)
                .filter(job -> job.getExpiresAt() == null
                        || !job.getExpiresAt().isBefore(clock.instant()));
    }

    private void appendRow(PlanningImportJob job, SlotValues values) {
        long existing = rowRepository.countByJob_Id(job.getId());
        if (existing >= properties.maxRows()) {
            throw new PlanningException(PlanningException.Kind.TOO_MANY_ROWS);
        }
        int nextNumber = rowRepository.findByJob_IdOrderByRowNumberAsc(job.getId()).stream()
                .mapToInt(PlanningImportRow::getRowNumber).max().orElse(0) + 1;
        PlanningImportRow row = new PlanningImportRow(job, nextNumber);
        // Un créneau saisi n'a pas de slot_key naturel : on en dérive un
        // stable et unique, seul moyen d'obtenir une identité de créneau
        // constante d'une publication à l'autre (RG-047).
        String slotKey = values.slotKey() != null && !values.slotKey().isBlank()
                ? values.slotKey().trim()
                : "CAL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        row.setInputs(slotKey, values.sessionDate(), values.startTime(), values.endTime(),
                values.timeZoneId(), values.title(), values.teacherPublicId(), values.roomCode());
        rowRepository.saveAndFlush(row);
    }

    private PlanningImportJob editableJob(UUID jobPublicId, Long actorInternalId, boolean globalScope) {
        PlanningImportJob job = jobRepository.findByPublicId(jobPublicId)
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.JOB_NOT_FOUND));
        if (!globalScope && !job.getRequestedById().equals(actorInternalId)) {
            throw new PlanningException(PlanningException.Kind.SCOPE_FORBIDDEN);
        }
        if (job.getStatus() != PlanningImportJobStatus.SIMULATED) {
            throw new PlanningException(PlanningException.Kind.JOB_NOT_SIMULATED);
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(clock.instant())) {
            throw new PlanningException(PlanningException.Kind.JOB_EXPIRED);
        }
        return job;
    }

    private PlanningImportRow rowOf(PlanningImportJob job, UUID rowPublicId) {
        return rowRepository.findByPublicId(rowPublicId)
                .filter(candidate -> candidate.getJob().getId().equals(job.getId()))
                .orElseThrow(() -> new PlanningException(PlanningException.Kind.ROW_NOT_FOUND));
    }

    private UUID teacherPublicId(Long internalId) {
        return internalId == null ? null
                : userDirectory.findByInternalId(internalId)
                        .map(UserDirectory.UserRef::publicId).orElse(null);
    }

    private static UUID parseUuidOrNull(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }

    private static LocalDate mondayOf(LocalDate date) {
        if (date == null) {
            throw new PlanningException(PlanningException.Kind.CALENDAR_INVALID_RANGE);
        }
        return date.with(java.time.temporal.TemporalAdjusters
                .previousOrSame(java.time.DayOfWeek.MONDAY));
    }
}
