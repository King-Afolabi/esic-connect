package com.esic.connect.studentimport.internal;

import com.esic.connect.studentimport.StudentImportChangeAction;
import com.esic.connect.studentimport.StudentImportChangeEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.esic.connect.studentimport.internal.StudentImportIssueDrafts.RowIssueDraft;

/**
 * Correction d'une ligne en anomalie <strong>avant</strong> confirmation
 * (EF-IMP-006 ; docs/02 §13.6 : « corriger une ligne en anomalie sans
 * recommencer l'import »).
 *
 * <p>La correction ne réécrit pas le fichier — il n'est jamais conservé
 * (RG-036). Elle modifie les valeurs <em>normalisées</em> déjà
 * persistées, puis <strong>rejoue exactement la même validation</strong>
 * que la simulation : mêmes contrôles de champ, même résolution d'action
 * planifiée. C'est le seul moyen d'éviter qu'une ligne corrigée suive un
 * chemin différent d'une ligne d'origine.
 *
 * <p>Après chaque correction, la synthèse du travail d'import est
 * recalculée à partir de <em>toutes</em> ses lignes : le compteur
 * d'erreurs et le caractère confirmable reflètent l'état réel, pas
 * l'état au moment du téléversement.
 *
 * <p>La détection de doublons intra-fichier n'est en revanche pas
 * rejouée ligne à ligne : elle porte sur l'ensemble du lot. Une
 * correction qui créerait un doublon est donc rattrapée à la
 * confirmation, qui revalide tout sous verrou — c'est là que se prend la
 * décision d'écrire, et c'est là que le contrôle doit être définitif.
 */
@Service
public class StudentImportRowCorrectionService {

    /** Champs corrigeables, avec leur nom canonique tel qu'il figure dans les en-têtes. */
    private static final List<String> CORRECTABLE_FIELDS = List.of(
            "last_name", "first_name", "email", "phone",
            "formation_code", "class_code", "academic_year",
            "student_number", "birth_date", "work_study", "company_name");

    private final StudentImportJobRepository jobRepository;
    private final StudentImportRowRepository rowRepository;
    private final StudentImportRowIssueRepository rowIssueRepository;
    private final StudentImportRowCorrectionRepository correctionRepository;
    private final PlannedActionResolver plannedActionResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    StudentImportRowCorrectionService(StudentImportJobRepository jobRepository,
                                      StudentImportRowRepository rowRepository,
                                      StudentImportRowIssueRepository rowIssueRepository,
                                      StudentImportRowCorrectionRepository correctionRepository,
                                      PlannedActionResolver plannedActionResolver,
                                      ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.jobRepository = jobRepository;
        this.rowRepository = rowRepository;
        this.rowIssueRepository = rowIssueRepository;
        this.correctionRepository = correctionRepository;
        this.plannedActionResolver = plannedActionResolver;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Applique des corrections à une ligne et rejoue sa validation.
     *
     * @param corrections valeurs par champ canonique ; une valeur vide
     *                    efface le champ, ce qui est une correction
     *                    légitime (une colonne renseignée par erreur)
     */
    @Transactional
    public StudentImportRow correct(UUID jobPublicId, UUID rowPublicId,
                                    Map<String, String> corrections, Long actorInternalId) {
        StudentImportJob job = jobRepository.findByPublicId(jobPublicId)
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.JOB_NOT_FOUND));
        Instant now = clock.instant();
        if (job.getStatus() != StudentImportJobStatus.SIMULATED) {
            // Un travail confirmé, annulé ou expiré n'est plus modifiable :
            // corriger après coup laisserait croire qu'on peut changer ce
            // qui a déjà été écrit.
            throw new StudentImportException(StudentImportException.Kind.JOB_NOT_SIMULATED);
        }
        if (job.isExpiredAt(now)) {
            throw new StudentImportException(StudentImportException.Kind.JOB_EXPIRED);
        }

        StudentImportRow row = rowRepository.findByPublicId(rowPublicId)
                .filter(candidate -> candidate.getJob().getId().equals(job.getId()))
                .orElseThrow(() -> new StudentImportException(StudentImportException.Kind.ROW_NOT_FOUND));

        Map<String, String> applied = applyCorrections(row, corrections, actorInternalId, now);
        if (applied.isEmpty()) {
            // Rien n'a changé : ne pas produire de trace d'audit ni de
            // révalidation inutile.
            return row;
        }

        revalidate(job, row);
        recomputeJobCounters(job);

        eventPublisher.publishEvent(new StudentImportChangeEvent(job.getPublicId(), actorInternalId,
                StudentImportChangeAction.ROW_CORRECTED,
                "job=" + job.getPublicId() + ";row=" + row.getRowNumber()
                        + ";fields=" + String.join(",", applied.keySet())));
        return row;
    }

    /** Historique des corrections d'une ligne, du plus ancien au plus récent. */
    @Transactional(readOnly = true)
    public List<StudentImportRowCorrection> history(Long rowId) {
        return correctionRepository.findByRowIdOrderByCorrectedAtAsc(rowId);
    }

    // ------------------------------------------------------------------

    /** @return les champs réellement modifiés, avec leur nouvelle valeur */
    private Map<String, String> applyCorrections(StudentImportRow row, Map<String, String> corrections,
                                                 Long actorInternalId, Instant now) {
        Map<String, String> applied = new LinkedHashMap<>();
        NormalizedRow before = NormalizedRow.fromPersistedRow(row);

        String lastName = pick(corrections, "last_name", before.lastName());
        String firstName = pick(corrections, "first_name", before.firstName());
        String email = lower(pick(corrections, "email", before.email()));
        String phone = pick(corrections, "phone", before.phone());
        String formationCode = upper(pick(corrections, "formation_code", before.formationCode()));
        String classCode = upper(pick(corrections, "class_code", before.classCode()));
        String academicYear = pick(corrections, "academic_year", before.academicYear());
        String studentNumber = upper(pick(corrections, "student_number", before.studentNumber()));
        LocalDate birthDate = parseDate(corrections, before.birthDate());
        Boolean workStudy = parseBoolean(corrections, before.workStudy());
        String companyName = pick(corrections, "company_name", before.companyName());

        record Change(String field, String previous, String next) {
        }
        List<Change> changes = new ArrayList<>();
        changes.add(new Change("last_name", before.lastName(), lastName));
        changes.add(new Change("first_name", before.firstName(), firstName));
        changes.add(new Change("email", before.email(), email));
        changes.add(new Change("phone", before.phone(), phone));
        changes.add(new Change("formation_code", before.formationCode(), formationCode));
        changes.add(new Change("class_code", before.classCode(), classCode));
        changes.add(new Change("academic_year", before.academicYear(), academicYear));
        changes.add(new Change("student_number", before.studentNumber(), studentNumber));
        changes.add(new Change("birth_date", text(before.birthDate()), text(birthDate)));
        changes.add(new Change("work_study", text(before.workStudy()), text(workStudy)));
        changes.add(new Change("company_name", before.companyName(), companyName));

        for (Change change : changes) {
            if (!java.util.Objects.equals(change.previous(), change.next())) {
                correctionRepository.save(new StudentImportRowCorrection(row, change.field(),
                        change.previous(), change.next(), actorInternalId, now));
                applied.put(change.field(), change.next());
            }
        }
        if (applied.isEmpty()) {
            return applied;
        }

        row.setNormalizedIdentity(lastName, firstName, email, phone);
        row.setNormalizedTarget(formationCode, classCode, academicYear);
        row.setNormalizedOptional(studentNumber, birthDate, workStudy, companyName);
        return applied;
    }

    /** Rejoue la validation de la ligne, exactement comme à la simulation. */
    private void revalidate(StudentImportJob job, StudentImportRow row) {
        NormalizedRow normalized = NormalizedRow.fromPersistedRow(row);
        List<RowIssueDraft> issues = new ArrayList<>(StudentImportFieldValidator.validate(normalized));
        boolean alreadyInError = issues.stream().anyMatch(RowIssueDraft::isError);
        PlannedActionResolver.RowResolution resolution =
                plannedActionResolver.resolve(normalized, alreadyInError);
        issues.addAll(resolution.issues());

        row.setRowStatus(StudentImportFieldValidator.statusFrom(issues));
        row.setPlannedAction(resolution.plannedAction());
        row.setResolution(resolution.resolvedClassPublicId(), resolution.resolvedUserPublicId(),
                resolution.resolvedEnrollmentPublicId());
        row.setStudentNumberGenerated(resolution.studentNumberGenerated());
        rowRepository.save(row);

        // Les anomalies précédentes ne valent plus rien : les conserver
        // ferait apparaître une ligne corrigée comme toujours fautive.
        rowIssueRepository.deleteByRowId(row.getId());
        rowIssueRepository.flush();
        issues.forEach(draft -> rowIssueRepository.save(new StudentImportRowIssue(row, draft.severity(),
                draft.code(), draft.message(), draft.columnName(), draft.receivedValue(),
                draft.suggestedValue())));
    }

    /**
     * Recalcule la synthèse du travail à partir de toutes ses lignes.
     * Sans cela, corriger la dernière ligne fautive laisserait le travail
     * marqué non confirmable.
     */
    private void recomputeJobCounters(StudentImportJob job) {
        List<StudentImportRow> rows = rowRepository.findByJobId(job.getId());
        int valid = 0;
        int warning = 0;
        int error = 0;
        int create = 0;
        int update = 0;
        int transfer = 0;
        int noop = 0;
        for (StudentImportRow candidate : rows) {
            switch (candidate.getRowStatus()) {
                case VALID -> valid++;
                case WARNING -> warning++;
                case ERROR -> error++;
            }
            switch (candidate.getPlannedAction()) {
                case CREATE_ACCOUNT_AND_ENROLL, ENROLL_EXISTING -> create++;
                case UPDATE_PROFILE -> update++;
                case TRANSFER_CLASS -> transfer++;
                case NONE -> noop++;
            }
        }
        job.recordSimulation(rows.size(), valid, warning, error, 0, create, update, transfer, noop);
        jobRepository.save(job);
    }

    private static String pick(Map<String, String> corrections, String field, String current) {
        if (!corrections.containsKey(field)) {
            return current;
        }
        String value = corrections.get(field);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static LocalDate parseDate(Map<String, String> corrections, LocalDate current) {
        if (!corrections.containsKey("birth_date")) {
            return current;
        }
        String value = corrections.get("birth_date");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException malformed) {
            throw new StudentImportException(StudentImportException.Kind.CORRECTION_INVALID_VALUE,
                    List.of("birth_date"));
        }
    }

    private static Boolean parseBoolean(Map<String, String> corrections, Boolean current) {
        if (!corrections.containsKey("work_study")) {
            return current;
        }
        String value = corrections.get("work_study");
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "oui", "1", "o", "yes" -> Boolean.TRUE;
            case "false", "non", "0", "n", "no" -> Boolean.FALSE;
            default -> throw new StudentImportException(
                    StudentImportException.Kind.CORRECTION_INVALID_VALUE, List.of("work_study"));
        };
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Champs que l'API accepte de corriger ; tout autre nom est refusé. */
    static void requireKnownFields(Map<String, String> corrections) {
        List<String> unknown = corrections.keySet().stream()
                .filter(field -> !CORRECTABLE_FIELDS.contains(field))
                .toList();
        if (!unknown.isEmpty()) {
            throw new StudentImportException(StudentImportException.Kind.CORRECTION_UNKNOWN_FIELD,
                    unknown);
        }
    }
}
