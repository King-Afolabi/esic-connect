package com.esic.connect.academic.internal;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rôles {@code @PreAuthorize} et utilitaires communs aux contrôleurs du
 * module. Lecture : rôles de gestion pédagogique/administrative ;
 * écriture : {@code ADMIN}/{@code SUPER_ADMIN} uniquement — le périmètre
 * d'écriture du {@code PEDAGOGICAL_MANAGER} relève du contrôle de
 * périmètre pédagogique, hors de ce lot. {@code TEACHER} exclu.
 */
final class AcademicWeb {

    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";
    static final String WRITE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN')";

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
