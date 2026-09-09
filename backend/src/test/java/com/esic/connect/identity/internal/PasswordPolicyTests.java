package com.esic.connect.identity.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Politique de mot de passe (docs/02 §17.1).
 *
 * <p>Ces règles sont volontairement peu nombreuses : longueur minimale,
 * refus des mots de passe courants, refus de l'adresse de la personne. Il
 * n'y a délibérément aucune exigence de composition à vérifier.
 */
class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy(12);

    @Test
    void aLongUncommonPassphraseIsAccepted() {
        assertThat(policy.violations("cheval batterie agrafe correct", "alice@esic-connect.test")).isEmpty();
    }

    @Test
    void aShortCommonPasswordIsCaughtByTheLengthRuleFirst() {
        // « azertyuiop » figure dans la liste des mots de passe courants,
        // mais il est d'abord trop court : c'est ce motif-là qui doit
        // être renvoyé, car c'est celui que la personne doit corriger.
        assertThat(policy.violations("azertyuiop", null))
                .singleElement()
                .asString()
                .contains("au moins 12 caractères");
    }

    @Test
    void aPasswordShorterThanTheMinimumIsRejected() {
        assertThat(policy.violations("court1234", "alice@esic-connect.test"))
                .singleElement()
                .asString()
                .contains("au moins 12 caractères");
    }

    @Test
    void anEmptyPasswordIsRejected() {
        assertThat(policy.violations("   ", null)).isNotEmpty();
        assertThat(policy.violations(null, null)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"motdepasse123", "azertyuiop123", "password12345", "123456789012",
            "administrateur", "esicconnect2026", "monmotdepasse1"})
    void commonPasswordsAreRejected(String candidate) {
        assertThat(policy.violations(candidate, null))
                .singleElement()
                .asString()
                .contains("trop courant");
    }

    @Test
    void caseAndAccentsDoNotBypassTheCommonPasswordList() {
        // « MotDePasse123 » et « môtdepasse123 » doivent être reconnus
        // comme le même mot de passe courant : sans canonicalisation, la
        // liste ne servirait à rien.
        assertThat(policy.violations("MotDePasse123", null)).isNotEmpty();
        assertThat(policy.violations("môtdepasse123", null)).isNotEmpty();
    }

    @Test
    void aPasswordMadeOfASingleRepeatedCharacterIsRejected() {
        assertThat(policy.violations("aaaaaaaaaaaaaaaa", null))
                .singleElement()
                .asString()
                .contains("trop simple");
    }

    @Test
    void aPasswordContainingTheEmailLocalPartIsRejected() {
        assertThat(policy.violations("dupontdupontdupont", "dupont@esic-connect.test"))
                .singleElement()
                .asString()
                .contains("adresse électronique");
    }

    @Test
    void aVeryShortLocalPartCannotBlockEveryPassword() {
        // Un local-part de moins de 3 caractères produirait des faux
        // positifs massifs : « ab » se retrouve dans une multitude de
        // passphrases légitimes.
        assertThat(policy.violations("abricotier majestueux", "ab@esic-connect.test")).isEmpty();
    }

    @Test
    void anExcessivelyLongPasswordIsRejected() {
        assertThat(policy.violations("a".repeat(500), null))
                .singleElement()
                .asString()
                .contains("dépasser");
    }

    @Test
    void configuringAMinimumBelowTwelveFailsAtStartup() {
        assertThatThrownBy(() -> new PasswordPolicy(8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("min-length");
    }
}
