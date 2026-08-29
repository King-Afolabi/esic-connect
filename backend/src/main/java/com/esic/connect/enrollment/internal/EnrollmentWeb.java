package com.esic.connect.enrollment.internal;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rôles {@code @PreAuthorize} et utilitaires communs aux contrôleurs du
 * module {@code enrollment}.
 *
 * <p>{@link #MANAGE_ROLES} : gestion des profils apprenants et des
 * inscriptions — {@code ADMIN}, {@code SUPER_ADMIN} et
 * {@code SCHOOL_ADMINISTRATION} (cahier §6.4, §10.1 : l'administration
 * scolaire importe et gère les apprenants). Le {@code PEDAGOGICAL_MANAGER}
 * est exclu dans ce lot : sa restriction au périmètre de ses formations
 * exige un port de périmètre pédagogique public, non encore disponible
 * (voir docs/CURRENT-STATE.md). {@code TEACHER} et {@code STUDENT} n'ont
 * aucun accès ici (la consultation de son propre historique par
 * l'apprenant relève d'un lot ultérieur).
 */
final class EnrollmentWeb {

    static final String MANAGE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION')";

    private EnrollmentWeb() {
    }

    static UUID parseUuid(String value, EnrollmentException.Kind notFound) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            throw new EnrollmentException(notFound);
        }
    }

    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
