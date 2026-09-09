package com.esic.connect.claim.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ClaimMessageRepository extends JpaRepository<ClaimMessage, Long> {

    List<ClaimMessage> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
