package com.esic.connect.enrollment.internal;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rôles {@code @PreAuthorize} et utilitaires communs aux contrôleurs du
 * module {@code enrollment}.
 *
 * <p>{@link #MANAGE_ROLES} : <strong>écriture</strong> des profils
 * apprenants et des inscriptions (création, inscription, changement de
 * classe, clôture) — {@code ADMIN}, {@code SUPER_ADMIN} et
 * {@code SCHOOL_ADMINISTRATION} (cahier §6.4, §10.1 : l'administration
 * scolaire importe et gère les apprenants). Le {@code PEDAGOGICAL_MANAGER}
 * n'a <em>pas</em> l'administration globale des comptes.
 *
 * <p>{@link #READ_ROLES} : <strong>consultation</strong> (liste + fiche).
 * Ajoute le {@code PEDAGOGICAL_MANAGER} et le {@code TEACHER} ; le service
 * restreint alors la réponse à leur périmètre effectif via
 * {@code RosterScopeResolver} — le premier ne voit que les classes de
 * ses formations, le second que les classes de ses séances —, jamais de
 * fuite inter-classes (cahier §5.5 « contrôle d'accès par périmètre »,
 * §18.2 « une ressource hors périmètre renvoie 404 »). Les trois rôles
 * d'administration gardent l'accès global (aucun filtre). {@code STUDENT}
 * n'a aucun accès ici : il consulte son propre historique via
 * {@code /me/attendance}.
 */
final class EnrollmentWeb {

    static final String MANAGE_ROLES = "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION')";
    static final String READ_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER','TEACHER')";

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
