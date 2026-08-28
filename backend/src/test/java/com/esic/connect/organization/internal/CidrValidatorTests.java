package com.esic.connect.organization.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Validation stricte des notations CIDR IPv4 et IPv6 (cahier §17.9). */
class CidrValidatorTests {

    @ParameterizedTest
    @ValueSource(strings = {
            "10.0.0.0/8",
            "192.168.1.0/24",
            "0.0.0.0/0",
            "255.255.255.255/32",
            "172.16.0.0/12",
            "2001:db8::/32",
            "::1/128",
            "fe80::/10",
            "::/0",
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334/64",
            "::ffff:192.168.1.1/128"
    })
    void acceptsValidIpv4AndIpv6Cidr(String cidr) {
        assertThat(CidrValidator.isValid(cidr)).as(cidr).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "10.0.0.0",              // pas de préfixe
            "10.0.0.0/",             // préfixe vide
            "/8",                    // adresse vide
            "10.0.0.0/33",           // préfixe IPv4 hors bornes
            "10.0.0.0/999",          // préfixe trop long
            "10.0.0.0/-1",           // caractère non numérique
            "10.0.0.0/ 8",           // espace
            "10.0.0.0/8/8",          // deux séparateurs
            "999.0.0.0/8",           // octet > 255
            "10.0.0.256/24",         // octet > 255
            "10.0.0/24",             // trois octets
            "10.0.0.0.0/24",         // cinq octets
            "example.com/24",        // nom d'hôte
            "cafe.bad/24",           // ni IPv4 ni IPv6
            "2001:db8::/129",        // préfixe IPv6 hors bornes
            "2001:db8:::1/64",       // IPv6 mal formé
            "gggg::/32",             // hex invalide
            "abcd/24"                // ni IPv4 ni IPv6
    })
    void rejectsInvalidCidr(String cidr) {
        assertThat(CidrValidator.isValid(cidr)).as(String.valueOf(cidr)).isFalse();
    }

    @Test
    void doesNotResolveHostNames() {
        // "localhost/24" ne doit jamais être accepté (pas un littéral).
        assertThat(CidrValidator.isValid("localhost/24")).isFalse();
    }
}
