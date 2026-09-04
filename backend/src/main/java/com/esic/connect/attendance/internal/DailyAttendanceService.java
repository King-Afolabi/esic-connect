package com.esic.connect.attendance.internal;

import com.esic.connect.academic.AcademicScopeDirectory;
import com.esic.connect.alternation.AlternationDirectory;
import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.attendance.DailyAttendanceResult;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.AttendanceCheckpointType;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Résultat journalier d'assiduité (EF-ATT-004 ; docs/02 §16.3).
 *
 * <p>Le cahier donne une table de vérité fondée sur les <strong>quatre
 * points de contrôle nommés</strong> : le matin est validé quand
 * {@code MORNING_ARRIVAL} et {@code MORNING_BREAK_RETURN} le sont de façon
 * cohérente, l'après-midi de même, et quatre validations cohérentes font
 * une journée complète.
 *
 * <p>« De façon cohérente » est la partie que le cahier laisse implicite.
 * Elle est tranchée ici : un <em>retour de pause sans l'arrivée qui le
 * précède</em> n'est pas une demi-journée, c'est une incohérence — elle
 * produit {@code TO_CONFIRM} et appelle un humain. L'inverse (arrivée sans
 * retour de pause) est simplement incomplet : {@code PARTIAL}.
 *
 * <p>Deux valeurs sont ajoutées à la table du cahier, qui suppose une
 * journée attendue : {@code COMPANY} (RG-028 — une période en entreprise
 * n'est jamais une absence) et {@code NOT_EXPECTED} (aucune séance ce
 * jour-là). Sans elles, ces deux cas tomberaient dans {@code ABSENT}.
 */
@Service
class DailyAttendanceService {

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AlternationDirectory alternationDirectory;
    private final AcademicScopeDirectory academicScope;
    private final AttendanceRecordRepository recordRepository;

    DailyAttendanceService(CourseSessionDirectory courseSessionDirectory,
                           EnrollmentDirectory enrollmentDirectory,
                           AlternationDirectory alternationDirectory,
                           AcademicScopeDirectory academicScope,
                           AttendanceRecordRepository recordRepository) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.alternationDirectory = alternationDirectory;
        this.academicScope = academicScope;
        this.recordRepository = recordRepository;
    }

    /** Une ligne par apprenant de la classe pour la journée demandée. */
    record DailyRow(
            UUID enrollmentPublicId,
            UUID studentProfilePublicId,
            String studentNumber,
            String firstName,
            String lastName,
            DailyAttendanceResult result,
            boolean morningValidated,
            boolean afternoonValidated,
            int lateCount,
            List<String> expectedCheckpoints,
            List<String> validatedCheckpoints) {
    }

    record DailyReport(
            UUID classGroupPublicId,
            LocalDate date,
            String alternationContext,
            int expectedCheckpointCount,
            List<DailyRow> rows) {
    }

    @Transactional(readOnly = true)
    DailyReport compute(UUID classGroupPublicId, LocalDate date, ZoneId zone) {
        if (classGroupPublicId == null || date == null) {
            throw new AttendanceException(AttendanceException.Kind.INVALID_SUBMISSION);
        }
        if (!academicScope.hasGlobalScope() && !academicScope.isClassInScope(classGroupPublicId)) {
            // Hors périmètre : l'existence même de la classe est une
            // information à protéger (docs/02 §18.2).
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }

        ZoneId effectiveZone = zone == null ? ZoneId.of("Europe/Paris") : zone;
        Instant from = date.atStartOfDay(effectiveZone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(effectiveZone).toInstant();

        AlternationDirectory.Axis axis =
                alternationDirectory.resolveClassAxis(classGroupPublicId, date);

        List<EnrollmentDirectory.RosterEntry> roster =
                enrollmentDirectory.findActiveRosterForClasses(Set.of(classGroupPublicId));

        // Points journaliers nommés, actifs, attendus ce jour-là.
        Map<AttendanceCheckpointType, Long> expected = new EnumMap<>(AttendanceCheckpointType.class);
        for (CourseSessionDirectory.SessionRef session
                : courseSessionDirectory.findSessionsForClasses(Set.of(classGroupPublicId), from, to)) {
            for (CourseSessionDirectory.CheckpointRef checkpoint : session.checkpoints()) {
                if (!checkpoint.type().isDaily()
                        || checkpoint.status() == AttendanceCheckpointStatus.CANCELLED
                        || !checkpoint.required()) {
                    continue;
                }
                expected.putIfAbsent(checkpoint.type(), checkpoint.internalId());
            }
        }

        Map<Long, AttendanceCheckpointType> typeByCheckpointId = new HashMap<>();
        expected.forEach((type, id) -> typeByCheckpointId.put(id, type));

        Map<Long, Set<AttendanceCheckpointType>> validatedByEnrollment = new HashMap<>();
        Map<Long, Set<AttendanceCheckpointType>> excusedByEnrollment = new HashMap<>();
        Map<Long, Integer> lateByEnrollment = new HashMap<>();
        if (!typeByCheckpointId.isEmpty()) {
            for (AttendanceRecord record
                    : recordRepository.findByAttendanceCheckpointIdIn(typeByCheckpointId.keySet())) {
                AttendanceCheckpointType type =
                        typeByCheckpointId.get(record.getAttendanceCheckpointId());
                if (type == null || record.getStatus() == AttendanceStatus.CANCELLED) {
                    continue;
                }
                if (record.getStatus() == AttendanceStatus.PRESENT
                        || record.getStatus() == AttendanceStatus.LATE) {
                    validatedByEnrollment
                            .computeIfAbsent(record.getEnrollmentId(), key -> new HashSet<>())
                            .add(type);
                    if (record.getStatus() == AttendanceStatus.LATE) {
                        lateByEnrollment.merge(record.getEnrollmentId(), 1, Integer::sum);
                    }
                } else if (record.getStatus() == AttendanceStatus.EXCUSED_ABSENCE) {
                    excusedByEnrollment
                            .computeIfAbsent(record.getEnrollmentId(), key -> new HashSet<>())
                            .add(type);
                }
            }
        }

        List<DailyRow> rows = new ArrayList<>();
        for (EnrollmentDirectory.RosterEntry entry : roster) {
            Set<AttendanceCheckpointType> validated = validatedByEnrollment
                    .getOrDefault(entry.enrollmentInternalId(), Set.of());
            Set<AttendanceCheckpointType> excused = excusedByEnrollment
                    .getOrDefault(entry.enrollmentInternalId(), Set.of());
            DailyAttendanceResult result = classify(expected.keySet(), validated, excused, axis);
            rows.add(new DailyRow(entry.enrollmentPublicId(), entry.studentProfilePublicId(),
                    entry.studentNumber(), entry.firstName(), entry.lastName(), result,
                    halfDayValidated(expected.keySet(), validated, true),
                    halfDayValidated(expected.keySet(), validated, false),
                    lateByEnrollment.getOrDefault(entry.enrollmentInternalId(), 0),
                    names(expected.keySet()), names(validated)));
        }

        return new DailyReport(classGroupPublicId, date, axis.name(), expected.size(), rows);
    }

    // ------------------------------------------------------------------

    /**
     * Table de vérité de docs/02 §16.3, appliquée aux seuls points
     * réellement attendus ce jour-là.
     */
    private static DailyAttendanceResult classify(Set<AttendanceCheckpointType> expected,
                                                  Set<AttendanceCheckpointType> validated,
                                                  Set<AttendanceCheckpointType> excused,
                                                  AlternationDirectory.Axis axis) {
        if (axis == AlternationDirectory.Axis.COMPANY) {
            // RG-028 : jamais une absence, quelle que soit la suite.
            return DailyAttendanceResult.COMPANY;
        }
        if (expected.isEmpty()) {
            return DailyAttendanceResult.NOT_EXPECTED;
        }
        if (isIncoherent(expected, validated)) {
            return DailyAttendanceResult.TO_CONFIRM;
        }

        boolean morning = halfDayValidated(expected, validated, true);
        boolean afternoon = halfDayValidated(expected, validated, false);
        boolean morningExpected = expected.stream().anyMatch(AttendanceCheckpointType::isMorning);
        boolean afternoonExpected = expected.stream().anyMatch(AttendanceCheckpointType::isAfternoon);

        if (morningExpected && afternoonExpected && morning && afternoon) {
            return DailyAttendanceResult.FULL_DAY;
        }
        if (morning && !afternoonExpected) {
            return DailyAttendanceResult.MORNING;
        }
        if (afternoon && !morningExpected) {
            return DailyAttendanceResult.AFTERNOON;
        }
        if (morning || afternoon) {
            // Une demi-journée validée quand les deux étaient attendues :
            // le cahier ne parle pas de « demi-journée » dans ce cas, il
            // parle de validations incomplètes.
            return DailyAttendanceResult.PARTIAL;
        }
        if (!validated.isEmpty()) {
            return DailyAttendanceResult.PARTIAL;
        }
        if (!excused.isEmpty()) {
            // Un justificatif accepté transforme ABSENT en EXCUSED, il ne
            // crée jamais une présence (RG-086, RG-087).
            return DailyAttendanceResult.EXCUSED;
        }
        return DailyAttendanceResult.ABSENT;
    }

    /**
     * Incohérence : un retour de pause validé sans l'arrivée attendue qui
     * le précède. Le cahier exige des validations « cohérentes » sans les
     * définir ; c'est la seule lecture qui donne un sens à
     * {@code TO_CONFIRM} (docs/02 §16.3).
     */
    private static boolean isIncoherent(Set<AttendanceCheckpointType> expected,
                                        Set<AttendanceCheckpointType> validated) {
        return brokenSequence(expected, validated, AttendanceCheckpointType.MORNING_ARRIVAL,
                AttendanceCheckpointType.MORNING_BREAK_RETURN)
                || brokenSequence(expected, validated, AttendanceCheckpointType.AFTERNOON_ARRIVAL,
                AttendanceCheckpointType.AFTERNOON_BREAK_RETURN);
    }

    private static boolean brokenSequence(Set<AttendanceCheckpointType> expected,
                                          Set<AttendanceCheckpointType> validated,
                                          AttendanceCheckpointType arrival,
                                          AttendanceCheckpointType breakReturn) {
        return expected.contains(arrival) && validated.contains(breakReturn)
                && !validated.contains(arrival);
    }

    private static boolean halfDayValidated(Set<AttendanceCheckpointType> expected,
                                            Set<AttendanceCheckpointType> validated,
                                            boolean morning) {
        Set<AttendanceCheckpointType> half = expected.stream()
                .filter(type -> morning ? type.isMorning() : type.isAfternoon())
                .collect(java.util.stream.Collectors.toCollection(
                        () -> EnumSet.noneOf(AttendanceCheckpointType.class)));
        return !half.isEmpty() && validated.containsAll(half);
    }

    private static List<String> names(Set<AttendanceCheckpointType> types) {
        return types.stream().map(Enum::name).sorted().toList();
    }
}
