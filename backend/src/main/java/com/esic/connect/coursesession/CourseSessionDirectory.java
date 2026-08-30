package com.esic.connect.coursesession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port public minimal du module {@code coursesession}.
 *
 * <p>Permet au module {@code attendance} de résoudre une séance et ses
 * points de contrôle à partir de l'identifiant public porté par un jeton
 * Redis, et d'appliquer le contrôle d'accès de lecture / gestion
 * <em>sans</em> dupliquer la logique de périmètre (elle est décidée dans
 * {@code coursesession} à partir du contexte Spring Security, jamais d'un
 * paramètre client). Ne renvoie ni entité JPA, ni repository, ni type de
 * {@code coursesession.internal}.
 *
 * <p>V10 : une séance porte <strong>plusieurs</strong> points de contrôle
 * ({@link CheckpointRef}) au lieu d'un seul.
 */
public interface CourseSessionDirectory {

    /**
     * Résout la séance {@code sessionPublicId} en appliquant le niveau
     * d'accès demandé pour l'appelant courant.
     */
    SessionAccess resolve(UUID sessionPublicId, AccessLevel level);

    /**
     * Résout une séance <strong>sans</strong> appliquer de contrôle
     * d'accès de l'appelant : réservé au module {@code attendance} après
     * qu'un jeton d'émargement émis par le serveur a été validé — c'est
     * le jeton, et non le rôle de l'appelant, qui constitue la capacité.
     */
    Optional<SessionRef> findForAttendance(UUID sessionPublicId);

    /**
     * Résout un point de contrôle précis d'une séance,
     * <strong>sans</strong> contrôle d'accès (réservé à {@code attendance}
     * après validation d'un jeton).
     *
     * @return le point de contrôle si la séance et le point de contrôle
     *         existent et que le point appartient bien à la séance,
     *         {@link Optional#empty()} sinon
     */
    Optional<CheckpointRef> findCheckpointForAttendance(UUID sessionPublicId, UUID checkpointPublicId);

    /**
     * Séances dont au moins une classe figure dans {@code classGroupPublicIds}
     * et dont le début tombe dans {@code [from, to]} (bornes incluses ;
     * {@code null} = borne ouverte).
     *
     * <p><strong>Sans</strong> contrôle d'accès de l'appelant : le module
     * {@code attendance} l'utilise pour bâtir l'assiduité attendue d'un
     * apprenant (à partir de ses seules inscriptions) et les rapports
     * (le contrôle de périmètre des classes est fait dans
     * {@code attendance} via {@code AcademicScopeDirectory}). Résultat
     * trié par début de séance.
     */
    List<SessionRef> findSessionsForClasses(Set<UUID> classGroupPublicIds, Instant from, Instant to);

    /**
     * Séance portant le point de contrôle {@code checkpointPublicId},
     * <strong>sans</strong> contrôle d'accès (le module {@code attendance}
     * l'utilise quand un apprenant ne connaît que l'identifiant du point
     * de contrôle — dépôt d'un justificatif).
     */
    Optional<SessionRef> findSessionByCheckpointPublicId(UUID checkpointPublicId);

    /** Niveau d'accès demandé sur une séance. */
    enum AccessLevel {
        /** Consultation (séance + présences). {@code TEACHER} : sa séance uniquement. */
        READ,
        /**
         * Gestion (points de contrôle, ouverture / fermeture, émission
         * d'un jeton). Exclut {@code SCHOOL_ADMINISTRATION} (lecture
         * seule des séances).
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
     * émargement, bâtir la liste des présences et présenter les points de
     * contrôle.
     *
     * @param internalId          clé primaire SQL de la séance
     * @param publicId            identifiant public de la séance
     * @param title               libellé libre facultatif ({@code null} possible)
     * @param status              statut du cycle de vie
     * @param teacherUserId       compte formateur (identifiant interne)
     * @param checkpoints         points de contrôle, triés par ordre d'affichage
     * @param classGroupPublicIds identifiants publics des classes rattachées
     * @param timeZoneId          fuseau IANA de saisie de la séance
     * @param startsAt            début planifié
     * @param endsAt              fin planifiée
     */
    record SessionRef(
            long internalId,
            UUID publicId,
            String title,
            SessionLifecycle status,
            long teacherUserId,
            List<CheckpointRef> checkpoints,
            Set<UUID> classGroupPublicIds,
            String timeZoneId,
            Instant startsAt,
            Instant endsAt) {

        /** Point de contrôle {@code publicId} de la séance, s'il existe. */
        public Optional<CheckpointRef> checkpoint(UUID checkpointPublicId) {
            return checkpoints.stream()
                    .filter(cp -> cp.publicId().equals(checkpointPublicId))
                    .findFirst();
        }

        /**
         * Premier point de contrôle {@code OPEN} par ordre d'affichage —
         * cible par défaut de l'ancienne route d'émission de jeton
         * (compat V9 : séance à un seul point de contrôle).
         */
        public Optional<CheckpointRef> firstOpenCheckpoint() {
            return checkpoints.stream()
                    .filter(cp -> cp.status() == AttendanceCheckpointStatus.OPEN)
                    .findFirst();
        }
    }

    /**
     * Référence technique d'un point de contrôle d'émargement.
     *
     * @param internalId   clé primaire SQL (valeur de
     *                     {@code attendance_record.attendance_checkpoint_id})
     * @param publicId     identifiant public
     * @param label        libellé
     * @param type         type ({@code START} / {@code END} / {@code CUSTOM})
     * @param status       statut du cycle de vie propre
     * @param required     {@code true} si obligatoire pour le calcul d'assiduité
     * @param displayOrder ordre d'affichage
     * @param openedAt     instant d'ouverture ({@code null} tant que non ouvert)
     * @param closedAt     instant de fermeture ({@code null} tant que non fermé)
     */
    record CheckpointRef(
            long internalId,
            UUID publicId,
            String label,
            AttendanceCheckpointType type,
            AttendanceCheckpointStatus status,
            boolean required,
            int displayOrder,
            Instant openedAt,
            Instant closedAt) {

        public boolean isOpen() {
            return status == AttendanceCheckpointStatus.OPEN;
        }
    }
}
