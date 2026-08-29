package com.esic.connect.alternation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ClassWorkStudyPatternRepository
        extends JpaRepository<ClassWorkStudyPattern, Long>, JpaSpecificationExecutor<ClassWorkStudyPattern> {

    Optional<ClassWorkStudyPattern> findByPublicId(UUID publicId);

    List<ClassWorkStudyPattern> findByClassGroupIdAndStatusOrderByValidFromAsc(
            Long classGroupId, ClassPatternStatus status);

    /**
     * Prochaine affectation historisée de la classe strictement après
     * {@code validFrom} (tous statuts confondus : l'historique clôturé
     * compte aussi). Déterministe : tri {@code valid_from} puis {@code id}.
     * Sert à borner la {@code effectiveDate} d'une clôture pour ne pas
     * produire un historique qui se chevauche.
     */
    Optional<ClassWorkStudyPattern> findFirstByClassGroupIdAndValidFromGreaterThanOrderByValidFromAscIdAsc(
            Long classGroupId, LocalDate validFrom);

    /**
     * Affectations {@code ACTIVE} de la classe qui recouvrent la date
     * fournie (bornes inclusives ; {@code valid_until} nul = ouvert). Il
     * ne doit en exister qu'une au plus — l'invariant de non-chevauchement
     * est garanti à l'écriture.
     */
    @Query("""
            select c from ClassWorkStudyPattern c
            where c.classGroupId = :classGroupId
              and c.status = com.esic.connect.alternation.internal.ClassPatternStatus.ACTIVE
              and c.validFrom <= :on
              and (c.validUntil is null or c.validUntil >= :on)
            """)
    List<ClassWorkStudyPattern> findActiveCovering(@Param("classGroupId") Long classGroupId,
                                                   @Param("on") LocalDate on);

    /**
     * Affectations {@code ACTIVE} de la classe dont la période
     * (inclusive, {@code valid_until} nul = +infini) recoupe l'intervalle
     * demandé — sert le pré-contrôle de non-chevauchement. Deux périodes
     * strictement adjacentes ({@code [a,b]} puis {@code [b+1,c]}) ne
     * recoupent pas et sont donc autorisées.
     */
    @Query("""
            select c from ClassWorkStudyPattern c
            where c.classGroupId = :classGroupId
              and c.status = com.esic.connect.alternation.internal.ClassPatternStatus.ACTIVE
              and c.validFrom <= :rangeEndOrMax
              and (c.validUntil is null or c.validUntil >= :rangeStart)
            """)
    List<ClassWorkStudyPattern> findActiveOverlapping(@Param("classGroupId") Long classGroupId,
                                                      @Param("rangeStart") LocalDate rangeStart,
                                                      @Param("rangeEndOrMax") LocalDate rangeEndOrMax);
}
