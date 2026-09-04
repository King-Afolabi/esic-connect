package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.EarlyDepartureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EarlyDepartureRepository extends JpaRepository<EarlyDeparture, Long> {

    Optional<EarlyDeparture> findByPublicId(UUID publicId);

    List<EarlyDeparture> findByCourseSessionIdOrderByRequestedAtAsc(Long courseSessionId);

    List<EarlyDeparture> findByEnrollmentIdInOrderByRequestedAtDesc(Collection<Long> enrollmentIds);

    /**
     * Un dossier encore ouvert interdit d'en déposer un second sur la même
     * séance : deux demandes concurrentes appelleraient deux décisions
     * pour un seul départ.
     */
    boolean existsByCourseSessionIdAndEnrollmentIdAndStatusIn(
            Long courseSessionId, Long enrollmentId, Collection<EarlyDepartureStatus> statuses);

    /** Dossiers d'un apprenant dont le départ tombe dans la fenêtre demandée. */
    List<EarlyDeparture> findByEnrollmentIdInAndDepartureAtBetween(
            Collection<Long> enrollmentIds, Instant from, Instant to);
}
