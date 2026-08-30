package com.esic.connect.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByAttendanceCheckpointIdOrderByRecordedAtAsc(Long attendanceCheckpointId);

    boolean existsByAttendanceCheckpointIdAndEnrollmentId(Long attendanceCheckpointId, Long enrollmentId);
}
