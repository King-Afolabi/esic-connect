package com.esic.connect.coursesession.internal;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rôles {@code @PreAuthorize} des routes du module {@code coursesession}.
 * Le contrôle fin (périmètre pédagogique, séance du formateur, lecture
 * seule de {@code SCHOOL_ADMINISTRATION}) est appliqué par le service
 * ({@link CourseSessionAccessGuard}), jamais d'après un paramètre client.
 *
 * <ul>
 *   <li>{@link #READ_ROLES} : liste + détail + présences —
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code SCHOOL_ADMINISTRATION}/{@code PEDAGOGICAL_MANAGER}/{@code TEACHER}
 *       (un {@code TEACHER} ne voit que ses séances, un
 *       {@code PEDAGOGICAL_MANAGER} que son périmètre) ;</li>
 *   <li>{@link #CREATE_ROLES} : création d'une séance exceptionnelle et
 *       liste des formateurs éligibles —
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code PEDAGOGICAL_MANAGER} ;</li>
 *   <li>{@link #MANAGE_ROLES} : ouverture / fermeture —
 *       {@code ADMIN}/{@code SUPER_ADMIN}/{@code PEDAGOGICAL_MANAGER}/{@code TEACHER}
 *       ({@code SCHOOL_ADMINISTRATION} exclu : lecture seule).</li>
 * </ul>
 *
 * <p>{@code STUDENT} n'a aucun accès à ces routes.
 */
final class CourseSessionWeb {

    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";
    static final String CREATE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER')";
    static final String MANAGE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','PEDAGOGICAL_MANAGER','TEACHER')";

    private CourseSessionWeb() {
    }

    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
