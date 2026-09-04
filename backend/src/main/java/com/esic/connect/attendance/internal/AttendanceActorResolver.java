package com.esic.connect.attendance.internal;

import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Résout l'auteur d'une opération d'assiduité pour l'affichage
 * (AC-018 : « une correction affiche l'ancienne valeur, la nouvelle,
 * <strong>l'auteur</strong>, la date et le motif »).
 *
 * <p>Deux niveaux de restitution, volontairement distincts :
 *
 * <ul>
 *   <li>{@link #displayName(Long)} — identité civile, réservée aux écrans
 *       du personnel, qui doit pouvoir remonter à la personne ;</li>
 *   <li>{@link #role(Long)} — fonction seule, servie à l'apprenant dans
 *       son journal de transparence. Savoir qu'une correction vient du
 *       secrétariat ou d'un formateur est un droit ; obtenir le nom d'un
 *       agent n'en est pas un (minimisation, docs/02 §14).</li>
 * </ul>
 *
 * <p>Le cache est local à l'instance de résolution : un historique
 * touche presque toujours les mêmes quelques auteurs, il serait absurde
 * d'interroger l'annuaire une fois par ligne (NFR-PERF-08).
 */
@Component
class AttendanceActorResolver {

    /** Du plus significatif au moins : un cumul de rôles n'affiche qu'un libellé. */
    private static final List<String> ROLE_PRECEDENCE = List.of(
            "SUPER_ADMIN", "ADMIN", "SCHOOL_ADMINISTRATION",
            "PEDAGOGICAL_MANAGER", "TEACHER", "STUDENT");

    private final UserDirectory userDirectory;

    AttendanceActorResolver(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    /** Une session de résolution, à usage local et non partagé. */
    Lookup lookup() {
        return new Lookup();
    }

    String role(Long userInternalId) {
        return lookup().role(userInternalId);
    }

    final class Lookup {

        private final Map<Long, String> roles = new HashMap<>();
        private final Map<Long, String> names = new HashMap<>();

        String role(Long userInternalId) {
            if (userInternalId == null) {
                return null;
            }
            return roles.computeIfAbsent(userInternalId, id -> userDirectory.findByInternalId(id)
                    .map(user -> mostSignificant(user.activeRoles()))
                    .orElse(null));
        }

        /** Identité civile de l'auteur — jamais servie à un apprenant. */
        String displayName(Long userInternalId) {
            if (userInternalId == null) {
                return null;
            }
            return names.computeIfAbsent(userInternalId, id -> userDirectory.findName(id)
                    .map(name -> (safe(name.firstName()) + " " + safe(name.lastName())).trim())
                    .filter(value -> !value.isEmpty())
                    .orElse(null));
        }
    }

    private static String mostSignificant(Set<String> activeRoles) {
        if (activeRoles == null) {
            return null;
        }
        Set<String> stripped = activeRoles.stream()
                .map(AttendanceActorResolver::stripPrefix)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (String candidate : ROLE_PRECEDENCE) {
            if (stripped.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String stripPrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring("ROLE_".length()) : role;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
