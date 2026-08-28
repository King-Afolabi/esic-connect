package com.esic.connect.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface SiteNetworkRangeRepository
        extends JpaRepository<SiteNetworkRange, Long>, JpaSpecificationExecutor<SiteNetworkRange> {

    Optional<SiteNetworkRange> findByPublicId(UUID publicId);

    boolean existsBySiteIdAndCidrAndActiveTrue(Long siteId, String cidr);
}
