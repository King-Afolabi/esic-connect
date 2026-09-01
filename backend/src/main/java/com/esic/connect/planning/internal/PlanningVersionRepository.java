package com.esic.connect.planning.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlanningVersionRepository extends JpaRepository<PlanningVersion, Long> {

    Optional<PlanningVersion> findByPublicId(UUID publicId);

    List<PlanningVersion> findBySchedule_IdOrderByVersionNumberDesc(Long scheduleId);

    Page<PlanningVersion> findBySchedule_Id(Long scheduleId, Pageable pageable);

    Optional<PlanningVersion> findFirstBySchedule_IdAndStatusOrderByVersionNumberDesc(
            Long scheduleId, PlanningVersionStatus status);
}
