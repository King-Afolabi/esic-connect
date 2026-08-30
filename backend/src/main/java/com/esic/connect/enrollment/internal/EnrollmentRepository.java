package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EnrollmentRepository
        extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {

    Optional<Enrollment> findByPublicId(UUID publicId);

    List<Enrollment> findByStudentProfile_UserIdAndStatus(Long userId, EnrollmentStatus status);

    List<Enrollment> findByStudentProfile_UserId(Long userId);

    long countByClassGroupIdInAndStatus(Collection<Long> classGroupIds, EnrollmentStatus status);

    List<Enrollment> findByClassGroupIdInAndStatus(Collection<Long> classGroupIds, EnrollmentStatus status);

    boolean existsByStudentProfileIdAndAcademicYearIdAndStatus(Long studentProfileId, Long academicYearId,
                                                              EnrollmentStatus status);
}
