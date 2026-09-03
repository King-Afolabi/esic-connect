package com.esic.connect.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rôles pour lesquels le second facteur est obligatoire (RG-007,
 * docs/02 §17.3 : « obligatoire pour {@code SUPER_ADMIN} et
 * {@code ADMIN} » ; critère AC-021).
 *
 * <p>La liste est configurable par {@code app.security.mfa.required-roles}
 * — un établissement peut vouloir l'étendre — mais son défaut couvre
 * exactement les deux rôles exigés par le cahier des charges. Une valeur
 * inconnue dans la configuration fait échouer le démarrage plutôt que
 * d'affaiblir silencieusement la politique.
 */
@Component
public class MfaPolicy {

    private final Set<RoleCode> requiredRoles;

    public MfaPolicy(@Value("${app.security.mfa.required-roles:SUPER_ADMIN,ADMIN}") String configured) {
        Set<RoleCode> parsed = EnumSet.noneOf(RoleCode.class);
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(value -> {
                    try {
                        parsed.add(RoleCode.valueOf(value.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException unknown) {
                        throw new IllegalStateException(
                                "app.security.mfa.required-roles contient un rôle inconnu : " + value);
                    }
                });
        this.requiredRoles = parsed;
    }

    /** Vrai si l'un des rôles détenus impose un second facteur. */
    public boolean isRequiredFor(Collection<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> {
                    try {
                        return RoleCode.valueOf(code);
                    } catch (IllegalArgumentException unknown) {
                        return null;
                    }
                })
                .anyMatch(role -> role != null && requiredRoles.contains(role));
    }
}
