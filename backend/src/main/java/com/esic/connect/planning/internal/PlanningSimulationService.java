package com.esic.connect.planning.internal;

import com.esic.connect.alternation.AlternationDirectory;
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
    private final PlanningImportJobIssueRepository jobIssueRepository;
    private final PlanningScheduleRepository scheduleRepository;
    private final PlanningVersionRepository versionRepository;
    private final PlanningEntryRepository entryRepository;
    private final PlanningReferenceResolver referenceResolver;
    private final CourseSessionDirectory courseSessionDirectory;
    private final PlanningProperties properties;
    private final AlternationDirectory alternationDirectory;
    private final Clock clock;

    PlanningSimulationService(PlanningImportJobRepository jobRepository,
                              PlanningImportRowRepository rowRepository,
                              PlanningImportRowIssueRepository rowIssueRepository,
                              PlanningImportJobIssueRepository jobIssueRepository,
                              PlanningScheduleRepository scheduleRepository,
                              PlanningVersionRepository versionRepository,
                              PlanningEntryRepository entryRepository,
                              PlanningReferenceResolver referenceResolver,
                              CourseSessionDirectory courseSessionDirectory,
                              PlanningProperties properties,
                              AlternationDirectory alternationDirectory,
                              Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.jobIssueRepository = jobIssueRepository;
        this.scheduleRepository = scheduleRepository;
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.referenceResolver = referenceResolver;
        this.courseSessionDirectory = courseSessionDirectory;
        this.properties = properties;
        this.alternationDirectory = alternationDirectory;
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
        String sha256 = PlanningCsvValues.sha256Hex(command.content());

        PlanningReferenceResolver.ResolvedTarget target =
                referenceResolver.resolveTarget(command.classGroupPublicId());

        // Le FORMAT est choisi sur l'extension puis confirmé par le contenu
        // réel dans le garde correspondant. Les deux chemins convergent vers
        // la MÊME structure, afin que la validation métier et la détection de
        // conflits ne divergent pas par format (EF-PLAN-011).
        ParsedPlanningCsv parsed;
        List<String> ignoredSheets = List.of();
        if (PlanningWorkbookParser.looksLikeWorkbook(command.originalFileName())) {
            byte[] workbookBytes = PlanningWorkbookGuard.validate(command.originalFileName(),
                    command.contentType(), command.content(), properties.maxFileBytes());
            PlanningWorkbookParser.ParsedWorkbook workbook =
                    PlanningWorkbookParser.parse(workbookBytes, properties.maxRows());
            parsed = workbook.parsed();
            ignoredSheets = workbook.ignoredSheets();
        } else {
            String csvText = PlanningCsvGuard.decodeAndValidate(command.originalFileName(),
                    command.contentType(), command.content(), properties.maxFileBytes());
            parsed = PlanningCsvParser.parse(csvText, properties.maxRows());
        }
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

        // Feuilles présentes mais non lues : signalées, jamais ignorées en
        // silence. Un planning porte sur UNE classe (EF-PLAN-011).
        for (String ignored : ignoredSheets) {
            jobIssueRepository.save(new PlanningImportJobIssue(job, PlanningIssueSeverity.WARNING,
                    PlanningIssueCodes.WORKBOOK_SHEET_IGNORED,
                    "La feuille « " + ignored + " » n'a pas été lue : un planning porte sur une "
                            + "seule classe, seule la première feuille est prise en compte.", null));
        }

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
        // G1-B.1) — formateur, classe ET salle. La salle est vérifiée
        // depuis le sprint 6 : `course_session.room_code` existe (V21), ce
        // qui lève la limite DEC-G1-005 (EF-ORG-004).
        detectPublishedConflicts(analyses, target);

        // 2ter. Avertissement d'alternance (EF-PLAN-010) : un créneau qui
        // tombe sur une période résolue en ENTREPRISE pour la classe visée.
        // Avertissement et non erreur : le cahier prévoit explicitement une
        // séance exceptionnelle qui prime sur le rythme (docs/02 §8.3).
        detectAlternationWarnings(analyses, target);

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

    /**
     * Rejoue l'analyse complète d'un travail d'import à partir de ses
     * lignes persistées (EF-PLAN-003).
     *
     * <p><strong>Pourquoi tout le lot, et pas seulement la ligne
     * corrigée.</strong> Les conflits de planning sont par nature
     * <em>croisés</em> : formateur, classe et salle se disputent un
     * créneau <em>entre</em> lignes. Corriger l'heure d'une ligne peut
     * lever le conflit d'une autre, ou en créer un nouveau. Ne revalider
     * que la ligne touchée laisserait le lot dans un état faux et
     * plausible — le pire des deux.
     *
     * <p>Le fichier d'origine n'est pas relu : il n'est jamais conservé
     * (RG-036). L'analyse repart des valeurs normalisées déjà stockées,
     * corrections comprises.
     */
    @Transactional
    PlanningImportJob revalidate(PlanningImportJob job) {
        PlanningReferenceResolver.ResolvedTarget target =
                referenceResolver.resolveTargetByInternalIds(job.getClassGroupId(),
                        job.getAcademicYearId());

        List<PlanningImportRow> rows = rowRepository.findByJob_IdOrderByRowNumberAsc(job.getId());
        List<RowAnalysis> analyses = new ArrayList<>();
        Map<String, RowAnalysis> bySlotKey = new HashMap<>();
        Map<Integer, PlanningImportRow> rowsByNumber = new HashMap<>();

        for (PlanningImportRow row : rows) {
            rowsByNumber.put(row.getRowNumber(), row);
            RowAnalysis analysis = analyseValues(new RawRowValues(
                            row.getInputSlotKey(), row.getInputSessionDate(), row.getInputStartTime(),
                            row.getInputEndTime(), row.getInputTimeZoneId(), row.getInputTitle(),
                            row.getInputTeacherPublicId(), row.getInputRoomCode()),
                    new ParsedPlanningCsv.DataRow(row.getRowNumber(), List.of(), false),
                    false, target);
            if (analysis.slotKey != null) {
                RowAnalysis previous = bySlotKey.putIfAbsent(analysis.slotKey, analysis);
                if (previous != null) {
                    analysis.addError(PlanningIssueCodes.SLOT_KEY_DUPLICATED, "slot_key",
                            analysis.slotKey,
                            "Ce slot_key est déjà présent sur une autre ligne du fichier.");
                }
            }
            analyses.add(analysis);
        }

        detectConflicts(analyses);
        detectPublishedConflicts(analyses, target);

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
            if (analyses.stream().noneMatch(a -> publishedKey.equals(a.slotKey))) {
                removed++;
            }
        }

        int valid = 0;
        int warning = 0;
        int error = 0;
        List<PlanningImportRowIssue> issuesToSave = new ArrayList<>();
        for (RowAnalysis analysis : analyses) {
            PlanningImportRow row = rowsByNumber.get(analysis.dataRow.rowNumber());
            row.setResolution(analysis.resolvedTeacherUserId, analysis.startsAt, analysis.endsAt);
            PlanningRowStatus status = analysis.hasError() ? PlanningRowStatus.ERROR
                    : analysis.hasWarning() ? PlanningRowStatus.WARNING : PlanningRowStatus.VALID;
            row.setOutcome(status, analysis.plannedAction);
            rowRepository.save(row);
            // Les anomalies précédentes ne valent plus rien : les garder
            // ferait apparaître une ligne corrigée comme toujours fautive.
            rowIssueRepository.deleteByRowId(row.getId());
            for (DraftIssue draft : analysis.issues) {
                issuesToSave.add(new PlanningImportRowIssue(row, draft.severity, draft.column,
                        PlanningCsvValues.truncateReceivedValue(draft.receivedValue), draft.code,
                        draft.message));
            }
            switch (status) {
                case VALID -> valid++;
                case WARNING -> warning++;
                case ERROR -> error++;
            }
        }
        rowIssueRepository.flush();
        rowIssueRepository.saveAll(issuesToSave);

        job.recordSimulationCounts(analyses.size(), valid, warning, error, 0,
                added, modified, unchanged, removed, error == 0);
        return jobRepository.save(job);
    }

    // ------------------------------------------------------------------

    private RowAnalysis analyseRow(ParsedPlanningCsv parsed, ParsedPlanningCsv.DataRow dataRow,
                                   PlanningReferenceResolver.ResolvedTarget target) {
        return analyseValues(new RawRowValues(
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.SLOT_KEY)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.SESSION_DATE)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.START_TIME)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.END_TIME)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.TIME_ZONE_ID)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.TITLE)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.TEACHER_PUBLIC_ID)),
                        PlanningCsvValues.trimToNull(parsed.cell(dataRow, PlanningColumn.ROOM_CODE))),
                dataRow, dataRow.columnCountMismatch(), target);
    }

    /**
     * Valeurs brutes d'une ligne, indépendantes du format d'entrée.
     *
     * <p>Extrait pour que la <strong>correction d'une ligne</strong>
     * (EF-PLAN-003) rejoue exactement la même analyse que la simulation :
     * une ligne corrigée ne doit pas suivre un chemin de validation
     * différent d'une ligne d'origine.
     */
    record RawRowValues(String slotKey, String rawDate, String rawStart, String rawEnd,
                        String rawZone, String title, String rawTeacher, String roomCode) {
    }

    /** Analyse commune à la simulation et à la correction de ligne. */
    RowAnalysis analyseValues(RawRowValues values, ParsedPlanningCsv.DataRow dataRow,
                              boolean columnCountMismatch,
                              PlanningReferenceResolver.ResolvedTarget target) {
        RowAnalysis analysis = new RowAnalysis(dataRow);
        if (columnCountMismatch) {
            analysis.addWarning("PLAN_COLUMN_COUNT_MISMATCH", null, null,
                    "Le nombre de colonnes de cette ligne diffère de l'en-tête.");
        }

        analysis.rawDate = values.rawDate();
        analysis.rawStart = values.rawStart();
        analysis.rawEnd = values.rawEnd();
        analysis.rawZone = values.rawZone();
        analysis.rawTeacher = values.rawTeacher();
        analysis.title = PlanningCsvValues.clamp(values.title(), 191);
        analysis.roomCode = PlanningCsvValues.clamp(values.roomCode(), 50);

        String slotKey = values.slotKey();
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
     * <p>Depuis la migration {@code V21}, la séance conserve son
     * {@code room_code} : le conflit de <strong>salle</strong> s'exerce
     * donc aussi contre les séances déjà publiées, et plus seulement à
     * l'intérieur d'un même fichier. Deux imports successifs ne peuvent
     * plus placer deux classes dans la même salle à la même heure sans
     * que rien ne le signale (EF-PLAN-009, EF-ORG-004).
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
                // Salle occupée par une séance déjà publiée (EF-PLAN-009,
                // EF-ORG-004). Une salle indéterminée des deux côtés n'est
                // évidemment pas un conflit : deux créneaux sans salle
                // n'occupent rien.
                if (a.roomCode != null && a.roomCode.equalsIgnoreCase(w.roomCode())) {
                    a.addError(PlanningIssueCodes.CONFLICT_ROOM, "room_code", a.roomCode,
                            "La salle est déjà occupée par une séance publiée sur ce créneau.");
                }
            }
        }
    }

    /**
     * Signale les créneaux tombant sur une période d'entreprise
     * (EF-PLAN-010).
     *
     * <p>Le contrôle porte sur la <strong>classe</strong>, pas sur chaque
     * apprenant : une exception individuelle ne remet pas en cause la
     * cohérence du planning, et parcourir l'effectif à chaque ligne
     * coûterait cher pour un simple avertissement.
     *
     * <p>Une seule résolution par jour distinct : un planning de vingt
     * créneaux sur cinq jours ne doit pas produire vingt requêtes
     * (NFR-PERF-08).
     */
    private void detectAlternationWarnings(List<RowAnalysis> analyses,
                                           PlanningReferenceResolver.ResolvedTarget target) {
        Map<java.time.LocalDate, AlternationDirectory.Axis> byDay = new HashMap<>();
        for (RowAnalysis a : analyses) {
            if (a.startsAt == null || a.timeZoneId == null || a.hasError()) {
                continue;
            }
            java.time.LocalDate day = a.startsAt.atZone(java.time.ZoneId.of(a.timeZoneId))
                    .toLocalDate();
            AlternationDirectory.Axis axis = byDay.computeIfAbsent(day,
                    date -> alternationDirectory.resolveClassAxis(target.classPublicId(), date));
            if (axis == AlternationDirectory.Axis.COMPANY) {
                a.addWarning(PlanningIssueCodes.ALTERNATION_COMPANY_PERIOD, "session_date",
                        a.rawDate,
                        "Ce jour est résolu en période d'entreprise pour la classe. "
                                + "Vérifiez qu'il s'agit bien d'une séance exceptionnelle.");
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
