package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CourseSessionRepository
        extends JpaRepository<CourseSession, Long>, JpaSpecificationExecutor<CourseSession> {

    Optional<CourseSession> findByPublicId(UUID publicId);

    /**
     * Verrou de ligne sur la séance — sérialise les opérations
     * concurrentes qui dépendent d'un invariant « au plus un … par
     * séance » (G1-C.2 : au plus une substitution {@code ACTIVE}
     * applicable). {@code SELECT … FOR UPDATE}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CourseSession s where s.id = :id")
    Optional<CourseSession> findByIdForUpdate(@Param("id") Long id);

    /**
     * Séance d'origine planning liée à un créneau stable précis
     * (identité inter-versions — idempotence de la publication,
     * DEC-G1-002).
     */
    Optional<CourseSession> findByPlanningSlotPublicId(UUID planningSlotPublicId);

    /**
     * Séances d'origine planning d'une classe donnée (jointure
     * {@code session_class}) — utilisé pour la supersession des créneaux
     * retirés d'une nouvelle version.
     */
    @Query("select distinct s from CourseSession s join s.classes c "
            + "where c.classGroupId = :classGroupId and s.planningSlotPublicId is not null "
            + "and s.status = :status and s.supersededByScheduling = false")
    List<CourseSession> findPlanningSessionsForClass(@Param("classGroupId") Long classGroupId,
                                                    @Param("status") SessionLifecycle status);
}
