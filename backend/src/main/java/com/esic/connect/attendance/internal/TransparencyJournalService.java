package com.esic.connect.attendance.internal;

import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.coursesession.CourseSessionDirectory.CheckpointRef;
import com.esic.connect.coursesession.CourseSessionDirectory.SessionRef;
import com.esic.connect.enrollment.EnrollmentDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Journal de transparence des présences d'un apprenant (EF-ATT-014 ;
 * docs/02 §5.7, §34.2).
 *
 * <p>Le cahier accorde à l'apprenant le droit de « consulter le journal de
 * transparence de ses présences ». Ce service en donne la lecture stricte :
 * <strong>tout</strong> ce qui a touché ses présences — son propre
 * émargement compris — avec le quand, le par quel canal, le à quel titre
 * et le pourquoi.
 *
 * <p>Trois bornes, toutes délibérées :
 *
 * <ul>
 *   <li>l'apprenant est résolu <strong>depuis le seul JWT</strong> : aucun
 *       identifiant d'apprenant n'est accepté du client, sans quoi la
 *       route deviendrait un moyen de lire le journal d'autrui
 *       (AC-017) ;</li>
 *   <li>les auteurs sont désignés par leur <strong>fonction</strong>, et
 *       {@code SELF} pour l'apprenant lui-même. Le nom d'un agent n'ajoute
 *       aucun droit à l'apprenant, il ajoute une donnée personnelle
 *       (docs/02 §14) ;</li>
 *   <li>le journal est <strong>en lecture seule et dérivé</strong> : il ne
 *       persiste rien. Une table de journal serait une seconde vérité à
 *       maintenir cohérente avec l'historique append-only qui existe
 *       déjà.</li>
 * </ul>
 */
@Service
class TransparencyJournalService {

    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final EarlyDepartureRepository earlyDepartureRepository;
    private final AttendanceActorResolver actorResolver;

    TransparencyJournalService(CourseSessionDirectory courseSessionDirectory,
                               EnrollmentDirectory enrollmentDirectory,
                               AttendanceRecordRepository recordRepository,
                               AttendanceCorrectionRepository correctionRepository,
                               EarlyDepartureRepository earlyDepartureRepository,
                               AttendanceActorResolver actorResolver) {
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.recordRepository = recordRepository;
        this.correctionRepository = correctionRepository;
        this.earlyDepartureRepository = earlyDepartureRepository;
        this.actorResolver = actorResolver;
    }

    @Transactional(readOnly = true)
    PageResponse<TransparencyEntry> journal(String studentSubject, Instant from, Instant to,
                                            int page, int size) {
        UUID userPublicId = parseUuid(studentSubject);
        List<EnrollmentDirectory.EnrollmentRef> enrollments =
                enrollmentDirectory.findEnrollmentsForUser(userPublicId);
        if (enrollments.isEmpty()) {
            return PageResponse.ofList(List.of(), page, size);
        }

        Map<UUID, EnrollmentDirectory.EnrollmentRef> byClass = new HashMap<>();
        Set<Long> enrollmentIds = new HashSet<>();
        for (EnrollmentDirectory.EnrollmentRef enrollment : enrollments) {
            enrollmentIds.add(enrollment.internalId());
            if (enrollment.classGroupPublicId() != null) {
                byClass.putIfAbsent(enrollment.classGroupPublicId(), enrollment);
            }
        }

        List<SessionRef> sessions =
                courseSessionDirectory.findSessionsForClasses(byClass.keySet(), from, to);
        Map<Long, SessionRef> sessionByCheckpoint = new HashMap<>();
        Map<Long, CheckpointRef> checkpointById = new HashMap<>();
        Map<Long, SessionRef> sessionByInternalId = new HashMap<>();
        for (SessionRef session : sessions) {
            sessionByInternalId.put(session.internalId(), session);
            for (CheckpointRef checkpoint : session.checkpoints()) {
                sessionByCheckpoint.put(checkpoint.internalId(), session);
                checkpointById.put(checkpoint.internalId(), checkpoint);
            }
        }

        AttendanceActorResolver.Lookup actors = actorResolver.lookup();
        List<TransparencyEntry> entries = new ArrayList<>();
        appendAttendanceEvents(enrollmentIds, sessionByCheckpoint, checkpointById, actors, entries);
        appendEarlyDepartureEvents(enrollmentIds, sessionByInternalId, actors, entries);

        entries.sort(Comparator.comparing(TransparencyEntry::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResponse.ofList(List.copyOf(entries), page, size);
    }

    // ------------------------------------------------------------------

    private void appendAttendanceEvents(Set<Long> enrollmentIds,
                                        Map<Long, SessionRef> sessionByCheckpoint,
                                        Map<Long, CheckpointRef> checkpointById,
                                        AttendanceActorResolver.Lookup actors,
                                        List<TransparencyEntry> entries) {
        List<AttendanceRecord> records =
                recordRepository.findByEnrollmentIdInOrderByRecordedAtDesc(enrollmentIds).stream()
                        .filter(record -> sessionByCheckpoint.containsKey(record.getAttendanceCheckpointId()))
                        .toList();
        if (records.isEmpty()) {
            return;
        }
        Map<Long, AttendanceRecord> recordById = new HashMap<>();
        for (AttendanceRecord record : records) {
            recordById.put(record.getId(), record);
            SessionRef session = sessionByCheckpoint.get(record.getAttendanceCheckpointId());
            CheckpointRef checkpoint = checkpointById.get(record.getAttendanceCheckpointId());

            // L'émargement lui-même : le fait que l'apprenant est le
            // premier à devoir pouvoir vérifier.
            //
            // C'est le CANAL qui dit qui a agi, pas la colonne auteur :
            // sur un émargement porté par l'apprenant, `recorded_by_id`
            // reste nul — personne n'a enregistré pour lui. Se fier à cette
            // colonne ferait passer chaque émargement pour une saisie
            // manuelle.
            boolean selfRecorded = record.getSource() != AttendanceRecordSource.MANUAL
                    && record.getSource() != AttendanceRecordSource.CORRECTION;
            entries.add(new TransparencyEntry(
                    record.getRecordedAt(),
                    selfRecorded ? TransparencyEvent.RECORDED.name()
                            : TransparencyEvent.CREATED_MANUALLY.name(),
                    selfRecorded ? TransparencyEntry.SELF : actors.role(record.getRecordedById()),
                    record.getSource() == null ? null : record.getSource().name(),
                    session.publicId(), session.title(), session.startsAt(),
                    checkpoint == null ? null : checkpoint.label(),
                    null, record.getStatus() == null ? null : record.getStatus().name(),
                    null, record.getLateMinutes(), record.getComment()));
        }

        // Une seule requête pour tout l'historique : une par présence
        // serait proportionnelle à l'affichage (NFR-PERF-08).
        for (AttendanceCorrection correction
                : correctionRepository.findByAttendanceRecordIdInOrderByOccurredAtDescIdDesc(
                        recordById.keySet())) {
            AttendanceRecord record = recordById.get(correction.getAttendanceRecordId());
            if (record == null) {
                continue;
            }
            SessionRef session = sessionByCheckpoint.get(record.getAttendanceCheckpointId());
            CheckpointRef checkpoint = checkpointById.get(record.getAttendanceCheckpointId());
            entries.add(new TransparencyEntry(
                    correction.getOccurredAt(),
                    correction.getAction().name(),
                    actors.role(correction.getActorUserId()),
                    null,
                    session.publicId(), session.title(), session.startsAt(),
                    checkpoint == null ? null : checkpoint.label(),
                    correction.getPreviousStatus() == null ? null : correction.getPreviousStatus().name(),
                    correction.getNewStatus() == null ? null : correction.getNewStatus().name(),
                    correction.getPreviousLateMinutes(), correction.getNewLateMinutes(),
                    correction.getReason()));
        }
    }

    private void appendEarlyDepartureEvents(Set<Long> enrollmentIds,
                                            Map<Long, SessionRef> sessionByInternalId,
                                            AttendanceActorResolver.Lookup actors,
                                            List<TransparencyEntry> entries) {
        for (EarlyDeparture departure
                : earlyDepartureRepository.findByEnrollmentIdInOrderByRequestedAtDesc(enrollmentIds)) {
            SessionRef session = sessionByInternalId.get(departure.getCourseSessionId());
            if (session == null) {
                continue;
            }
            entries.add(new TransparencyEntry(
                    departure.getRequestedAt(),
                    TransparencyEvent.EARLY_DEPARTURE_DECLARED.name(),
                    TransparencyEntry.SELF, null,
                    session.publicId(), session.title(), session.startsAt(), null,
                    null, null, null, null, departure.getReason()));
            if (departure.getTeacherOpinionAt() != null) {
                entries.add(new TransparencyEntry(
                        departure.getTeacherOpinionAt(),
                        TransparencyEvent.EARLY_DEPARTURE_FORWARDED.name(),
                        "TEACHER", null,
                        session.publicId(), session.title(), session.startsAt(), null,
                        null, departure.getTeacherOpinion() == null ? null
                                : departure.getTeacherOpinion().name(),
                        null, null, departure.getTeacherOpinionComment()));
            }
            if (departure.getDecidedAt() != null) {
                entries.add(new TransparencyEntry(
                        departure.getDecidedAt(),
                        TransparencyEvent.EARLY_DEPARTURE_DECIDED.name(),
                        actors.role(departure.getDecidedById()), null,
                        session.publicId(), session.title(), session.startsAt(), null,
                        null, departure.getStatus().name(), null, null,
                        departure.getDecisionComment()));
            }
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }
    }
}
