package com.esic.connect.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AttendanceJustificationRepository
        extends JpaRepository<AttendanceJustification, Long>, JpaSpecificationExecutor<AttendanceJustification> {

    Optional<AttendanceJustification> findByPublicId(UUID publicId);

    List<AttendanceJustification> findByAttendanceRecordIdOrderBySubmittedAtDesc(Long attendanceRecordId);

    boolean existsByAttendanceRecordIdAndStatusNot(Long attendanceRecordId, JustificationStatus status);
}
