package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface StudentGroupRepository
        extends JpaRepository<StudentGroup, Long>, JpaSpecificationExecutor<StudentGroup> {

    Optional<StudentGroup> findByPublicId(UUID publicId);

    boolean existsByCodeAndAcademicYearId(String code, Long academicYearId);
}
