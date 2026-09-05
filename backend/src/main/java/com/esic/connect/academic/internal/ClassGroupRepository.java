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
     * Résout un lot de classes par identifiants publics en une requête
     * (bloc G1-F : résolution des codes de classe d'un tableau de bord
     * sans une requête par ligne). Les identifiants inconnus sont ignorés.
     */
    List<ClassGroup> findByPublicIdIn(Collection<UUID> publicIds);

    /**
     * Classes portant ce code (sans tenir compte de la casse) — le code
     * n'est unique que dans une promotion, l'import doit donc désambiguïser
     * par formation puis par année ({@code resolveForImport}).
     */
    List<ClassGroup> findByCodeIgnoreCase(String code);

    /** Recherche globale (EF-USER-009) : code ou nom contenant le fragment. */
    @Query("""
            SELECT c FROM ClassGroup c
            WHERE LOWER(c.code) LIKE :pattern OR LOWER(c.name) LIKE :pattern
            ORDER BY c.code ASC
            """)
    List<ClassGroup> search(@Param("pattern") String pattern,
                            org.springframework.data.domain.Pageable pageable);

    @Query("""
            SELECT c FROM ClassGroup c
            WHERE c.id IN :ids AND (LOWER(c.code) LIKE :pattern OR LOWER(c.name) LIKE :pattern)
            ORDER BY c.code ASC
            """)
    List<ClassGroup> searchWithin(@Param("pattern") String pattern,
                                  @Param("ids") Collection<Long> ids,
                                  org.springframework.data.domain.Pageable pageable);

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
