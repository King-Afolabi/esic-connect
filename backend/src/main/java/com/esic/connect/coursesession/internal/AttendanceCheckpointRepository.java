package com.esic.connect.coursesession.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AttendanceCheckpointRepository extends JpaRepository<AttendanceCheckpoint, Long> {

    Optional<AttendanceCheckpoint> findByCourseSessionId(Long courseSessionId);
}
