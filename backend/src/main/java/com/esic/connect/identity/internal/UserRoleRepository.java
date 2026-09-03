package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserId(Long userId);

    /**
     * Affectations d'un utilisateur, rôle chargé dans la même requête
     * (utilisé hors transaction de lecture pour le détail d'un compte).
     */
    @Query("select ur from UserRole ur join fetch ur.role where ur.user.id = :userId")
    List<UserRole> findWithRoleByUserId(@Param("userId") Long userId);

    @Query("select ur from UserRole ur join fetch ur.role "
            + "where ur.user.id = :userId and ur.active = true")
    List<UserRole> findActiveWithRoleByUserId(@Param("userId") Long userId);

    @Query("select ur from UserRole ur join fetch ur.role "
            + "where ur.user.id in :userIds and ur.active = true")
    List<UserRole> findActiveWithRoleByUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * Codes des rôles ACTIFS d'un compte. Requête projetée : la décision
     * de politique de second facteur (RG-007) n'a besoin que des codes,
     * pas des entités d'affectation.
     */
    @Query("select r.code from UserRole ur join ur.role r "
            + "where ur.user.id = :userId and ur.active = true")
    List<RoleCode> findActiveRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * Affectations actives d'un rôle donné dont le compte porteur est
     * dans le statut demandé. Utilisé par {@code DefaultTeacherDirectory}
     * pour lister les formateurs éligibles à une séance sans exposer
     * {@code GET /api/v1/users}.
     */
    @Query("select ur from UserRole ur join fetch ur.user u join ur.role r "
            + "where r.code = :code and ur.active = true and u.status = :status")
    List<UserRole> findActiveAssignmentsByRoleCodeAndUserStatus(@Param("code") RoleCode code,
                                                               @Param("status") AccountStatus status);
}
