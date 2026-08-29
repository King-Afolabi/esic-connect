package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface EnrollmentRepository
        extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {

    Optional<Enrollment> findByPublicId(UUID publicId);

    boolean existsByStudentProfileIdAndAcademicYearIdAndStatus(Long studentProfileId, Long academicYearId,
                                                              EnrollmentStatus status);
}
