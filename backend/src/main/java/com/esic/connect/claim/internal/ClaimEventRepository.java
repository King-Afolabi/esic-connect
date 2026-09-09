package com.esic.connect.claim.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ClaimEventRepository extends JpaRepository<ClaimEvent, Long> {

    List<ClaimEvent> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
