package com.esic.connect.organization.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface SiteRepository extends JpaRepository<Site, Long>, JpaSpecificationExecutor<Site> {

    Optional<Site> findByPublicId(UUID publicId);

    boolean existsByCode(String code);
}
