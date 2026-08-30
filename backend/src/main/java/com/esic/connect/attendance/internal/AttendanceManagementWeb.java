package com.esic.connect.attendance.internal;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rôles {@code @PreAuthorize} des routes de gestion de l'assiduité (V10).
 * Le contrôle fin (séance du formateur, périmètre pédagogique,
 * propriétaire d'un justificatif) est appliqué par les services, jamais
 * d'après un paramètre client.
 *
 * <ul>
 *   <li>{@link #MANAGE_ROLES} : présence manuelle, correction, annulation,
 *       historique — {@code ADMIN}/{@code SUPER_ADMIN}/
 *       {@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER}/{@code TEACHER}
 *       (le service restreint {@code SCHOOL_ADMINISTRATION} au global,
 *       {@code PEDAGOGICAL_MANAGER} à son périmètre, {@code TEACHER} à ses
 *       séances) ;</li>
 *   <li>{@link #REVIEW_ROLES} : examen d'un justificatif —
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/
 *       {@code PEDAGOGICAL_MANAGER} (périmètre) ; {@code TEACHER} peut
 *       seulement <em>lire</em> les justificatifs de ses séances ;</li>
 *   <li>{@link #REPORT_ROLES} : rapports et exports — mêmes rôles que
 *       {@link #MANAGE_ROLES}, filtrés par périmètre côté service ;</li>
 *   <li>{@link #STUDENT_ROLE} : espace « Mes présences » et dépôt de
 *       justificatif.</li>
 * </ul>
 */
final class AttendanceManagementWeb {

    static final String MANAGE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";
    static final String REVIEW_LIST_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";
    static final String REVIEW_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";
    static final String REPORT_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";
    static final String STUDENT_ROLE = "hasRole('STUDENT')";

    private AttendanceManagementWeb() {
    }

    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
