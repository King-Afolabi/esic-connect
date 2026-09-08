package com.esic.connect.attendance.internal;

import com.esic.connect.identity.DuplicateDependencyContributor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remonte à {@code identity}, <strong>en lecture seule</strong>, le volume
 * de données que {@code attendance} rattache à un compte : présences
 * enregistrées, justificatifs déposés, dossiers de départ anticipé
 * signalés (ANO-USER-001 — comparaison de doublons).
 *
 * <p>Trois décomptes bornés, indexés sur la clé étrangère du compte.
 * Aucune écriture, aucun effet de bord.
 */
@Component
class AttendanceDuplicateContributor implements DuplicateDependencyContributor {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceJustificationRepository justificationRepository;
    private final EarlyDepartureRepository earlyDepartureRepository;

    AttendanceDuplicateContributor(AttendanceRecordRepository attendanceRecordRepository,
                                   AttendanceJustificationRepository justificationRepository,
                                   EarlyDepartureRepository earlyDepartureRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.justificationRepository = justificationRepository;
        this.earlyDepartureRepository = earlyDepartureRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(long userInternalId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("attendanceRecords",
                attendanceRecordRepository.countByStudentUserId(userInternalId));
        counts.put("justifications",
                justificationRepository.countBySubmittedById(userInternalId));
        counts.put("earlyDepartures",
                earlyDepartureRepository.countByRequestedById(userInternalId));
        return counts;
    }
}
