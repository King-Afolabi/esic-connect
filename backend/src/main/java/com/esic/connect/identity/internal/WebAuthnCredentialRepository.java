package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredentialEntity, Long> {

    Optional<WebAuthnCredentialEntity> findByCredentialIdAndStatus(String credentialId,
                                                                  WebAuthnCredentialStatus status);

    List<WebAuthnCredentialEntity> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, WebAuthnCredentialStatus status);

    Optional<WebAuthnCredentialEntity> findByPublicIdAndUserId(UUID publicId, Long userId);

    boolean existsByUserIdAndStatus(Long userId, WebAuthnCredentialStatus status);
}
