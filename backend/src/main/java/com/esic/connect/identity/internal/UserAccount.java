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
 * Compte utilisateur (docs/04-modele-donnees.md §10.1).
 *
 * Ne porte aucune logique métier (activation, connexion, MFA...) : ce
 * socle ne persiste que la structure de données.
 */
@Entity
@Table(name = "user_account")
@EntityListeners(AuditingEntityListener.class)
public class UserAccount extends BaseEntity {

    @Column(name = "external_source")
    private String externalSource;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "preferred_time_zone")
    private String preferredTimeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_by_id")
    private Long suspendedById;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "external_synced_at")
    private Instant externalSyncedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;

    protected UserAccount() {
        // JPA
    }

    public UserAccount(String email, String firstName, String lastName, AccountStatus status) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Suspend le compte (cahier §9.5, modèle §2.5 {@code ACTIVE →
     * SUSPENDED}). La connexion est alors refusée
     * ({@code UserAccountUserDetails}). Le motif et l'auteur sont
     * conservés pour l'audit ; aucune donnée n'est supprimée.
     */
    public void suspend(String reason, Long actorId, Instant at) {
        this.status = AccountStatus.SUSPENDED;
        this.suspendedAt = at;
        this.suspendedById = actorId;
        this.suspensionReason = reason;
        this.updatedById = actorId;
    }

    /** Réactive un compte suspendu ({@code SUSPENDED → ACTIVE}, cahier §9.5). */
    public void reactivate(Long actorId) {
        this.status = AccountStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspendedById = null;
        this.suspensionReason = null;
        this.updatedById = actorId;
    }

    /**
     * Archivage logique (cahier §9.7, modèle §2.4/§2.5) : aucune
     * suppression physique, l'historique est conservé. Opération
     * irréversible dans ce périmètre. La clôture des rôles actifs est
     * réalisée par le service, dans la même transaction.
     */
    public void archive(Long actorId, Instant at) {
        this.status = AccountStatus.ARCHIVED;
        this.archivedAt = at;
        this.updatedById = actorId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Horodate la dernière connexion réussie (docs/04 §10.1). */
    public void recordSuccessfulLogin(Instant loginAt) {
        this.lastLoginAt = loginAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    /**
     * Active le compte à l'issue du parcours d'invitation (cahier §8.3) :
     * enregistre le hachage du mot de passe choisi, marque l'adresse
     * comme vérifiée et bascule le statut en {@link AccountStatus#ACTIVE}.
     * Le mot de passe en clair n'entre jamais dans cette méthode.
     */
    public void activateWithPassword(String encodedPassword, Instant activatedAt) {
        this.passwordHash = encodedPassword;
        this.emailVerifiedAt = activatedAt;
        this.status = AccountStatus.ACTIVE;
    }
}
