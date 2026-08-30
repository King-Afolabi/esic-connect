package com.esic.connect.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    List<AttendanceRecord> findByAttendanceCheckpointIdOrderByRecordedAtAsc(Long attendanceCheckpointId);

    boolean existsByAttendanceCheckpointIdAndEnrollmentId(Long attendanceCheckpointId, Long enrollmentId);

    Optional<AttendanceRecord> findByPublicId(UUID publicId);

    Optional<AttendanceRecord> findByAttendanceCheckpointIdAndEnrollmentId(Long attendanceCheckpointId,
                                                                          Long enrollmentId);

    List<AttendanceRecord> findByEnrollmentIdInOrderByRecordedAtDesc(Collection<Long> enrollmentIds);

    /** Présences de plusieurs points de contrôle pour plusieurs inscriptions (rapports, vue apprenant). */
    List<AttendanceRecord> findByAttendanceCheckpointIdInAndEnrollmentIdIn(
            Collection<Long> attendanceCheckpointIds, Collection<Long> enrollmentIds);

    List<AttendanceRecord> findByAttendanceCheckpointIdIn(Collection<Long> attendanceCheckpointIds);
}
