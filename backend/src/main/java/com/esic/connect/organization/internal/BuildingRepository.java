package com.esic.connect.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface BuildingRepository extends JpaRepository<Building, Long>, JpaSpecificationExecutor<Building> {

    Optional<Building> findByPublicId(UUID publicId);

    boolean existsBySiteIdAndCode(Long siteId, String code);

    boolean existsBySiteIdAndStatus(Long siteId, OrganizationStatus status);
}
