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

    /**
     * Brouillon de calendrier ouvert d'une classe pour un auteur
     * (EF-PLAN-006). Le nom de source distingue un travail saisi d'un
     * travail issu d'un fichier : les deux partagent la table, pas
     * l'origine.
     */
    Optional<PlanningImportJob>
            findFirstByClassGroupIdAndAcademicYearIdAndOriginalFileNameAndRequestedByIdAndStatusOrderByIdDesc(
            Long classGroupId, Long academicYearId, String originalFileName, Long requestedById,
            PlanningImportJobStatus status);
}
