package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AccountInvitationRepository extends JpaRepository<AccountInvitation, Long> {

    Optional<AccountInvitation> findByTokenHash(String tokenHash);

    List<AccountInvitation> findByUserIdAndStatus(Long userId, AccountInvitationStatus status);
}
