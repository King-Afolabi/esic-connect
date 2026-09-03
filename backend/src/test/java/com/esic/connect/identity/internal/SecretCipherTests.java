package com.esic.connect.identity.internal;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chiffrement au repos des secrets TOTP (docs/02 §17.3).
 *
 * <p>Un secret TOTP ne peut pas être haché : le serveur doit le relire.
 * Ces tests vérifient qu'il n'est ni stocké en clair, ni silencieusement
 * accepté après altération.
 */
class SecretCipherTests {

    private static final String JWT_SECRET = "un-secret-de-signature-de-trente-deux-octets-au-moins";

    private final SecretCipher cipher = new SecretCipher("", JWT_SECRET);

    @Test
    void unSecretChiffrePuisDechiffreRedonneLaValeurInitiale() {
        String secret = "JBSWY3DPEHPK3PXP";

        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void leCryptogrammeNeContientPasLaValeurEnClair() {
        String secret = "JBSWY3DPEHPK3PXP";

        assertThat(cipher.encrypt(secret)).doesNotContain(secret);
    }

    @Test
    void deuxChiffrementsDuMemeSecretDifferent() {
        // Vecteur d'initialisation aléatoire : deux comptes portant le
        // même secret ne se reconnaissent pas en base.
        String secret = "JBSWY3DPEHPK3PXP";

        assertThat(cipher.encrypt(secret)).isNotEqualTo(cipher.encrypt(secret));
    }

    @Test
    void unCryptogrammeAltereEstRefuseAuLieuDeProduireUnFauxSecret() {
        byte[] envelope = Base64.getDecoder().decode(cipher.encrypt("JBSWY3DPEHPK3PXP"));
        envelope[envelope.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(envelope);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("altéré");
    }

    @Test
    void unSecretChiffreAvecUneAutreCleEstIllisible() {
        String envelope = cipher.encrypt("JBSWY3DPEHPK3PXP");
        SecretCipher other = new SecretCipher("une-autre-cle-de-chiffrement-locale", JWT_SECRET);

        assertThatThrownBy(() -> other.decrypt(envelope))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void uneEnveloppeTronqueeEstRefusee() {
        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(new byte[4])))
                .isInstanceOf(IllegalStateException.class);
    }
}
