package com.esic.connect.attendance.internal;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rôles {@code @PreAuthorize} des routes du module {@code attendance}.
 *
 * <ul>
 *   <li>{@link #VALIDATE_ROLE} : {@code POST /api/v1/attendance/validate}
 *       — {@code STUDENT} uniquement. Aucun autre rôle ne peut émarger.</li>
 *   <li>{@link #MANAGE_ROLES} : émission d'un jeton d'émargement
 *       ({@code POST /api/v1/sessions/{id}/attendance-token}) —
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code PEDAGOGICAL_MANAGER}/{@code TEACHER}.
 *       Le contrôle fin (séance du formateur, périmètre pédagogique) est
 *       délégué à {@code coursesession}.</li>
 *   <li>{@link #READ_ROLES} : consultation des présences d'une séance
 *       ({@code GET /api/v1/sessions/{id}/attendance}) — mêmes rôles que
 *       la lecture des séances, {@code SCHOOL_ADMINISTRATION} inclus.</li>
 * </ul>
 */
final class AttendanceWeb {

    static final String VALIDATE_ROLE = "hasRole('STUDENT')";
    static final String MANAGE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','TEACHER')";
    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";

    private AttendanceWeb() {
    }

    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
