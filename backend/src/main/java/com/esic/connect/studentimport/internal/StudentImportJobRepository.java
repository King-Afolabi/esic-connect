package com.esic.connect.studentimport.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

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
}
