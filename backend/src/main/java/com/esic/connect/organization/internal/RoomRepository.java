package com.esic.connect.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    Optional<Room> findByPublicId(UUID publicId);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    boolean existsBySiteIdAndStatus(Long siteId, OrganizationStatus status);

    boolean existsByBuildingIdAndStatus(Long buildingId, OrganizationStatus status);
}
