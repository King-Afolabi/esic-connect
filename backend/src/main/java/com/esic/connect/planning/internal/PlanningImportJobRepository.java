package com.esic.connect.planning.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlanningImportJobRepository
        extends JpaRepository<PlanningImportJob, Long>, JpaSpecificationExecutor<PlanningImportJob> {

    Optional<PlanningImportJob> findByPublicId(UUID publicId);

    /** Verrou de ligne pour la publication (DEC-G1-003). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PlanningImportJob j where j.id = :id")
    Optional<PlanningImportJob> findByIdForUpdate(Long id);

    List<PlanningImportJob> findByStatusAndExpiresAtBefore(PlanningImportJobStatus status, Instant cutoff);

    Page<PlanningImportJob> findAll(org.springframework.data.domain.Pageable pageable);
}
