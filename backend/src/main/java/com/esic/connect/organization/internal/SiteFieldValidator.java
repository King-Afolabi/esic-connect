package com.esic.connect.organization.internal;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

/**
 * Validation des champs d'un site nécessitant un référentiel externe :
 * fuseau horaire IANA ({@link ZoneId}) et code pays ISO 3166-1 alpha-2
 * ({@link Locale#getISOCountries()}).
 */
final class SiteFieldValidator {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private SiteFieldValidator() {
    }

    /** @return l'identifiant de fuseau accepté (trimé), sinon lève {@link OrganizationException}. */
    static String requireIanaTimeZone(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || !ZoneId.getAvailableZoneIds().contains(trimmed)) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_TIME_ZONE);
        }
        try {
            ZoneId.of(trimmed);
        } catch (DateTimeException unknown) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_TIME_ZONE);
        }
        return trimmed;
    }

    /** @return le code pays en majuscules, {@code null} si absent, sinon lève {@link OrganizationException}. */
    static String normalizeCountryCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(upper)) {
            throw new OrganizationException(OrganizationException.Kind.INVALID_COUNTRY_CODE);
        }
        return upper;
    }
}
