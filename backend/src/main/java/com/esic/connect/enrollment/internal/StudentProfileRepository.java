package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StudentProfileRepository
        extends JpaRepository<StudentProfile, Long>, JpaSpecificationExecutor<StudentProfile> {

    Optional<StudentProfile> findByPublicId(UUID publicId);

    Optional<StudentProfile> findByUserId(Long userId);

    /**
     * Résout un lot de profils par compte en <strong>une</strong> requête
     * (anti-N+1, NFR-PERF-08) — une page d'inscriptions ou de la liste des
     * apprenants ne doit pas payer une requête de profil par ligne. Un
     * compte sans profil est simplement absent du résultat : ce n'est
     * jamais une erreur (le profil est facultatif, refonte 2026-09).
     */
    List<StudentProfile> findByUserIdIn(Collection<Long> userIds);

    /** Identifiants internes de profil pour un lot de comptes (traduction compte -> profil). */
    @Query("select p.id from StudentProfile p where p.userId in :userIds")
    List<Long> findIdsByUserIdIn(@Param("userIds") Collection<Long> userIds);

    Optional<StudentProfile> findByStudentNumberIgnoreCase(String studentNumber);

    boolean existsByUserId(Long userId);

    boolean existsByStudentNumberIgnoreCase(String studentNumber);
}
