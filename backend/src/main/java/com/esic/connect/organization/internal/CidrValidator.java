package com.esic.connect.organization.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Validation stricte d'un CIDR IPv4 ou IPv6 (cahier §17.9, docs/04 §9.4).
 *
 * <p>Aucune résolution DNS n'est déclenchée : la partie adresse est
 * d'abord confirmée comme littéral IPv4 (analyse manuelle) ou comme
 * littéral IPv6 (présence d'au moins un {@code ':'}, ce qui interdit à
 * {@link InetAddress#getByName(String)} de retomber sur une recherche de
 * nom d'hôte — il ne peut alors que produire l'adresse ou lever
 * {@link UnknownHostException}).
 *
 * <p>Le préfixe est borné : 0..32 pour IPv4, 0..128 pour IPv6.
 */
final class CidrValidator {

    private static final int MAX_IPV4_PREFIX = 32;
    private static final int MAX_IPV6_PREFIX = 128;

    private CidrValidator() {
    }

    static boolean isValid(String candidate) {
        if (candidate == null) {
            return false;
        }
        String value = candidate.trim();
        int slash = value.indexOf('/');
        // Exactement un séparateur, ni en tête ni en fin.
        if (slash <= 0 || slash != value.lastIndexOf('/') || slash == value.length() - 1) {
            return false;
        }
        String address = value.substring(0, slash);
        String prefixText = value.substring(slash + 1);
        if (prefixText.length() > 3 || !prefixText.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        int prefix = Integer.parseInt(prefixText);

        if (isIpv4Literal(address)) {
            return prefix <= MAX_IPV4_PREFIX;
        }
        if (address.indexOf(':') >= 0 && isIpv6Literal(address)) {
            return prefix <= MAX_IPV6_PREFIX;
        }
        return false;
    }

    private static boolean isIpv4Literal(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3
                    || !octet.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String address) {
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            boolean hexOrSep = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == ':' || c == '.';
            if (!hexOrSep) {
                return false;
            }
        }
        try {
            InetAddress.getByName(address);
            return true;
        } catch (UnknownHostException malformed) {
            return false;
        }
    }
}
