package com.esic.connect.identity.internal;

import com.esic.connect.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Passkey enregistrée pour un compte (EF-AUTH-006 ; migration V18).
 *
 * <p>Ne contient qu'une <strong>clé publique</strong> et un compteur de
 * signature. Aucune empreinte digitale, aucun modèle facial : la
 * vérification de l'utilisateur reste sur le terminal, le serveur ne
 * reçoit qu'une preuve cryptographique (RG-091, AC-020).
 *
 * <p>Le nom {@code WebAuthnCredentialEntity} évite la confusion avec
 * {@code CredentialRecord}, le type de la bibliothèque de vérification :
 * l'un est la ligne persistée, l'autre l'objet de calcul.
 */
@Entity
@Table(name = "webauthn_credential")
@EntityListeners(AuditingEntityListener.class)
public class WebAuthnCredentialEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Identifiant du justificatif produit par l'authentificateur, en base64url. */
    @Column(name = "credential_id", nullable = false, length = 512)
    private String credentialId;

    /** Données de justificatif attesté sérialisées (CBOR) : AAGUID, id, clé publique COSE. */
    @Column(name = "credential_record", nullable = false, length = 2048)
    private byte[] credentialRecord;

    /**
     * Compteur de signature de l'authentificateur. Un compteur qui
     * n'augmente pas trahit un clonage : la bibliothèque de vérification
     * le contrôle, à condition qu'on lui donne la dernière valeur connue.
     */
    @Column(name = "signature_count", nullable = false)
    private long signatureCount;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WebAuthnCredentialStatus status;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebAuthnCredentialEntity() {
    }

    static WebAuthnCredentialEntity registered(Long userId, String credentialId,
                                               byte[] credentialRecord, long signatureCount,
                                               String label) {
        WebAuthnCredentialEntity entity = new WebAuthnCredentialEntity();
        entity.userId = userId;
        entity.credentialId = credentialId;
        entity.credentialRecord = credentialRecord;
        entity.signatureCount = signatureCount;
        entity.label = label;
        entity.status = WebAuthnCredentialStatus.ACTIVE;
        return entity;
    }

    void recordUse(Instant now, long newSignatureCount) {
        this.lastUsedAt = now;
        this.signatureCount = newSignatureCount;
    }

    void revoke(Instant now) {
        this.status = WebAuthnCredentialStatus.REVOKED;
        this.revokedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public byte[] getCredentialRecord() {
        return credentialRecord;
    }

    public long getSignatureCount() {
        return signatureCount;
    }

    public String getLabel() {
        return label;
    }

    public WebAuthnCredentialStatus getStatus() {
        return status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
