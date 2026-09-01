package com.esic.connect.planning.internal;

import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.ExistingSessionWindow;
import com.esic.connect.identity.TeacherDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Simulation d'un import CSV de planning (EF-PLAN-001/002 ; AC-007 :
 * « des séances uniquement après confirmation et publication »).
 *
 * <p><strong>Invariant T1</strong> : une simulation n'écrit que dans les
 * tables techniques {@code planning_import_*}. Aucune {@code planning_schedule},
 * {@code planning_version}, {@code planning_entry} ni {@code course_session}
 * n'est créée ici. Le fichier n'est jamais persisté (SHA-256 seul).
 *
 * <p>Contrôles de cette tranche : structure de fichier, valeurs de
 * cellule, résolution du formateur (port {@code TeacherDirectory}),
 * doublon de {@code slot_key} intra-fichier, conflits
 * formateur / classe / salle <em>intra-fichier</em> et hors plage horaire
 * ({@code DEC-G1-005}), comparaison avec la version publiée courante
 * ({@code ADDED} / {@code MODIFIED} / {@code UNCHANGED} + compteur de
 * retraits — {@code DEC-G1-002/004}). Les avertissements d'alternance
 * ({@code DEC-G1-006}) et le conflit avec des séances déjà publiées
 * relèvent d'un checkpoint ultérieur.
 */
@Service
class PlanningSimulationService {

    private final PlanningImportJobRepository jobRepository;
    private final PlanningImportRowRepository rowRepository;
    private final PlanningImportRowIssueRepository rowIssueRepository;
    private final PlanningScheduleRepository scheduleRepository;
    private final PlanningVersionRepository versionRepository;
    private final PlanningEntryRepository entryRepository;
    private final PlanningReferenceResolver referenceResolver;
    private final CourseSessionDirectory courseSessionDirectory;
    private final PlanningProperties properties;
    private final Clock clock;

    PlanningSimulationService(PlanningImportJobRepository jobRepository,
                              PlanningImportRowRepository rowRepository,
                              PlanningImportRowIssueRepository rowIssueRepository,
                              PlanningScheduleRepository scheduleRepository,
                              PlanningVersionRepository versionRepository,
                              PlanningEntryRepository entryRepository,
                              PlanningReferenceResolver referenceResolver,
                              CourseSessionDirectory courseSessionDirectory,
                              PlanningProperties properties,
                              Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.scheduleRepository = scheduleRepository;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.referenceResolver = referenceResolver;
        this.courseSessionDirectory = courseSessionDirectory;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * @param originalFileName nom d'origine (assaini avant persistance)
     * @param contentType      type MIME déclaré ({@code null} toléré)
     * @param content          octets bruts reçus
     * @param requesterInternalId compte de l'appelant (auteur)
     * @param classGroupPublicId  classe cible de l'import
     */
    record SimulationCommand(
            String originalFileName,
            String contentType,
            byte[] content,
            Long requesterInternalId,
            UUID classGroupPublicId) {
    }

    @Transactional
    PlanningImportJob simulate(SimulationCommand command) {
        String csvText = PlanningCsvGuard.decodeAndValidate(command.originalFileName(),
                command.contentType(), command.content(), properties.maxFileBytes());
        String sha256 = PlanningCsvValues.sha256Hex(command.content());

        PlanningReferenceResolver.ResolvedTarget target =
                referenceResolver.resolveTarget(command.classGroupPublicId());

        ParsedPlanningCsv parsed = PlanningCsvParser.parse(csvText, properties.maxRows());
        if (parsed.tooManyRows()) {
            throw new PlanningException(PlanningException.Kind.TOO_MANY_ROWS);
        }
        if (!parsed.missingMandatoryNames().isEmpty()) {
            throw new PlanningException(PlanningException.Kind.MISSING_COLUMNS);
        }
        if (parsed.noDataRows()) {
            throw new PlanningException(PlanningException.Kind.FILE_UNREADABLE);
        }

        Instant now = clock.instant();
        PlanningImportJob job = new PlanningImportJob(
                target.classInternalId(), target.academicYearInternalId(),
                PlanningCsvValues.sanitizeFileName(command.originalFileName()), sha256,
                command.content().length, parsed.separator(), command.requesterInternalId(),
                now, now.plus(properties.simulationTtl()));
        jobRepository.save(job);

        // 1. Analyse ligne à ligne.
        List<RowAnalysis> analyses = new ArrayList<>();
        Map<String, RowAnalysis> bySlotKey = new HashMap<>();
        for (ParsedPlanningCsv.DataRow dataRow : parsed.rows()) {
            RowAnalysis analysis = analyseRow(parsed, dataRow, target);
            // Doublon de slot_key intra-fichier.
            if (analysis.slotKey != null) {
                RowAnalysis previous = bySlotKey.putIfAbsent(analysis.slotKey, analysis);
                if (previous != null) {
                    analysis.addError(PlanningIssueCodes.SLOT_KEY_DUPLICATED, "slot_key",
                            analysis.slotKey, "Ce slot_key est déjà présent sur une autre ligne du fichier.");
                }
            }
            analyses.add(analysis);
        }

        // 2. Conflits intra-fichier (formateur / classe / salle) + hors plage horaire.
        detectConflicts(analyses);

        // 2bis. Conflits avec des séances DÉJÀ publiées (RG-034 ; audit
        // G1-B.1) — formateur et classe uniquement. La salle n'est PAS
        // vérifiée contre les séances existantes : le module
        // `coursesession` ne porte pas de `room_code` (limite documentée
        // dans G1_REQUIREMENTS_TRACEABILITY.md / DEC-G1-005).
        detectPublishedConflicts(analyses, target);

        // 3. Comparaison avec la version publiée courante.
        Map<String, PlanningEntry> publishedBySlotKey = loadPublishedEntries(target);
        int added = 0;
        int modified = 0;
        int unchanged = 0;
        for (RowAnalysis analysis : analyses) {
            if (analysis.hasError() || analysis.slotKey == null) {
                analysis.plannedAction = analysis.hasError() ? PlannedAction.CONFLICT : PlannedAction.ADDED;
                continue;
            }
            PlanningEntry existing = publishedBySlotKey.get(analysis.slotKey);
            if (existing == null) {
                analysis.plannedAction = PlannedAction.ADDED;
                added++;
            } else if (isUnchanged(existing, analysis)) {
                analysis.plannedAction = PlannedAction.UNCHANGED;
                unchanged++;
            } else {
                analysis.plannedAction = PlannedAction.MODIFIED;
                modified++;
            }
        }
        int removed = 0;
        for (String publishedKey : publishedBySlotKey.keySet()) {
            boolean present = analyses.stream().anyMatch(a -> publishedKey.equals(a.slotKey));
            if (!present) {
                removed++;
            }
        }

        // 4. Persistance des lignes + anomalies.
        int valid = 0;
        int warning = 0;
        int error = 0;
        List<PlanningImportRowIssue> issuesToSave = new ArrayList<>();
        for (RowAnalysis analysis : analyses) {
            PlanningImportRow row = new PlanningImportRow(job, analysis.dataRow.rowNumber());
            row.setInputs(analysis.slotKey, analysis.rawDate, analysis.rawStart, analysis.rawEnd,
                    analysis.rawZone, analysis.title, analysis.rawTeacher, analysis.roomCode);
            row.setResolution(analysis.resolvedTeacherUserId, analysis.startsAt, analysis.endsAt);
            PlanningRowStatus status = analysis.hasError() ? PlanningRowStatus.ERROR
                    : analysis.hasWarning() ? PlanningRowStatus.WARNING : PlanningRowStatus.VALID;
            row.setOutcome(status, analysis.plannedAction);
            rowRepository.save(row);
            for (DraftIssue draft : analysis.issues) {
                issuesToSave.add(new PlanningImportRowIssue(row, draft.severity, draft.column,
                        PlanningCsvValues.truncateReceivedValue(draft.receivedValue), draft.code, draft.message));
            }
            switch (status) {
                case VALID -> valid++;
                case WARNING -> warning++;
                case ERROR -> error++;
            }
        }
        rowIssueRepository.saveAll(issuesToSave);

        boolean confirmable = error == 0;
        job.recordSimulationCounts(analyses.size(), valid, warning, error, 0,
                added, modified, unchanged, removed, confirmable);
        jobRepository.save(job);
        return job;
    }

    // ------------------------------------------------------------------

    private RowAnalysis analyseRow(ParsedPlanningCsv parsed, ParsedPlanningCsv.DataRow dataRow,
                                   PlanningReferenceResolver.ResolvedTarget target) {
        RowAnalysis analysis = new RowAnalysis(dataRow);
        if (dataRow.columnCountMismatch()) {
            analysis.addWarning("PLAN_COLUMN_COUNT_MISMATCH", null, null,
                    "Le nombre de colonnes de cette ligne diffère de l'en-tête.");
        }

        analysis.rawDate = PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.SESSION_DATE));
        analysis.rawStart = PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.START_TIME));
        analysis.rawEnd = PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.END_TIME));
        analysis.rawZone = PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.TIME_ZONE_ID));
        analysis.rawTeacher = PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.TEACHER_PUBLIC_ID));
        analysis.title = PlanningCsvValues.clamp(
                PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.TITLE)), 191);
        analysis.roomCode = PlanningCsvValues.clamp(
                PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.ROOM_CODE)), 50);

        String slotKey = PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.SLOT_KEY));
        if (slotKey == null) {
            analysis.addError(PlanningIssueCodes.SLOT_KEY_REQUIRED, "slot_key", null,
                    "La colonne slot_key est obligatoire.");
        } else if (slotKey.length() > 64) {
            analysis.addError(PlanningIssueCodes.SLOT_KEY_REQUIRED, "slot_key", slotKey,
                    "slot_key dépasse 64 caractères.");
        } else {
            analysis.slotKey = slotKey;
        }

        if (analysis.title == null) {
            analysis.addError(PlanningIssueCodes.TITLE_REQUIRED, "title", null,
                    "La colonne title est obligatoire.");
        }

        Optional<LocalDate> date = PlanningCsvValues.parseDate(analysis.rawDate);
        if (date.isEmpty()) {
            analysis.addError(PlanningIssueCodes.DATE_INVALID, "session_date", analysis.rawDate,
                    "Date invalide (attendu yyyy-MM-dd ou dd/MM/yyyy).");
        }
        Optional<LocalTime> start = PlanningCsvValues.parseTime(analysis.rawStart);
        if (start.isEmpty()) {
            analysis.addError(PlanningIssueCodes.TIME_INVALID, "start_time", analysis.rawStart,
                    "Heure de début invalide (attendu HH:mm).");
        }
        Optional<LocalTime> end = PlanningCsvValues.parseTime(analysis.rawEnd);
        if (end.isEmpty()) {
            analysis.addError(PlanningIssueCodes.TIME_INVALID, "end_time", analysis.rawEnd,
                    "Heure de fin invalide (attendu HH:mm).");
        }
        Optional<ZoneId> zone = PlanningCsvValues.parseZone(analysis.rawZone);
        if (zone.isEmpty()) {
            analysis.addError(PlanningIssueCodes.TIME_ZONE_INVALID, "time_zone_id", analysis.rawZone,
                    "Fuseau horaire inconnu (identifiant IANA attendu).");
        }

        if (date.isPresent() && start.isPresent() && end.isPresent() && zone.isPresent()) {
            analysis.timeZoneId = zone.get().getId();
            analysis.startsAt = PlanningCsvValues.toUtc(date.get(), start.get(), zone.get());
            analysis.endsAt = PlanningCsvValues.toUtc(date.get(), end.get(), zone.get());
            if (!analysis.endsAt.isAfter(analysis.startsAt)) {
                analysis.addError(PlanningIssueCodes.PERIOD_INVALID, "end_time", analysis.rawEnd,
                        "L'heure de fin doit être postérieure à l'heure de début.");
            } else {
                long minutes = ChronoUnit.MINUTES.between(analysis.startsAt, analysis.endsAt);
                if (minutes < properties.minDuration().toMinutes()
                        || minutes > properties.maxDuration().toMinutes()) {
                    analysis.addWarning(PlanningIssueCodes.DURATION_ABNORMAL, "end_time", analysis.rawEnd,
                            "Durée du créneau inhabituelle.");
                }
                LocalTime s = start.get();
                LocalTime e = end.get();
                if (s.isBefore(properties.workingDayStart()) || e.isAfter(properties.workingDayEnd())) {
                    analysis.addWarning(PlanningIssueCodes.OUTSIDE_WORKING_HOURS, "start_time", analysis.rawStart,
                            "Créneau hors de la plage horaire habituelle.");
                }
            }
        }

        Optional<TeacherDirectory.TeacherRef> teacher = referenceResolver.resolveTeacher(analysis.rawTeacher);
        if (analysis.rawTeacher == null) {
            analysis.addError(PlanningIssueCodes.TEACHER_UNKNOWN, "teacher_public_id", null,
                    "La colonne teacher_public_id est obligatoire.");
        } else if (teacher.isEmpty()) {
            analysis.addError(PlanningIssueCodes.TEACHER_NOT_ELIGIBLE, "teacher_public_id", analysis.rawTeacher,
                    "Aucun compte formateur actif ne correspond à cet identifiant.");
        } else {
            analysis.resolvedTeacherUserId = teacher.get().internalId();
        }
        return analysis;
    }

    private void detectConflicts(List<RowAnalysis> analyses) {
        for (int i = 0; i < analyses.size(); i++) {
            RowAnalysis a = analyses.get(i);
            if (a.startsAt == null || a.endsAt == null) {
                continue;
            }
            for (int j = i + 1; j < analyses.size(); j++) {
                RowAnalysis b = analyses.get(j);
                if (b.startsAt == null || b.endsAt == null || !overlaps(a, b)) {
                    continue;
                }
                // Même classe pour tout le fichier : deux créneaux qui se chevauchent = conflit classe.
                a.addError(PlanningIssueCodes.CONFLICT_CLASS, null, null,
                        "Chevauchement avec un autre créneau de la même classe.");
                b.addError(PlanningIssueCodes.CONFLICT_CLASS, null, null,
                        "Chevauchement avec un autre créneau de la même classe.");
                if (a.resolvedTeacherUserId != null
                        && a.resolvedTeacherUserId.equals(b.resolvedTeacherUserId)) {
                    a.addError(PlanningIssueCodes.CONFLICT_TEACHER, "teacher_public_id", null,
                            "Le formateur est affecté à deux créneaux qui se chevauchent.");
                    b.addError(PlanningIssueCodes.CONFLICT_TEACHER, "teacher_public_id", null,
                            "Le formateur est affecté à deux créneaux qui se chevauchent.");
                }
                if (a.roomCode != null && a.roomCode.equalsIgnoreCase(b.roomCode)) {
                    a.addError(PlanningIssueCodes.CONFLICT_ROOM, "room_code", a.roomCode,
                            "La salle est utilisée par deux créneaux qui se chevauchent.");
                    b.addError(PlanningIssueCodes.CONFLICT_ROOM, "room_code", b.roomCode,
                            "La salle est utilisée par deux créneaux qui se chevauchent.");
                }
            }
        }
    }

    private static boolean overlaps(RowAnalysis a, RowAnalysis b) {
        return a.startsAt.isBefore(b.endsAt) && b.startsAt.isBefore(a.endsAt);
    }

    /**
     * Conflits avec des séances <strong>déjà publiées</strong> (RG-034).
     * Passe par le port public {@link CourseSessionDirectory} — aucun
     * repository ni entité de {@code coursesession}. Exclut :
     * <ul>
     *   <li>les séances supersédées / annulées (le port ne renvoie que
     *       les séances opérationnelles) ;</li>
     *   <li>le <strong>même créneau republié</strong> : une séance dont
     *       le {@code planningSlotPublicId} correspond au {@code slot_key}
     *       de la ligne pour le même planning n'est pas un conflit
     *       contre elle-même.</li>
     * </ul>
     * La salle n'est pas vérifiée ici (le module {@code coursesession}
     * n'a pas de {@code room_code} — limite documentée).
     */
    private void detectPublishedConflicts(List<RowAnalysis> analyses,
                                          PlanningReferenceResolver.ResolvedTarget target) {
        Instant windowStart = null;
        Instant windowEnd = null;
        for (RowAnalysis a : analyses) {
            if (a.startsAt == null || a.endsAt == null) {
                continue;
            }
            windowStart = windowStart == null || a.startsAt.isBefore(windowStart) ? a.startsAt : windowStart;
            windowEnd = windowEnd == null || a.endsAt.isAfter(windowEnd) ? a.endsAt : windowEnd;
        }
        if (windowStart == null) {
            return;
        }
        List<ExistingSessionWindow> existing =
                courseSessionDirectory.findOperationalSessionWindows(windowStart, windowEnd);
        if (existing.isEmpty()) {
            return;
        }
        UUID schedulePublicId = scheduleRepository
                .findByClassGroupIdAndAcademicYearId(target.classInternalId(), target.academicYearInternalId())
                .map(PlanningSchedule::getPublicId)
                .orElse(null);

        for (RowAnalysis a : analyses) {
            if (a.startsAt == null || a.endsAt == null || a.slotKey == null || a.hasError()) {
                continue;
            }
            UUID selfSlot = PlanningSlotIds.stableSlotId(schedulePublicId, a.slotKey);
            UUID rowTeacher = parseUuidOrNull(a.rawTeacher);
            for (ExistingSessionWindow w : existing) {
                if (!(a.startsAt.isBefore(w.endsAt()) && w.startsAt().isBefore(a.endsAt))) {
                    continue;
                }
                if (selfSlot != null && selfSlot.equals(w.planningSlotPublicId())) {
                    continue; // même créneau republié : pas un conflit contre lui-même
                }
                if (rowTeacher != null && rowTeacher.equals(w.teacherPublicId())) {
                    a.addError(PlanningIssueCodes.CONFLICT_TEACHER, "teacher_public_id", null,
                            "Le formateur a déjà une séance publiée qui chevauche ce créneau.");
                }
                if (w.classGroupPublicIds().contains(target.classPublicId())) {
                    a.addError(PlanningIssueCodes.CONFLICT_CLASS, null, null,
                            "La classe a déjà une séance publiée qui chevauche ce créneau.");
                }
            }
        }
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private Map<String, PlanningEntry> loadPublishedEntries(PlanningReferenceResolver.ResolvedTarget target) {
        Optional<PlanningSchedule> schedule = scheduleRepository
                .findByClassGroupIdAndAcademicYearId(target.classInternalId(), target.academicYearInternalId());
        if (schedule.isEmpty()) {
            return Map.of();
        }
        Optional<PlanningVersion> current = versionRepository.findFirstBySchedule_IdAndStatusOrderByVersionNumberDesc(
                schedule.get().getId(), PlanningVersionStatus.PUBLISHED);
        if (current.isEmpty()) {
            return Map.of();
        }
        Map<String, PlanningEntry> bySlotKey = new HashMap<>();
        for (PlanningEntry entry : entryRepository.findByPlanningVersion_IdOrderByStartsAtAsc(current.get().getId())) {
            bySlotKey.put(entry.getSlotKey(), entry);
        }
        return bySlotKey;
    }

    private static boolean isUnchanged(PlanningEntry existing, RowAnalysis analysis) {
        return existing.getStartsAt().equals(analysis.startsAt)
                && existing.getEndsAt().equals(analysis.endsAt)
                && java.util.Objects.equals(existing.getTitle(), analysis.title)
                && java.util.Objects.equals(existing.getTeacherUserId(), analysis.resolvedTeacherUserId)
                && java.util.Objects.equals(existing.getRoomCode(), analysis.roomCode)
                && java.util.Objects.equals(existing.getTimeZoneId(), analysis.timeZoneId);
    }

    /** Accumulateur d'analyse d'une ligne (hors persistance). */
    private static final class RowAnalysis {
        private final ParsedPlanningCsv.DataRow dataRow;
        private final List<DraftIssue> issues = new ArrayList<>();
        private String slotKey;
        private String title;
        private String roomCode;
        private String timeZoneId;
        private String rawDate;
        private String rawStart;
        private String rawEnd;
        private String rawZone;
        private String rawTeacher;
        private Long resolvedTeacherUserId;
        private Instant startsAt;
        private Instant endsAt;
        private PlannedAction plannedAction = PlannedAction.ADDED;

        RowAnalysis(ParsedPlanningCsv.DataRow dataRow) {
            this.dataRow = dataRow;
        }

        void addError(String code, String column, String receivedValue, String message) {
            issues.add(new DraftIssue(PlanningIssueSeverity.ERROR, code, column, receivedValue, message));
        }

        void addWarning(String code, String column, String receivedValue, String message) {
            issues.add(new DraftIssue(PlanningIssueSeverity.WARNING, code, column, receivedValue, message));
        }

        boolean hasError() {
            return issues.stream().anyMatch(i -> i.severity == PlanningIssueSeverity.ERROR);
        }

        boolean hasWarning() {
            return issues.stream().anyMatch(i -> i.severity == PlanningIssueSeverity.WARNING);
        }
    }

    private record DraftIssue(PlanningIssueSeverity severity, String code, String column,
                              String receivedValue, String message) {
    }
}
