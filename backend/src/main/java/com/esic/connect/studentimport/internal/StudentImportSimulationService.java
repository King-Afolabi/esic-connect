package com.esic.connect.studentimport.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.studentimport.internal.PlannedActionResolver.RowResolution;
import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 de l'import — <strong>simulation</strong> (rapport §6, §4.4).
 * Lit le fichier, normalise et valide chaque ligne, dé-duplique dans le
 * fichier, résout la classe et le compte via les <em>ports publics</em>,
 * calcule {@code planned_action} et persiste le tout dans les seules
 * tables {@code student_import_*} — <strong>aucune écriture métier</strong>
 * (invariant T1). N'émet aucun e-mail et ne publie aucun événement
 * d'audit sur ce checkpoint (l'audit {@code STUDENT_IMPORT_SIMULATED}
 * relève du checkpoint audit).
 */
@Service
class StudentImportSimulationService {

    private final StudentImportProperties properties;
    private final Clock clock;
    private final StudentImportJobRepository jobRepository;
    private final StudentImportJobIssueRepository jobIssueRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentImportRowIssueRepository rowIssueRepository;
    private final AcademicScopeDirectory academicScopeDirectory;
    private final PlannedActionResolver plannedActionResolver;

    StudentImportSimulationService(StudentImportProperties properties,
                                   Clock clock,
                                   StudentImportJobRepository jobRepository,
                                   StudentImportJobIssueRepository jobIssueRepository,
                                   StudentImportRowRepository rowRepository,
                                   StudentImportRowIssueRepository rowIssueRepository,
                                   AcademicScopeDirectory academicScopeDirectory,
                                   PlannedActionResolver plannedActionResolver) {
        this.properties = properties;
        this.clock = clock;
        this.jobRepository = jobRepository;
        this.jobIssueRepository = jobIssueRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.academicScopeDirectory = academicScopeDirectory;
        this.plannedActionResolver = plannedActionResolver;
    }

    @Transactional
    StudentImportJob simulate(SimulationCommand command) {
        boolean scopeFilterRequested = notBlank(command.scopeProgramCode()) || notBlank(command.scopeClassCode());
        if (scopeFilterRequested && !academicScopeDirectory.hasGlobalScope()) {
            // Un appelant limité à son périmètre ne pose pas de filtre de job : ses lignes
            // hors périmètre sont de toute façon marquées ERROR ligne par ligne (rapport §9).
            throw new StudentImportException(StudentImportException.Kind.SCOPE_FORBIDDEN);
        }

        String content = CsvFileGuard.decodeAndValidate(command.fileName(), command.contentType(),
                command.content(), properties.maxFileBytes());
        ParsedCsv parsed = CsvParser.parse(content, properties.maxRows());
        guardStructure(parsed);

        Instant now = clock.instant();
        StudentImportJob job = jobRepository.save(new StudentImportJob(
                CsvValueNormalizer.sanitizeFileName(command.fileName()),
                CsvValueNormalizer.sha256Hex(command.content()),
                command.content().length,
                parsed.separator(),
                command.requesterInternalId(),
                now,
                now.plus(properties.simulationTtl())));
        job.applyScope(CsvValueNormalizer.trimToNull(command.scopeProgramCode()),
                CsvValueNormalizer.trimToNull(command.scopeClassCode()));

        recordGlobalHeaderNotices(job, parsed);

        List<NormalizedRow> normalized = parsed.rows().stream()
                .map(row -> CsvRowNormalizer.normalize(parsed, row))
                .toList();
        Map<Integer, List<RowIssueDraft>> duplicateIssues = FileDuplicateDetector.detect(normalized);

        Counters counters = new Counters();
        for (NormalizedRow normalizedRow : normalized) {
            List<RowIssueDraft> issues = new ArrayList<>(StudentImportFieldValidator.validate(normalizedRow));
            issues.addAll(duplicateIssues.getOrDefault(normalizedRow.rowNumber(), List.of()));
            boolean alreadyInError = issues.stream().anyMatch(RowIssueDraft::isError);

            RowResolution resolution = plannedActionResolver.resolve(normalizedRow, alreadyInError);
            issues.addAll(resolution.issues());

            StudentImportRowStatus status = StudentImportFieldValidator.statusFrom(issues);
            persistRow(job, normalizedRow, status, resolution, issues);
            counters.tally(status, resolution.plannedAction());
        }

        job.recordSimulation(counters.total, counters.valid, counters.warning, counters.error, 0,
                counters.plannedCreate, counters.plannedUpdate, counters.plannedTransfer, counters.plannedNoop);
        return jobRepository.save(job);
    }

    // ------------------------------------------------------------------

    private static void guardStructure(ParsedCsv parsed) {
        if (parsed.header().isEmpty()) {
            throw new StudentImportException(StudentImportException.Kind.HEADER_UNREADABLE);
        }
        if (parsed.tooManyRows()) {
            throw new StudentImportException(StudentImportException.Kind.TOO_MANY_ROWS);
        }
        if (!parsed.missingMandatoryNames().isEmpty()) {
            throw new StudentImportException(StudentImportException.Kind.MISSING_COLUMN,
                    List.copyOf(parsed.missingMandatoryNames()));
        }
        if (parsed.noDataRows()) {
            throw new StudentImportException(StudentImportException.Kind.NO_DATA_ROWS);
        }
    }

    private void recordGlobalHeaderNotices(StudentImportJob job, ParsedCsv parsed) {
        parsed.ignoredColumnNames().forEach(name -> jobIssueRepository.save(new StudentImportJobIssue(job,
                StudentImportIssueSeverity.WARNING, StudentImportIssueCodes.COLUMN_IGNORED,
                "La colonne « " + name + " » n'est pas gérée par cette tranche et sera ignorée.", name)));
        parsed.unknownColumnNames().forEach(name -> jobIssueRepository.save(new StudentImportJobIssue(job,
                StudentImportIssueSeverity.WARNING, StudentImportIssueCodes.UNKNOWN_COLUMN,
                "La colonne « " + name + " » est inconnue et sera ignorée.", name)));
    }

    private void persistRow(StudentImportJob job, NormalizedRow normalizedRow, StudentImportRowStatus status,
                            RowResolution resolution, List<RowIssueDraft> issues) {
        StudentImportRow row = new StudentImportRow(job, normalizedRow.rowNumber(), status,
                resolution.plannedAction());
        row.setNormalizedIdentity(normalizedRow.lastName(), normalizedRow.firstName(),
                normalizedRow.email(), normalizedRow.phone());
        row.setNormalizedTarget(normalizedRow.formationCode(), normalizedRow.classCode(),
                normalizedRow.academicYear());
        row.setNormalizedOptional(normalizedRow.studentNumber(), normalizedRow.birthDate(),
                normalizedRow.workStudy(), normalizedRow.companyName());
        row.setResolution(resolution.resolvedClassPublicId(), resolution.resolvedUserPublicId(),
                resolution.resolvedEnrollmentPublicId());
        row.setStudentNumberGenerated(resolution.studentNumberGenerated());
        StudentImportRow saved = rowRepository.save(row);
        issues.forEach(draft -> rowIssueRepository.save(new StudentImportRowIssue(saved, draft.severity(),
                draft.code(), draft.message(), draft.columnName(), draft.receivedValue(), draft.suggestedValue())));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Compteurs du bilan de simulation (rapport §6, AC-004). */
    private static final class Counters {
        int total;
        int valid;
        int warning;
        int error;
        int plannedCreate;
        int plannedUpdate;
        int plannedTransfer;
        int plannedNoop;

        void tally(StudentImportRowStatus status, StudentImportPlannedAction action) {
            total++;
            switch (status) {
                case VALID -> valid++;
                case WARNING -> warning++;
                case ERROR -> error++;
            }
            switch (action) {
                case CREATE_ACCOUNT_AND_ENROLL -> plannedCreate++;
                case ENROLL_EXISTING, UPDATE_PROFILE -> plannedUpdate++;
                case TRANSFER_CLASS -> plannedTransfer++;
                case NONE -> plannedNoop++;
            }
        }
    }

    /**
     * @param fileName             nom d'origine (assaini par le service)
     * @param contentType          type MIME déclaré, éventuellement {@code null}
     * @param content              octets bruts reçus (non conservés — seule l'empreinte l'est)
     * @param requesterInternalId  auteur de la demande
     * @param scopeProgramCode     filtre de périmètre éventuel
     * @param scopeClassCode       filtre de périmètre éventuel
     */
    record SimulationCommand(
            String fileName,
            String contentType,
            byte[] content,
            Long requesterInternalId,
            String scopeProgramCode,
            String scopeClassCode) {
    }
}
