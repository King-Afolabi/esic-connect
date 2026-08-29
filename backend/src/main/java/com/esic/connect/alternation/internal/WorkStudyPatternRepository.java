package com.esic.connect.alternation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface WorkStudyPatternRepository
        extends JpaRepository<WorkStudyPattern, Long>, JpaSpecificationExecutor<WorkStudyPattern> {

    Optional<WorkStudyPattern> findByPublicId(UUID publicId);

    boolean existsByCode(String code);
}
