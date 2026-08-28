package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface ProgramLevelRepository extends JpaRepository<ProgramLevel, Long>,
        JpaSpecificationExecutor<ProgramLevel> {

    Optional<ProgramLevel> findByPublicId(UUID publicId);

    boolean existsByProgramIdAndCode(Long programId, String code);

    boolean existsByProgramIdAndStatus(Long programId, AcademicStatus status);
}
