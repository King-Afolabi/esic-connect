package com.esic.connect.coursesession;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port public minimal du module {@code coursesession}.
 *
 * <p>Permet au module {@code attendance} de résoudre une séance et son
 * point de contrôle unique à partir de l'identifiant public porté par un
 * jeton Redis, et d'appliquer le contrôle d'accès de lecture / gestion
 * <em>sans</em> dupliquer la logique de périmètre (elle est décidée dans
 * {@code coursesession} à partir du contexte Spring Security, jamais d'un
 * paramètre client). Ne renvoie ni entité JPA, ni repository, ni type de
 * {@code coursesession.internal}.
 */
public interface CourseSessionDirectory {

    /**
     * Résout la séance {@code sessionPublicId} en appliquant le niveau
     * d'accès demandé pour l'appelant courant.
     *
     * @param sessionPublicId identifiant public de la séance ; peut être
     *                        {@code null}
     * @param level           niveau d'accès requis
     * @return un {@link SessionAccess} : {@link Access#GRANTED} avec la
     *         {@link SessionRef}, ou {@link Access#NOT_FOUND} /
     *         {@link Access#FORBIDDEN} sans référence
     */
    SessionAccess resolve(UUID sessionPublicId, AccessLevel level);

    /**
     * Résout une séance <strong>sans</strong> appliquer de contrôle
     * d'accès de l'appelant : réservé au module {@code attendance} après
     * qu'un jeton d'émargement émis par le serveur a été validé — c'est
     * le jeton, et non le rôle de l'appelant, qui constitue la capacité.
     *
     * @param sessionPublicId identifiant public de la séance
     * @return la référence si la séance existe, {@link Optional#empty()} sinon
     */
    Optional<SessionRef> findForAttendance(UUID sessionPublicId);

    /** Niveau d'accès demandé sur une séance. */
    enum AccessLevel {
        /** Consultation (séance + présences). {@code TEACHER} : sa séance uniquement. */
        READ,
        /**
         * Gestion (ouverture / fermeture, émission d'un jeton
         * d'émargement). Exclut {@code SCHOOL_ADMINISTRATION} (lecture
         * seule dans cette tranche).
         */
        MANAGE
    }

    /** Résultat d'un contrôle d'accès sur une séance. */
    enum Access {
        GRANTED,
        NOT_FOUND,
        FORBIDDEN
    }

    /**
     * @param access  issue du contrôle d'accès
     * @param session référence de la séance si {@code access == GRANTED},
     *                {@code null} sinon
     */
    record SessionAccess(Access access, SessionRef session) {
    }

    /**
     * Référence technique d'une séance, suffisante pour valider un
     * émargement et bâtir la liste des présences.
     *
     * @param internalId           clé primaire SQL de la séance
     * @param publicId             identifiant public de la séance
     * @param title                libellé libre facultatif ({@code null} possible)
     * @param status               statut du cycle de vie
     * @param checkpointInternalId clé primaire SQL du point de contrôle
     *                             unique (valeur de
     *                             {@code attendance_record.attendance_checkpoint_id})
     * @param checkpointPublicId   identifiant public du point de contrôle
     * @param checkpointOpen       {@code true} si le point de contrôle est
     *                             ouvert (séance {@code OPEN})
     * @param classGroupPublicIds  identifiants publics des classes
     *                             rattachées à la séance
     * @param startsAt             début planifié
     * @param endsAt               fin planifiée
     */
    record SessionRef(
            long internalId,
            UUID publicId,
            String title,
            SessionLifecycle status,
            long checkpointInternalId,
            UUID checkpointPublicId,
            boolean checkpointOpen,
            Set<UUID> classGroupPublicIds,
            Instant startsAt,
            Instant endsAt) {
    }
}
