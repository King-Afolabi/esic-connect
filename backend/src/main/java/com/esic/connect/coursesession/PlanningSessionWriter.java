package com.esic.connect.coursesession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port d'écriture public du module {@code coursesession}, appelé par le
 * module {@code planning} au moment de la <strong>publication</strong>
 * d'une version de planning (DEC-G1-001).
 *
 * <p>Contrat :
 * <ul>
 *   <li>appelé <strong>synchronement, dans la transaction de publication</strong>
 *       de {@code planning} : toute exception levée ici fait rollback
 *       l'ensemble de la publication (aucune séance, aucune version) ;</li>
 *   <li>aucune clé SQL interne en entrée — seulement des {@link UUID}
 *       publics ; {@code coursesession} les résout en interne
 *       ({@code TeacherDirectory}, {@code ClassGroupDirectory}) ;</li>
 *   <li><strong>idempotent par {@code entryPublicId}</strong> : réappeler
 *       {@link #sync} avec les mêmes entrées ne recrée rien (unicité
 *       {@code course_session.planning_entry_public_id}) ;</li>
 *   <li>ne renvoie ni entité JPA, ni repository, ni type de
 *       {@code coursesession.internal}.</li>
 * </ul>
 *
 * <p>Devenir des séances (DEC-G1-004) :
 * <ul>
 *   <li>{@code entryPublicId} inconnu ⇒ <strong>création</strong> d'une
 *       séance {@code PLANNED} d'origine planning
 *       ({@code planning_entry_public_id} renseigné, {@code exception_reason}
 *       nul) ;</li>
 *   <li>{@code entryPublicId} déjà lié à une séance {@code PLANNED} ⇒
 *       <strong>réutilisation</strong> (mise à jour des propriétés
 *       modifiables) ;</li>
 *   <li>{@code entryPublicId} lié à une séance {@code OPEN} / {@code CLOSED}
 *       ⇒ <strong>jamais</strong> réécrite (l'émargement fait foi) ;</li>
 *   <li>séance {@code PLANNED} d'origine planning dont l'{@code entryPublicId}
 *       n'est plus présent dans {@code entries} ⇒
 *       <strong>supersédée</strong> ({@code superseded_by_scheduling = true}).</li>
 * </ul>
 */
public interface PlanningSessionWriter {

    /**
     * Applique l'ensemble des entrées d'une version de planning publiée à
     * toutes les séances « planning » de la classe + année visées.
     *
     * @param command lot immuable d'entrées à synchroniser
     * @return le détail des séances créées / réutilisées / supersédées
     * @throws PlanningSessionSyncException si une entrée réfère un
     *         formateur ou une classe non résolvable, ou toute autre
     *         incohérence (déclenche le rollback de la publication)
     */
    PlanningSyncResult sync(PlanningSyncCommand command);

    /**
     * @param scheduleVersionPublicId identifiant public de la
     *                                {@code planning_version} publiée
     * @param classGroupPublicId       classe visée
     * @param academicYearPublicId     année scolaire visée
     * @param entries                  entrées de la version (ordre non
     *                                 significatif)
     */
    record PlanningSyncCommand(
            UUID scheduleVersionPublicId,
            UUID classGroupPublicId,
            UUID academicYearPublicId,
            List<PlannedSession> entries) {
    }

    /**
     * Une entrée de planning à matérialiser en séance.
     *
     * @param entryPublicId identifiant public stable de la
     *                      {@code planning_entry} (identité inter-versions)
     * @param teacherPublicId identifiant public du compte formateur
     *                        ({@code user_account.public_id}) — jamais une
     *                        clé SQL
     * @param roomCode      code fonctionnel de salle, ou {@code null}
     *                      (RG-035 : salle affectable après l'import) —
     *                      non consommé par {@code coursesession} en G1
     * @param title         libellé de la séance (non vide)
     * @param startsAt      début (UTC)
     * @param endsAt        fin (UTC)
     * @param timeZoneId    fuseau IANA de saisie (affichage)
     */
    record PlannedSession(
            UUID entryPublicId,
            UUID teacherPublicId,
            String roomCode,
            String title,
            Instant startsAt,
            Instant endsAt,
            String timeZoneId) {
    }

    /**
     * @param created    entrées ayant donné lieu à une nouvelle séance
     * @param reused     entrées dont la séance {@code PLANNED} existante a
     *                   été réutilisée (éventuellement mise à jour)
     * @param superseded séances « planning » {@code PLANNED} retirées de
     *                   la nouvelle version
     */
    record PlanningSyncResult(
            List<SyncedSession> created,
            List<SyncedSession> reused,
            List<SupersededSession> superseded) {
    }

    /**
     * @param entryPublicId   entrée de planning traitée
     * @param sessionPublicId séance {@code course_session} correspondante
     */
    record SyncedSession(UUID entryPublicId, UUID sessionPublicId) {
    }

    /**
     * @param sessionPublicId   séance supersédée
     * @param previousEntryPublicId entrée de planning qui la liait avant
     */
    record SupersededSession(UUID sessionPublicId, UUID previousEntryPublicId) {
    }

    /**
     * Erreur de synchronisation levée par {@link #sync}. Non sensible :
     * porte seulement une catégorie et l'identifiant public d'entrée
     * fautif. Fait rollback la transaction de publication.
     */
    final class PlanningSessionSyncException extends RuntimeException {

        /** Catégorie exhaustive de l'échec. */
        public enum Kind {
            /** {@code teacherPublicId} inconnu, non actif ou sans rôle {@code TEACHER}. */
            TEACHER_NOT_ELIGIBLE,
            /** {@code classGroupPublicId} inconnu. */
            CLASS_UNKNOWN,
            /** Une entrée est mal formée (titre vide, période invalide, fuseau inconnu…). */
            INVALID_ENTRY
        }

        private final transient Kind kind;
        private final transient UUID entryPublicId;

        public PlanningSessionSyncException(Kind kind, UUID entryPublicId) {
            super(kind.name());
            this.kind = kind;
            this.entryPublicId = entryPublicId;
        }

        public Kind kind() {
            return kind;
        }

        public UUID entryPublicId() {
            return entryPublicId;
        }
    }
}
