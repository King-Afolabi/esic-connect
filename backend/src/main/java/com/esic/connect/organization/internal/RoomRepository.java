package com.esic.connect.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    Optional<Room> findByPublicId(UUID publicId);

    /** Résolution d'une salle par le jeton de son QR fixe (EF-ATT-010). */
    Optional<Room> findByStaticQrReference(String staticQrReference);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    boolean existsBySiteIdAndStatus(Long siteId, OrganizationStatus status);

    boolean existsByBuildingIdAndStatus(Long buildingId, OrganizationStatus status);
}
