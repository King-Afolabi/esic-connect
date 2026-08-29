package com.esic.connect.academic.internal;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rôles {@code @PreAuthorize} et utilitaires communs aux contrôleurs du
 * module.
 *
 * <ul>
 *   <li>{@link #READ_ROLES} : lecture du référentiel — le filtrage par
 *       périmètre pédagogique est appliqué dans les services via
 *       {@link AcademicScopeGuard} (jamais côté client) ;</li>
 *   <li>{@link #WRITE_ROLES} : écritures réservées à
 *       {@code ADMIN}/{@code SUPER_ADMIN} (année scolaire, création d'une
 *       formation) ;</li>
 *   <li>{@link #SCOPED_WRITE_ROLES} : écritures ouvertes en plus au
 *       {@code PEDAGOGICAL_MANAGER}, restreintes ensuite à son périmètre
 *       par {@link AcademicScopeGuard} ;</li>
 *   <li>{@link #ASSIGNMENT_ROLES} : gestion des affectations de
 *       responsable pédagogique, réservée à {@code ADMIN}/{@code SUPER_ADMIN}.</li>
 * </ul>
 */
final class AcademicWeb {

    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";
    static final String WRITE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN')";
    static final String SCOPED_WRITE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER')";
    static final String ASSIGNMENT_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN')";

    private AcademicWeb() {
    }

    static UUID parseUuid(String value, AcademicException.Kind notFound) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new AcademicException(notFound);
        }
    }

    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
