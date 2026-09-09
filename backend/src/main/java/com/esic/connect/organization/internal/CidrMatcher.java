package com.esic.connect.organization.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Appartenance d'une adresse IP à un bloc CIDR (EF-ATT-008 ; docs/02
 * §16.7).
 *
 * <p>Comparaison faite sur les <strong>octets</strong> de l'adresse, pas
 * sur son écriture : {@code 192.168.001.010} et {@code 192.168.1.10} sont
 * la même machine, et une comparaison textuelle les distinguerait. Aucune
 * résolution DNS n'est tentée — un nom d'hôte n'est pas une adresse, et
 * interroger le DNS pendant une décision d'autorisation ouvrirait une
 * dépendance réseau sur le chemin d'émargement.
 *
 * <p>Les familles ne se mélangent pas : une adresse IPv4 n'appartient
 * jamais à un bloc IPv6 et réciproquement. Le refus est le défaut — toute
 * valeur illisible renvoie {@code false}.
 */
final class CidrMatcher {

    private CidrMatcher() {
    }

    static boolean matches(String cidr, String ipAddress) {
        if (cidr == null || ipAddress == null) {
            return false;
        }
        int slash = cidr.lastIndexOf('/');
        if (slash <= 0 || slash == cidr.length() - 1) {
            return false;
        }
        byte[] network = literalToBytes(cidr.substring(0, slash).trim());
        byte[] address = literalToBytes(ipAddress.trim());
        if (network == null || address == null || network.length != address.length) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1).trim());
        } catch (NumberFormatException notANumber) {
            return false;
        }
        int maxPrefix = network.length * 8;
        if (prefix < 0 || prefix > maxPrefix) {
            return false;
        }

        int fullBytes = prefix / 8;
        for (int index = 0; index < fullBytes; index++) {
            if (network[index] != address[index]) {
                return false;
            }
        }
        int remainingBits = prefix % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainingBits)) & 0xFF;
        return (network[fullBytes] & mask) == (address[fullBytes] & mask);
    }

    /**
     * Octets d'une adresse écrite en clair, ou {@code null}.
     *
     * <p>La vérification de forme est faite <em>avant</em> l'appel à
     * {@link InetAddress#getByName} : sans elle, un nom d'hôte
     * déclencherait une résolution DNS.
     */
    private static byte[] literalToBytes(String value) {
        if (value.isEmpty() || !isIpLiteral(value)) {
            return null;
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException notAnAddress) {
            return null;
        }
    }

    private static boolean isIpLiteral(String value) {
        boolean looksIpv6 = value.indexOf(':') >= 0;
        if (looksIpv6) {
            return value.chars().allMatch(c -> Character.digit(c, 16) >= 0 || c == ':' || c == '.');
        }
        return value.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
    }
}
