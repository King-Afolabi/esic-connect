package com.esic.connect.coursesession.internal;

import com.esic.connect.coursesession.SessionLifecycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CourseSessionRepository
        extends JpaRepository<CourseSession, Long>, JpaSpecificationExecutor<CourseSession> {

    Optional<CourseSession> findByPublicId(UUID publicId);

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
