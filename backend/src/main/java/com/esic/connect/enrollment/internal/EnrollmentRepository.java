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

    List<Enrollment> findByStudentProfile_PublicIdAndStatus(UUID studentProfilePublicId, EnrollmentStatus status);

    List<Enrollment> findByStudentProfile_UserIdAndStatus(Long userId, EnrollmentStatus status);

    List<Enrollment> findByStudentProfile_UserId(Long userId);

    long countByClassGroupIdInAndStatus(Collection<Long> classGroupIds, EnrollmentStatus status);

    List<Enrollment> findByClassGroupIdInAndStatus(Collection<Long> classGroupIds, EnrollmentStatus status);

    boolean existsByStudentProfileIdAndAcademicYearIdAndStatus(Long studentProfileId, Long academicYearId,
                                                              EnrollmentStatus status);

    /**
     * Inscriptions <strong>actives</strong> dont l'apprenant porte le
     * numéro étudiant recherché (EF-USER-009).
     *
     * <p>La recherche par nom ne se fait pas ici : le nom vit dans
     * {@code identity}, et {@code enrollment} n'a pas d'entité vers
     * laquelle joindre — la frontière de module est respectée en
     * demandant d'abord les comptes à {@code UserDirectory}, puis les
     * inscriptions de ces comptes.
     */
    @Query("""
            SELECT e FROM Enrollment e
            JOIN e.studentProfile p
            WHERE e.status = :status AND LOWER(p.studentNumber) LIKE :pattern
            ORDER BY p.studentNumber ASC
            """)
    List<Enrollment> searchByStudentNumber(@Param("pattern") String pattern,
                                           @Param("status") EnrollmentStatus status,
                                           org.springframework.data.domain.Pageable pageable);

    /** Inscriptions actives des profils apprenants indiqués. */
    @Query("""
            SELECT e FROM Enrollment e
            JOIN e.studentProfile p
            WHERE e.status = :status AND p.userId IN :userIds
            """)
    List<Enrollment> findActiveByStudentUserIds(@Param("userIds") java.util.Collection<Long> userIds,
                                                @Param("status") EnrollmentStatus status);
}