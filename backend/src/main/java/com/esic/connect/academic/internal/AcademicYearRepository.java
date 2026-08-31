package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface AcademicYearRepository extends JpaRepository<AcademicYear, Long>,
        JpaSpecificationExecutor<AcademicYear> {

    Optional<AcademicYear> findByPublicId(UUID publicId);

    Optional<AcademicYear> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);
}
