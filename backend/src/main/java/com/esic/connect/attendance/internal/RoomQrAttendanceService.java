package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.coursesession.AttendanceCheckpointStatus;
import com.esic.connect.coursesession.CourseSessionDirectory;
import com.esic.connect.enrollment.EnrollmentDirectory;
import com.esic.connect.identity.UserDirectory;
import com.esic.connect.organization.RoomDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Émargement par le QR <strong>fixe de salle</strong> (EF-ATT-010) sous
 * contrôle de plage réseau (EF-ATT-008 ; docs/02 §16.6 et §16.7).
 *
 * <p>Le QR identifie une <strong>ressource de salle</strong>, jamais une
 * séance : le serveur détermine la salle, puis la séance active ou
 * imminente pour cet apprenant, son inscription et la fenêtre applicable.
 * C'est ce qui permet d'imprimer une affiche une fois pour toutes.
 *
 * <p>Trois refus, tous exigés par le cahier :
 * <ul>
 *   <li>hors plage réseau de l'établissement — le QR est public, seule
 *       l'origine réseau atteste d'une présence sur site ;</li>
 *   <li><strong>après le début</strong> de la séance (RG-051) : au-delà,
 *       c'est le QR dynamique du formateur, sous son contrôle ;</li>
 *   <li>aucune séance correspondante dans la fenêtre.</li>
 * </ul>
 *
 * <p>L'adresse IP est utilisée <strong>pendant la décision uniquement</strong>
 * (RG-094) : elle n'est ni persistée, ni écrite dans l'audit métier, ni
 * renvoyée dans la réponse ou l'erreur.
 */
@Service
class RoomQrAttendanceService {

    private final RoomDirectory roomDirectory;
    private final CourseSessionDirectory courseSessionDirectory;
    private final EnrollmentDirectory enrollmentDirectory;
    private final UserDirectory userDirectory;
    private final AttendanceRecordRepository recordRepository;
    private final AttendanceRecordPersister recordPersister;
    private final AttendanceChangePublisher changePublisher;
    private final Clock clock;
    private final Duration openBefore;

    RoomQrAttendanceService(RoomDirectory roomDirectory,
                            CourseSessionDirectory courseSessionDirectory,
                            EnrollmentDirectory enrollmentDirectory,
                            UserDirectory userDirectory,
                            AttendanceRecordRepository recordRepository,
                            AttendanceRecordPersister recordPersister,
                            AttendanceChangePublisher changePublisher,
                            Clock clock,
                            @Value("${app.attendance.room-qr-open-before:PT15M}") Duration openBefore) {
        if (openBefore == null || openBefore.isNegative()) {
            throw new IllegalStateException(
                    "app.attendance.room-qr-open-before doit être une durée non négative.");
        }
        this.roomDirectory = roomDirectory;
        this.courseSessionDirectory = courseSessionDirectory;
        this.enrollmentDirectory = enrollmentDirectory;
        this.userDirectory = userDirectory;
        this.recordRepository = recordRepository;
        this.recordPersister = recordPersister;
        this.changePublisher = changePublisher;
        this.clock = clock;
        this.openBefore = openBefore;
    }

    @Transactional
    AttendanceRecordResponse validate(AttendanceRequests.ValidateRoomQr request,
                                      String callerSubject, String callerIpAddress) {
        String reference = request.roomReference() == null ? null : request.roomReference().trim();
        RoomDirectory.RoomRef room = roomDirectory.findActiveByStaticQrReference(reference)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.ROOM_QR_UNKNOWN));

        // Contrôle réseau AVANT toute autre décision : inutile de révéler
        // qu'une séance existe à quelqu'un qui n'est pas sur le réseau.
        if (!roomDirectory.isWithinAuthorizedRange(room.sitePublicId(), callerIpAddress)) {
            throw new AttendanceException(AttendanceException.Kind.ROOM_QR_OUT_OF_NETWORK);
        }

        UUID callerPublicId = parseSubject(callerSubject);
        UserDirectory.UserRef account = userDirectory.findByPublicId(callerPublicId)
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN));
        if (account.archived()) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }

        Instant now = clock.instant();
        // Fenêtre de recherche large côté requête, resserrée ensuite par
        // les règles : une séance du jour suffit à couvrir les cas.
        Instant from = now.minus(Duration.ofHours(12));
        Instant to = now.plus(Duration.ofHours(12));

        List<EnrollmentDirectory.EnrollmentRef> enrollments = enrollmentDirectory
                .findActiveEnrollmentsForUserOn(callerPublicId, LocalDate.ofInstant(now, ZoneId.of("UTC")));
        if (enrollments.isEmpty()) {
            // Une seconde tentative à la date locale de la salle serait plus
            // juste, mais la couverture d'inscription est bornée au jour :
            // on élargit d'un jour de part et d'autre plutôt que de deviner.
            enrollments = java.util.stream.Stream.of(
                            LocalDate.ofInstant(now.minus(Duration.ofDays(1)), ZoneId.of("UTC")),
                            LocalDate.ofInstant(now.plus(Duration.ofDays(1)), ZoneId.of("UTC")))
                    .flatMap(date -> enrollmentDirectory
                            .findActiveEnrollmentsForUserOn(callerPublicId, date).stream())
                    .distinct()
                    .toList();
        }
        if (enrollments.isEmpty()) {
            throw new AttendanceException(AttendanceException.Kind.NOT_ENROLLED);
        }

        Set<UUID> classPublicIds = enrollments.stream()
                .map(EnrollmentDirectory.EnrollmentRef::classGroupPublicId)
                .collect(Collectors.toSet());

        // Séances de CES classes, dans CETTE salle, dont la fenêtre de QR
        // fixe est ouverte : de `openBefore` avant le début jusqu'au début.
        Optional<CourseSessionDirectory.SessionRef> candidate = courseSessionDirectory
                .findSessionsForClasses(classPublicIds, from, to).stream()
                .filter(session -> room.code().equalsIgnoreCase(session.roomCode()))
                .filter(session -> !now.isBefore(session.startsAt().minus(openBefore)))
                .filter(session -> !now.isAfter(session.startsAt()))
                .min(Comparator.comparing(CourseSessionDirectory.SessionRef::startsAt));

        if (candidate.isEmpty()) {
            // Distinguer « pas de séance » de « trop tard » aide
            // l'apprenant sans rien révéler d'une autre classe.
            boolean startedAlready = courseSessionDirectory
                    .findSessionsForClasses(classPublicIds, from, to).stream()
                    .filter(session -> room.code().equalsIgnoreCase(session.roomCode()))
                    .anyMatch(session -> now.isAfter(session.startsAt()));
            throw new AttendanceException(startedAlready
                    ? AttendanceException.Kind.ROOM_QR_SESSION_STARTED
                    : AttendanceException.Kind.ROOM_QR_NO_SESSION);
        }

        CourseSessionDirectory.SessionRef session = candidate.get();
        CourseSessionDirectory.CheckpointRef checkpoint = session.checkpoints().stream()
                .filter(cp -> cp.status() != AttendanceCheckpointStatus.CANCELLED)
                .filter(CourseSessionDirectory.CheckpointRef::isOpen)
                .min(Comparator.comparingInt(CourseSessionDirectory.CheckpointRef::displayOrder))
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.SESSION_CLOSED));

        EnrollmentDirectory.EnrollmentRef enrollment = enrollments.stream()
                .filter(candidateEnrollment -> session.classGroupPublicIds()
                        .contains(candidateEnrollment.classGroupPublicId()))
                .findFirst()
                .orElseThrow(() -> new AttendanceException(AttendanceException.Kind.NOT_ENROLLED));

        if (recordRepository.existsByAttendanceCheckpointIdAndEnrollmentId(
                checkpoint.internalId(), enrollment.internalId())) {
            throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
        }

        // Le QR fixe n'est utilisable QUE jusqu'au début : par construction
        // l'apprenant n'est jamais en retard par ce canal.
        AttendanceRecord record = new AttendanceRecord(checkpoint.internalId(), enrollment.internalId(),
                account.internalId(), null, now, AttendanceRecordSource.ROOM_STATIC_QR,
                AttendanceStatus.PRESENT, null, null);
        AttendanceRecord saved;
        try {
            saved = recordPersister.persist(record);
        } catch (DataIntegrityViolationException violation) {
            if (AttendanceRecordPersister.isDuplicateAttendanceViolation(violation)) {
                throw new AttendanceException(AttendanceException.Kind.ALREADY_RECORDED);
            }
            throw violation;
        }

        Long actorId = changePublisher.actorId(callerSubject);
        // Détail d'audit SANS adresse IP (RG-094) : la salle suffit à
        // reconstituer le contexte.
        changePublisher.publishRecorded(saved.getPublicId(), actorId,
                "session=" + session.publicId() + ";checkpoint=" + checkpoint.publicId()
                        + ";source=ROOM_STATIC_QR;room=" + room.code());
        return new AttendanceRecordResponse(saved.getPublicId(), session.publicId(),
                checkpoint.publicId(), session.title(), AttendanceStatus.PRESENT, null, false,
                saved.getRecordedAt(), AttendanceRecordSource.ROOM_STATIC_QR);
    }

    private static UUID parseSubject(String callerSubject) {
        try {
            return UUID.fromString(callerSubject);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AttendanceException(AttendanceException.Kind.OPERATION_FORBIDDEN);
        }
    }
}
