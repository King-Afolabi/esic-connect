package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface PromotionRepository extends JpaRepository<Promotion, Long>, JpaSpecificationExecutor<Promotion> {

    Optional<Promotion> findByPublicId(UUID publicId);

    boolean existsByProgramIdAndAcademicYearIdAndCode(Long programId, Long academicYearId, String code);

    boolean existsByProgramIdAndStatus(Long programId, AcademicStatus status);

    boolean existsByAcademicYearIdAndStatus(Long academicYearId, AcademicStatus status);

    /**
     * Une promotion de cette année scolaire commence avant {@code date} ?
     * Les promotions sans période renseignée ({@code start_date IS NULL})
     * sont exclues par la sémantique SQL de la comparaison.
     */
    boolean existsByAcademicYearIdAndStartDateBefore(Long academicYearId, LocalDate date);

    /** Une promotion de cette année scolaire se termine après {@code date} ? */
    boolean existsByAcademicYearIdAndEndDateAfter(Long academicYearId, LocalDate date);
}
