package com.esic.connect.identity.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Code de récupération à usage unique (EF-AUTH-009 ; migration V18).
 *
 * <p>Comme un jeton d'invitation, le code brut n'est jamais stocké :
 * seule son empreinte SHA-256 l'est. Il est affiché une seule fois, au
 * moment de la génération, et n'est plus jamais consultable ensuite.
 */
@Entity
@Table(name = "mfa_recovery_code")
@EntityListeners(AuditingEntityListener.class)
public class MfaRecoveryCode extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MfaRecoveryCodeStatus status;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MfaRecoveryCode() {
    }

    static MfaRecoveryCode active(Long userId, String codeHash) {
        MfaRecoveryCode code = new MfaRecoveryCode();
        code.userId = userId;
        code.codeHash = codeHash;
        code.status = MfaRecoveryCodeStatus.ACTIVE;
        return code;
    }

    void consume(Instant now) {
        this.status = MfaRecoveryCodeStatus.CONSUMED;
        this.consumedAt = now;
    }

    void revoke() {
        this.status = MfaRecoveryCodeStatus.REVOKED;
    }

    public MfaRecoveryCodeStatus getStatus() {
        return status;
    }

    public Long getUserId() {
        return userId;
    }
}
