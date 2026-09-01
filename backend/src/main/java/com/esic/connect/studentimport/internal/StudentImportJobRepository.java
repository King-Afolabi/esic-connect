package com.esic.connect.studentimport.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentImportJobRepository
        extends JpaRepository<StudentImportJob, Long>, JpaSpecificationExecutor<StudentImportJob> {

    Optional<StudentImportJob> findByPublicId(UUID publicId);

    /**
     * Verrou pessimiste sur le job pour la confirmation (rapport §4.4,
     * §11 : {@code SELECT ... FOR UPDATE}) — sérialise deux confirmations
     * concurrentes du même job ; le perdant relit l'état {@code APPLIED}
     * et renvoie le bilan mémorisé (idempotence, invariant T6).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentImportJob> findWithLockByPublicId(UUID publicId);

    // --- Purge planifiée (rapport §12.C, §7.6) ---

    List<StudentImportJob> findByStatusInAndExpiresAtBefore(Collection<StudentImportJobStatus> statuses,
                                                            Instant cutoff);

    List<StudentImportJob> findByStatusAndCreatedAtBefore(StudentImportJobStatus status, Instant cutoff);

    List<StudentImportJob> findByStatusAndConfirmedAtBefore(StudentImportJobStatus status, Instant cutoff);

    /** Derniers jobs, du plus récent au plus ancien — borné par un {@code Pageable} (bloc G1-F). */
    List<StudentImportJob> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
