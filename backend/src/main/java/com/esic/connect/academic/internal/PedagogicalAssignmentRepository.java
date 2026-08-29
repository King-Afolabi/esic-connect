package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PedagogicalAssignmentRepository
        extends JpaRepository<PedagogicalAssignment, Long>, JpaSpecificationExecutor<PedagogicalAssignment> {

    Optional<PedagogicalAssignment> findByPublicId(UUID publicId);

    boolean existsByProgramIdAndAssignmentRoleAndStatus(Long programId, PedagogicalAssignmentRole assignmentRole,
                                                        PedagogicalAssignmentStatus status);

    /**
     * Accès effectif à une formation : une affectation {@code ACTIVE} du
     * responsable dont la période (bornes inclusives) couvre le jour
     * fourni. Sert le contrôle de périmètre unitaire.
     */
    @Query("""
            select (count(pa) > 0) from PedagogicalAssignment pa
            where pa.program.id = :programId
              and pa.managerUserId = :managerId
              and pa.status = :status
              and pa.validFrom <= :on
              and (pa.validUntil is null or pa.validUntil >= :on)
            """)
    boolean existsEffectiveScope(@Param("programId") Long programId,
                                 @Param("managerId") Long managerId,
                                 @Param("status") PedagogicalAssignmentStatus status,
                                 @Param("on") LocalDate on);

    /**
     * Identifiants des formations du périmètre effectif du responsable au
     * jour fourni. Sert le filtrage des listes académiques.
     */
    @Query("""
            select distinct pa.program.id from PedagogicalAssignment pa
            where pa.managerUserId = :managerId
              and pa.status = :status
              and pa.validFrom <= :on
              and (pa.validUntil is null or pa.validUntil >= :on)
            """)
    List<Long> findScopedProgramIds(@Param("managerId") Long managerId,
                                    @Param("status") PedagogicalAssignmentStatus status,
                                    @Param("on") LocalDate on);
}
