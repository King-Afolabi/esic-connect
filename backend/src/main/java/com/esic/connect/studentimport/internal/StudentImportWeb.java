package com.esic.connect.studentimport.internal;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Rôles {@code @PreAuthorize} et utilitaires communs aux contrôleurs du
 * module {@code studentimport} (rapport §8, §9).
 *
 * <p>{@link #MANAGE_ROLES} : téléversement, simulation, consultation,
 * confirmation et annulation d'un import CSV d'apprenants — {@code ADMIN},
 * {@code SUPER_ADMIN} et {@code SCHOOL_ADMINISTRATION} (portée globale) et
 * {@code PEDAGOGICAL_MANAGER} (limité à son périmètre : décision fine
 * prise dans le service via
 * {@link com.esic.connect.academic.AcademicScopeDirectory}, jamais
 * d'après un paramètre client). {@code TEACHER} et {@code STUDENT} n'ont
 * aucun accès (403).
 *
 * <p>La décision fine de périmètre reste côté serveur : le
 * {@code @PreAuthorize} n'est qu'un premier filtre par rôle.
 */
final class StudentImportWeb {

    static final String MANAGE_ROLES =
            "hasAnyRole('ADMIN','SUPER_ADMIN','SCHOOL_ADMINISTRATION','PEDAGOGICAL_MANAGER')";

    private StudentImportWeb() {
    }

    /**
     * Analyse un identifiant public reçu dans une URL. Une valeur mal
     * formée est traitée comme une ressource introuvable (jamais un 500).
     */
    static UUID parseUuid(String value, StudentImportException.Kind notFound) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException notAUuid) {
            throw new StudentImportException(notFound);
        }
    }

    /** Sujet ({@code sub}) du JWT de l'appelant, ou {@code null}. */
    static String subject(Jwt caller) {
        return caller != null ? caller.getSubject() : null;
    }
}
