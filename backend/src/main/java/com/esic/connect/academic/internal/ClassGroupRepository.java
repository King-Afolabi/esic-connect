package com.esic.connect.academic.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ClassGroupRepository extends JpaRepository<ClassGroup, Long>,
        JpaSpecificationExecutor<ClassGroup> {

    Optional<ClassGroup> findByPublicId(UUID publicId);

    /**
     * Classes portant ce code (sans tenir compte de la casse) — le code
     * n'est unique que dans une promotion, l'import doit donc désambiguïser
     * par formation puis par année ({@code resolveForImport}).
     */
    List<ClassGroup> findByCodeIgnoreCase(String code);

    boolean existsByPromotionIdAndCode(Long promotionId, String code);

    boolean existsByPromotionIdAndStatus(Long promotionId, AcademicStatus status);

    boolean existsByProgramLevelIdAndStatus(Long programLevelId, AcademicStatus status);

    /**
     * Identifiants internes des classes rattachées (via leur promotion) à
     * l'une des formations fournies. Sert le filtrage d'une liste d'un
     * autre module par le périmètre pédagogique de l'appelant, via le port
     * {@link com.esic.connect.academic.AcademicScopeDirectory}.
     */
    @Query("select cg.id from ClassGroup cg where cg.promotion.program.id in :programIds")
    List<Long> findIdsByProgramIdIn(@Param("programIds") Collection<Long> programIds);
}
