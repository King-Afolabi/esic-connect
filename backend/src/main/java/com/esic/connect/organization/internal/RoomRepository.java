package com.esic.connect.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    Optional<Room> findByPublicId(UUID publicId);

    /** Résolution d'une salle par le jeton de son QR fixe (EF-ATT-010). */
    Optional<Room> findByStaticQrReference(String staticQrReference);

    /**
     * Recherche globale (EF-USER-009) : salles <strong>actives</strong>
     * dont le code ou le nom contient le fragment. Une salle archivée
     * n'est pas un lieu où l'on peut aller.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r FROM Room r
            WHERE r.status = com.esic.connect.organization.internal.OrganizationStatus.ACTIVE
              AND (LOWER(r.code) LIKE :pattern OR LOWER(r.name) LIKE :pattern)
            ORDER BY r.code ASC
            """)
    java.util.List<Room> search(@org.springframework.data.repository.query.Param("pattern") String pattern,
                                org.springframework.data.domain.Pageable pageable);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    boolean existsBySiteIdAndStatus(Long siteId, OrganizationStatus status);

    boolean existsByBuildingIdAndStatus(Long buildingId, OrganizationStatus status);
}
