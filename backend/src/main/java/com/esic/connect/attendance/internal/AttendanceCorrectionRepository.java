package com.esic.connect.attendance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Accès à l'historique append-only {@code attendance_correction}. Aucune
 * méthode de mise à jour ou de suppression n'est exposée : le repository
 * hérite de {@link JpaRepository} mais le service ne fait qu'insérer et
 * lire.
 */
interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {

    List<AttendanceCorrection> findByAttendanceRecordIdOrderByOccurredAtAscIdAsc(Long attendanceRecordId);

    /**
     * Historique de plusieurs présences en une requête — le journal de
     * transparence en couvre potentiellement des centaines, une requête
     * par ligne serait proportionnelle à l'affichage (NFR-PERF-08).
     */
    List<AttendanceCorrection> findByAttendanceRecordIdInOrderByOccurredAtDescIdDesc(
            java.util.Collection<Long> attendanceRecordIds);
}
