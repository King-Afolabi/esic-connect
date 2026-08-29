package com.esic.connect.alternation.internal;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rôles {@code @PreAuthorize} et utilitaires communs aux contrôleurs du
 * module {@code alternation}.
 *
 * <ul>
 *   <li>{@link #PATTERN_READ_ROLES} : consultation des modèles de rythme —
 *       {@code ADMIN}, {@code SUPER_ADMIN}, {@code SCHOOL_ADMINISTRATION},
 *       {@code PEDAGOGICAL_MANAGER} (les modèles sont un référentiel
 *       global, non rattaché à une formation) ;</li>
 *   <li>{@link #PATTERN_WRITE_ROLES} : création / modification / archivage
 *       d'un modèle — {@code ADMIN}, {@code SUPER_ADMIN},
 *       {@code SCHOOL_ADMINISTRATION} ;</li>
 *   <li>{@link #SCOPED_ROLES} : affectations de classe et exceptions
 *       individuelles — mêmes rôles plus {@code PEDAGOGICAL_MANAGER},
 *       dont l'accès est ensuite restreint à son périmètre par le service
 *       via {@link com.esic.connect.academic.AcademicScopeDirectory}
 *       (jamais d'après un paramètre client).</li>
 * </ul>
 *
 * <p>{@code TEACHER} et {@code STUDENT} n'ont aucun accès à ce module.
 */
final class AlternationWeb {

    static final String PATTERN_READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";
    static final String PATTERN_WRITE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION')";
    static final String SCOPED_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";

    private AlternationWeb() {
    }

    static UUID parseUuid(String value, AlternationException.Kind notFound) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new AlternationException(notFound);
        }
    }

    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
