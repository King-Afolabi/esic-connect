package com.esic.connect.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    Optional<MfaRecoveryCode> findByCodeHashAndStatus(String codeHash, MfaRecoveryCodeStatus status);

    List<MfaRecoveryCode> findByUserIdAndStatus(Long userId, MfaRecoveryCodeStatus status);

    long countByUserIdAndStatus(Long userId, MfaRecoveryCodeStatus status);
}
