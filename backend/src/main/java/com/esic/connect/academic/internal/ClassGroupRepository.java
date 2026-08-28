package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface ClassGroupRepository extends JpaRepository<ClassGroup, Long>,
        JpaSpecificationExecutor<ClassGroup> {

    Optional<ClassGroup> findByPublicId(UUID publicId);

    boolean existsByPromotionIdAndCode(Long promotionId, String code);

    boolean existsByPromotionIdAndStatus(Long promotionId, AcademicStatus status);

    boolean existsByProgramLevelIdAndStatus(Long programLevelId, AcademicStatus status);
}
