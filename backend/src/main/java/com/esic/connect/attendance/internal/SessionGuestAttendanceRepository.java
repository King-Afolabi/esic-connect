package com.esic.connect.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SessionGuestAttendanceRepository extends JpaRepository<SessionGuestAttendance, Long> {

    Optional<SessionGuestAttendance> findByPublicId(UUID publicId);

    List<SessionGuestAttendance> findByCourseSessionIdOrderByRecordedAtAsc(Long courseSessionId);
}
