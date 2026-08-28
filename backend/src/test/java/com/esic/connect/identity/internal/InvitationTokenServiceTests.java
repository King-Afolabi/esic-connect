package com.esic.connect.identity.internal;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jeton d'invitation : au moins 32 octets d'aléa via SecureRandom, encodé
 * en Base64 URL sans remplissage, empreinte SHA-256 hexadécimale stable.
 */
class InvitationTokenServiceTests {

    private final InvitationTokenService tokenService = new InvitationTokenService();

    @Test
    void generatesUrlSafeTokenOfAtLeast32BytesOfEntropy() {
        String token = tokenService.generateRawToken();

        assertThat(token).doesNotContain("+", "/", "=");
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded.length).isGreaterThanOrEqualTo(32);
    }

    @Test
    void generatesADifferentTokenEachTime() {
        assertThat(tokenService.generateRawToken()).isNotEqualTo(tokenService.generateRawToken());
    }

    @Test
    void hashIsSha256HexAndDeterministic() {
        String token = tokenService.generateRawToken();

        String first = tokenService.hash(token);
        String second = tokenService.hash(token);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(first).isNotEqualTo(token);
    }

    @Test
    void differentTokensProduceDifferentHashes() {
        assertThat(tokenService.hash(tokenService.generateRawToken()))
                .isNotEqualTo(tokenService.hash(tokenService.generateRawToken()));
    }

    @Test
    void hashMatchesKnownSha256Vector() {
        // SHA-256("abc") — vecteur de référence.
        assertThat(tokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
