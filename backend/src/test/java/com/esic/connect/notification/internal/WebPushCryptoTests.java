package com.esic.connect.notification.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Conformité du chiffrement de poussée au <strong>vecteur de test
 * officiel de la RFC 8291 §5</strong> (EF-NOTIF-005).
 *
 * <p><strong>Pourquoi ce test compte plus que les autres.</strong> Le
 * contenu poussé est chiffré pour l'appareil de l'abonné : le service de
 * poussée relaie un message qu'il ne peut pas lire. C'est cette propriété
 * qui rend la poussée acceptable au regard du cahier (§29.3). Une
 * implémentation légèrement fausse — un octet d'ordre, un préfixe
 * d'information, une longueur — produirait des messages que le navigateur
 * refuse de déchiffrer, sans qu'aucun test « maison » ne s'en aperçoive :
 * chiffrer puis déchiffrer avec le même code fautif fonctionne
 * parfaitement. Seule la comparaison à un résultat produit par
 * <em>quelqu'un d'autre</em> prouve la conformité.
 */
class WebPushCryptoTests {

    // Vecteur de la RFC 8291 §5 — valeurs publiques du document de
    // normalisation, sans lien avec un quelconque abonné réel.
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY =
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLoc"
                    + "InmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyou"
                    + "BWLVWGNWQexSgSxsj_Qulcy4a-fN";

    private final WebPushCrypto crypto = new WebPushCrypto();

    @Test
    @DisplayName("le corps chiffré est octet pour octet celui de la RFC 8291 §5")
    void matchesTheOfficialRfc8291TestVector() {
        ECPrivateKey asPrivate = WebPushCrypto.privateKeyOf(WebPushCrypto.decode(AS_PRIVATE));
        ECPublicKey asPublic = WebPushCrypto.publicKeyOf(WebPushCrypto.decode(AS_PUBLIC));

        byte[] body = crypto.encrypt(UA_PUBLIC, AUTH_SECRET,
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decode(SALT), asPrivate, asPublic);

        assertThat(WebPushCrypto.encode(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    @DisplayName("l'en-tête aes128gcm porte le sel, la taille d'enregistrement et la clé éphémère")
    void producesAnRfc8188Header() {
        byte[] body = crypto.encrypt(UA_PUBLIC, AUTH_SECRET,
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.decode(SALT),
                WebPushCrypto.privateKeyOf(WebPushCrypto.decode(AS_PRIVATE)),
                WebPushCrypto.publicKeyOf(WebPushCrypto.decode(AS_PUBLIC)));

        assertThat(Arrays.copyOf(body, 16)).isEqualTo(WebPushCrypto.decode(SALT));
        // Octet 20 : longueur de l'identifiant de clé = 65 (point non compressé).
        assertThat(body[20]).isEqualTo((byte) 65);
        assertThat(Arrays.copyOfRange(body, 21, 86)).isEqualTo(WebPushCrypto.decode(AS_PUBLIC));
    }

    @Test
    @DisplayName("deux chiffrements du même message diffèrent — sel et clé éphémère sont tirés au hasard")
    void randomisesSaltAndEphemeralKey() {
        byte[] first = crypto.encrypt(UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8));
        byte[] second = crypto.encrypt(UA_PUBLIC, AUTH_SECRET, PLAINTEXT.getBytes(StandardCharsets.UTF_8));

        assertThat(first).isNotEqualTo(second);
        assertThat(Arrays.copyOf(first, 16)).isNotEqualTo(Arrays.copyOf(second, 16));
    }

    @Test
    @DisplayName("le base64url sans remplissage émis par les navigateurs est accepté")
    void acceptsUnpaddedBase64Url() {
        // AUTH_SECRET fait 16 octets : son encodage sans remplissage a une
        // longueur non multiple de 4, que le décodeur du JDK refuserait tel quel.
        assertThat(AUTH_SECRET.length() % 4).isNotZero();
        assertThat(WebPushCrypto.decode(AUTH_SECRET)).hasSize(16);
    }

    @Test
    @DisplayName("une clé d'abonné mal formée est refusée, jamais devinée")
    void rejectsMalformedSubscriberKey() {
        assertThatThrownBy(() -> crypto.encrypt("AAAA", AUTH_SECRET, "x".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
