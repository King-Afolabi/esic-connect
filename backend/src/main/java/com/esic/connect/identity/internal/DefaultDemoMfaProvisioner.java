package com.esic.connect.identity.internal;

import com.esic.connect.identity.DemoMfaProvisioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Implémentation du port {@link DemoMfaProvisioner}, confinée à
 * {@code identity.internal} et enregistrée <strong>uniquement sous le
 * profil {@code demo}</strong>. Idempotente ; ne journalise jamais le
 * secret en clair.
 */
@Component
@Profile("demo")
class DefaultDemoMfaProvisioner implements DemoMfaProvisioner {

    private static final Logger log = LoggerFactory.getLogger(DefaultDemoMfaProvisioner.class);

    /** Pas de temps sentinelle : garantit qu'aucun code réel futur ne sera jamais refusé pour rejeu. */
    private static final long SENTINEL_STEP = 0L;

    private final UserAccountRepository userAccountRepository;
    private final MfaCredentialRepository credentialRepository;
    private final SecretCipher secretCipher;

    DefaultDemoMfaProvisioner(UserAccountRepository userAccountRepository,
                              MfaCredentialRepository credentialRepository,
                              SecretCipher secretCipher) {
        this.userAccountRepository = userAccountRepository;
        this.credentialRepository = credentialRepository;
        this.secretCipher = secretCipher;
    }

    @Override
    @Transactional
    public void ensureDeterministicTotp(String email, String base32Secret) {
        // Échoue fort et tôt sur un secret mal formé plutôt que de stocker
        // une valeur qui ne produira jamais aucun code valide.
        TotpGenerator.decodeBase32(base32Secret);

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserAccount account = userAccountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Compte de démonstration introuvable pour l'enrôlement TOTP déterministe : "
                                + normalizedEmail + ". Provisionnez d'abord le compte."));

        Optional<MfaCredential> activeCredential =
                credentialRepository.findByUserIdAndStatus(account.getId(), MfaCredentialStatus.ACTIVE);
        if (activeCredential.isPresent()
                && secretCipher.decrypt(activeCredential.get().getSecretCipher()).equals(base32Secret)) {
            return; // Déjà en place avec exactement ce secret : rien à écrire.
        }

        Instant now = Instant.now();
        activeCredential.ifPresent(credential -> credential.revoke(now));
        credentialRepository.findByUserIdAndStatus(account.getId(), MfaCredentialStatus.PENDING)
                .ifPresent(credential -> credential.revoke(now));

        MfaCredential credential = MfaCredential.pending(account.getId(), secretCipher.encrypt(base32Secret));
        // Contourne délibérément le parcours /mfa/enroll + /mfa/enroll/confirm :
        // ce code de bootstrap serveur, réservé au profil demo, n'a aucune
        // session HTTP à travers laquelle le faire transiter. Le pas
        // sentinelle est très inférieur à tout pas réel courant : aucun code
        // futur valide ne sera jamais rejeté comme rejeu.
        credential.confirm(now, SENTINEL_STEP);
        credentialRepository.saveAndFlush(credential);

        log.warn("Facteur TOTP déterministe activé pour le compte de démonstration {} "
                + "(ESIC_DEMO_TOTP_SECRET défini) — réservé au profil demo, jamais en production.",
                normalizedEmail);
    }
}
