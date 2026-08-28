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
}
