package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface StudentProfileRepository
        extends JpaRepository<StudentProfile, Long>, JpaSpecificationExecutor<StudentProfile> {

    Optional<StudentProfile> findByPublicId(UUID publicId);

    Optional<StudentProfile> findByUserId(Long userId);

    Optional<StudentProfile> findByStudentNumberIgnoreCase(String studentNumber);

    boolean existsByUserId(Long userId);

    boolean existsByStudentNumberIgnoreCase(String studentNumber);
}
