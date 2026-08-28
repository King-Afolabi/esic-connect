package com.esic.connect.shared;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Propriétés techniques communes à toutes les entités du socle : clé
 * interne, identifiant public et verrouillage optimiste (docs/04 §4.1 et
 * §6.6). Ne contient volontairement aucune relation métier (par exemple
 * vers {@code UserAccount}) : chaque module reste libre de sa propre
 * stratégie de référencement inter-entités.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "public_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void ensurePublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Long getVersion() {
        return version;
    }
}
