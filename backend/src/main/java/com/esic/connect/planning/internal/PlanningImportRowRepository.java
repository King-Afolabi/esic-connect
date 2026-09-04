package com.esic.connect.planning.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlanningImportRowRepository extends JpaRepository<PlanningImportRow, Long> {

    List<PlanningImportRow> findByJob_IdOrderByRowNumberAsc(Long jobId);

    Page<PlanningImportRow> findByJob_Id(Long jobId, Pageable pageable);

    Optional<PlanningImportRow> findByPublicId(UUID publicId);

    long countByJob_Id(Long jobId);

    @Modifying
    void deleteByJob_Id(Long jobId);
}
