package com.esic.connect.planning.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PlanningScheduleRepository extends JpaRepository<PlanningSchedule, Long> {

    Optional<PlanningSchedule> findByPublicId(UUID publicId);

    Optional<PlanningSchedule> findByClassGroupIdAndAcademicYearId(Long classGroupId, Long academicYearId);

    /** Verrou de ligne pour la publication (DEC-G1-003, {@code SELECT ... FOR UPDATE}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlanningSchedule s where s.id = :id")
    Optional<PlanningSchedule> findByIdForUpdate(Long id);
}
