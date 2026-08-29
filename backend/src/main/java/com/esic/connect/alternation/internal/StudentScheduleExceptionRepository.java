package com.esic.connect.alternation.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentScheduleExceptionRepository
        extends JpaRepository<StudentScheduleException, Long>, JpaSpecificationExecutor<StudentScheduleException> {

    Optional<StudentScheduleException> findByPublicId(UUID publicId);

    Page<StudentScheduleException> findByEnrollmentId(Long enrollmentId, Pageable pageable);

    /**
     * Exceptions {@code ACTIVE} de l'inscription dont l'intervalle
     * {@code [startAt, endAt)} recoupe la période demandée. Sert le
     * pré-contrôle de non-chevauchement (par type) et la résolution
     * effective à une date.
     */
    @Query("""
            select e from StudentScheduleException e
            where e.enrollmentId = :enrollmentId
              and e.status = com.esic.connect.alternation.internal.ScheduleExceptionStatus.ACTIVE
              and e.startAt < :rangeEnd
              and e.endAt > :rangeStart
            """)
    List<StudentScheduleException> findActiveOverlapping(@Param("enrollmentId") Long enrollmentId,
                                                         @Param("rangeStart") Instant rangeStart,
                                                         @Param("rangeEnd") Instant rangeEnd);
}
