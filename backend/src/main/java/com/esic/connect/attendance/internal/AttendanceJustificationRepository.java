package com.esic.connect.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AttendanceJustificationRepository
        extends JpaRepository<AttendanceJustification, Long>, JpaSpecificationExecutor<AttendanceJustification> {

    Optional<AttendanceJustification> findByPublicId(UUID publicId);

    List<AttendanceJustification> findByAttendanceRecordIdOrderBySubmittedAtDesc(Long attendanceRecordId);

    boolean existsByAttendanceRecordIdAndStatusNot(Long attendanceRecordId, JustificationStatus status);

    List<AttendanceJustification> findBySubmittedByIdOrderBySubmittedAtDesc(Long submittedById);

    List<AttendanceJustification> findByStatusInOrderBySubmittedAtAsc(Collection<JustificationStatus> statuses);

    /** Décompte borné (bloc G1-F). */
    long countByStatus(JustificationStatus status);

    /**
     * Nombre de justificatifs d'un statut donné rattachés à l'un des
     * enregistrements de présence indiqués — pour le compteur « en
     * attente dans mon périmètre » du tableau de bord et de la synthèse,
     * sans matérialiser toute la file des justificatifs {@code PENDING}
     * de la base (NFR-PERF-08).
     */
    long countByStatusAndAttendanceRecordIdIn(JustificationStatus status, Collection<Long> attendanceRecordIds);

    long countBySubmittedByIdAndStatus(Long submittedById, JustificationStatus status);

    /** Justificatifs déposés par un compte (comparaison de doublons, ANO-USER-001). */
    long countBySubmittedById(Long submittedById);

    /**
     * Justificatifs examinés sur une fenêtre (EF-REP-007 — volume et
     * délai de traitement). Seule la paire de dates est chargée : le
     * calcul du délai médian n'a besoin de rien d'autre, et charger les
     * entités entières ferait passer commentaires et références par la
     * mémoire pour un simple compte.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT j.submittedAt, j.reviewedAt FROM AttendanceJustification j
            WHERE j.reviewedAt IS NOT NULL AND j.reviewedAt >= :from AND j.reviewedAt <= :to
            """)
    List<Object[]> findDecisionDelays(
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to);
}