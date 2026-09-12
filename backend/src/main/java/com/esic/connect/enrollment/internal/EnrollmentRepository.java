package com.esic.connect.enrollment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EnrollmentRepository
        extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {

    Optional<Enrollment> findByPublicId(UUID publicId);

    List<Enrollment> findByUserIdAndStatus(Long userId, EnrollmentStatus status);

    List<Enrollment> findByUserId(Long userId);

    /** Toutes les inscriptions (tous statuts) d'un lot de comptes, en une requête (anti-N+1). */
    List<Enrollment> findByUserIdIn(Collection<Long> userIds);

    /** Décompte borné des inscriptions d'un compte (comparaison de doublons, ANO-USER-001). */
    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, EnrollmentStatus status);

    long countByClassGroupIdInAndStatus(Collection<Long> classGroupIds, EnrollmentStatus status);

    List<Enrollment> findByClassGroupIdInAndStatus(Collection<Long> classGroupIds, EnrollmentStatus status);

    /**
     * Identifiants internes distincts des <strong>comptes</strong> ayant
     * une inscription au statut donné dans l'une des classes indiquées —
     * filtre de périmètre pédagogique (un {@code PEDAGOGICAL_MANAGER} ne
     * voit que les apprenants de ses classes). Utilisé pour l'écran «
     * Apprenants » (rôle {@code STUDENT}) et, par traduction vers des
     * identifiants de profil, pour la liste des profils apprenants.
     */
    @Query("""
            SELECT DISTINCT e.userId FROM Enrollment e
            WHERE e.status = :status AND e.classGroupId IN :classGroupIds
            """)
    List<Long> findUserIdsByClassGroupIdInAndStatus(
            @Param("classGroupIds") Collection<Long> classGroupIds,
            @Param("status") EnrollmentStatus status);

    boolean existsByUserIdAndAcademicYearIdAndStatus(Long userId, Long academicYearId, EnrollmentStatus status);

    /**
     * Inscriptions actives des comptes indiqués — utilisée notamment pour
     * la recherche d'apprenants par nom ou numéro étudiant (EF-USER-009),
     * les deux étant résolus par le port {@code identity.UserDirectory}
     * (numéro étudiant et nom vivent tous deux sur {@code user_account},
     * refonte 2026-09) avant ce simple filtrage par compte.
     */
    List<Enrollment> findByUserIdInAndStatus(java.util.Collection<Long> userIds, EnrollmentStatus status);
}