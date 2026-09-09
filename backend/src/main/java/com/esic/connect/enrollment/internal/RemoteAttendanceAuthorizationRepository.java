package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RemoteAttendanceAuthorizationRepository
        extends JpaRepository<RemoteAttendanceAuthorization, Long> {

    Optional<RemoteAttendanceAuthorization> findByPublicId(UUID publicId);

    List<RemoteAttendanceAuthorization> findByStudentUserIdOrderByValidFromDesc(Long studentUserId);

    /**
     * Autorisations d'un apprenant couvrant une date : {@code ACTIVE} et
     * intervalle englobant. Le filtre de classe est appliqué en mémoire —
     * une autorisation générale ({@code class_group_id} nul) couvre toutes
     * les classes, ce qu'un simple {@code =} en SQL n'exprimerait pas.
     */
    @org.springframework.data.jpa.repository.Query("""
            select authorization from RemoteAttendanceAuthorization authorization
            where authorization.studentUserId = :studentUserId
              and authorization.status = com.esic.connect.enrollment.internal.RemoteAuthorizationStatus.ACTIVE
              and authorization.validFrom <= :date
              and (authorization.validUntil is null or authorization.validUntil >= :date)
            """)
    List<RemoteAttendanceAuthorization> findCovering(
            @org.springframework.data.repository.query.Param("studentUserId") Long studentUserId,
            @org.springframework.data.repository.query.Param("date") LocalDate date);
}
