package com.esic.connect.identity.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOTP (EF-AUTH-008).
 *
 * <p>Le générateur est vérifié contre les <strong>vecteurs de la
 * RFC 6238</strong> plutôt que contre lui-même : c'est la seule preuve
 * qui vaille qu'une application d'authentification du commerce
 * produira les mêmes codes.
 */
class TotpGeneratorTests {

    /** Secret de la RFC 6238 : les 20 octets ASCII « 12345678901234567890 ». */
    private static final String RFC_SECRET = TotpGenerator.encodeBase32(
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    /**
     * Vecteurs officiels HMAC-SHA1 de la RFC 6238, tronqués à six
     * chiffres — les huit chiffres publiés se terminent par ces valeurs.
     */
    @ParameterizedTest
    @CsvSource({
            "59,           287082",
            "1111111109,   081804",
            "1111111111,   050471",
            "1234567890,   005924",
            "2000000000,   279037",
            "20000000000,  353130"
    })
    void produitLesCodesDeLaRfc6238(long epochSecond, String expectedCode) {
        long step = TotpGenerator.stepOf(Instant.ofEpochSecond(epochSecond));
        assertThat(TotpGenerator.codeAt(RFC_SECRET, step)).isEqualTo(expectedCode);
    }

    @Test
    void unCodeCourantEstAccepteEtRenvoieSonPasDeTemps() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = TotpGenerator.stepOf(now);
        String code = TotpGenerator.codeAt(RFC_SECRET, step);

        assertThat(TotpGenerator.matches(RFC_SECRET, code, now, 1)).isEqualTo(step);
    }

    @Test
    void leCodeDuPasPrecedentResteAccepteDansLaTolerance() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long previousStep = TotpGenerator.stepOf(now) - 1;
        String code = TotpGenerator.codeAt(RFC_SECRET, previousStep);

        assertThat(TotpGenerator.matches(RFC_SECRET, code, now, 1)).isEqualTo(previousStep);
        // Sans tolérance, ce même code est refusé : la fenêtre est bien
        // un choix explicite, pas un effet de bord.
        assertThat(TotpGenerator.matches(RFC_SECRET, code, now, 0)).isNull();
    }

    @Test
    void unCodeTropAncienEstRefuse() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String code = TotpGenerator.codeAt(RFC_SECRET, TotpGenerator.stepOf(now) - 5);

        assertThat(TotpGenerator.matches(RFC_SECRET, code, now, 1)).isNull();
    }

    @Test
    void uneSaisieMalFormeeEstRefuseeSansException() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);

        assertThat(TotpGenerator.matches(RFC_SECRET, null, now, 1)).isNull();
        assertThat(TotpGenerator.matches(RFC_SECRET, "", now, 1)).isNull();
        assertThat(TotpGenerator.matches(RFC_SECRET, "abcdef", now, 1)).isNull();
        assertThat(TotpGenerator.matches(RFC_SECRET, "12345", now, 1)).isNull();
        assertThat(TotpGenerator.matches(RFC_SECRET, "1234567", now, 1)).isNull();
    }

    @Test
    void unCodeGardeSesZerosDeTete() {
        // Le pas 1234567890/30 produit 005924 : sans le formatage à six
        // caractères, le code partirait à « 5924 » et serait refusé partout.
        long step = TotpGenerator.stepOf(Instant.ofEpochSecond(1_234_567_890L));
        assertThat(TotpGenerator.codeAt(RFC_SECRET, step)).hasSize(6).startsWith("00");
    }

    @Test
    void unSecretGenereFaitVingtOctetsEtSeRelitEnBase32() {
        String secret = TotpGenerator.newSecret(new SecureRandom());

        assertThat(TotpGenerator.decodeBase32(secret)).hasSize(20);
        assertThat(secret).matches("[A-Z2-7]+");
    }

    @Test
    void lUriDApprovisionnementPorteLEmetteurEtLeSecretSansAutreDonnee() {
        String uri = TotpGenerator.provisioningUri("ESIC Connect", "eleve@esic-connect.test", RFC_SECRET);

        assertThat(uri).startsWith("otpauth://totp/ESIC%20Connect:eleve%40esic-connect.test");
        assertThat(uri).contains("secret=" + RFC_SECRET)
                .contains("algorithm=SHA1")
                .contains("digits=6")
                .contains("period=30");
    }
}
