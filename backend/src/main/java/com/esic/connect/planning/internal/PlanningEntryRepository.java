package com.esic.connect.planning.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlanningEntryRepository extends JpaRepository<PlanningEntry, Long> {

    Optional<PlanningEntry> findByPublicId(UUID publicId);

    List<PlanningEntry> findByPlanningVersion_IdOrderByStartsAtAsc(Long versionId);

    List<PlanningEntry> findByPlanningScheduleIdOrderByStartsAtAsc(Long scheduleId);
}
