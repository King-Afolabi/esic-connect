package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface MfaCredentialRepository extends JpaRepository<MfaCredential, Long> {

    /**
     * Facteur vivant d'un compte. La contrainte
     * {@code uq_mfa_credential_active} garantit qu'il n'y en a qu'un.
     */
    Optional<MfaCredential> findByUserIdAndStatusIn(Long userId, List<MfaCredentialStatus> statuses);

    Optional<MfaCredential> findByUserIdAndStatus(Long userId, MfaCredentialStatus status);

    boolean existsByUserIdAndStatus(Long userId, MfaCredentialStatus status);
}
