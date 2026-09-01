package com.esic.connect.attendance.internal;

import com.esic.connect.attendance.AttendanceDashboardDirectory;
import com.esic.connect.attendance.AttendanceStatus;
import com.esic.connect.identity.UserDirectory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation du port {@link AttendanceDashboardDirectory} (bloc G1-F).
 * Confinée à {@code attendance.internal} ; requêtes agrégées bornées
 * uniquement.
 */
@Component
class DefaultAttendanceDashboardDirectory implements AttendanceDashboardDirectory {

    private final AttendanceRecordRepository recordRepository;
    private final AttendanceJustificationRepository justificationRepository;
    private final UserDirectory userDirectory;

    DefaultAttendanceDashboardDirectory(AttendanceRecordRepository recordRepository,
                                        AttendanceJustificationRepository justificationRepository,
                                        UserDirectory userDirectory) {
        this.recordRepository = recordRepository;
        this.justificationRepository = justificationRepository;
        this.userDirectory = userDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingJustifications() {
        return justificationRepository.countByStatus(JustificationStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentAttendanceDigest studentDigest(UUID studentUserPublicId) {
        Long userId = userDirectory.findByPublicId(studentUserPublicId)
                .map(UserDirectory.UserRef::internalId)
                .orElse(null);
        if (userId == null) {
            return new StudentAttendanceDigest(0, 0, 0, 0, 0, 0);
        }
        long present = 0;
        long late = 0;
        long absent = 0;
        long excused = 0;
        for (Object[] row : recordRepository.countByStatusForStudent(userId)) {
            AttendanceStatus status = (AttendanceStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case PRESENT -> present = count;
                case LATE -> late = count;
                case ABSENT -> absent = count;
                case EXCUSED_ABSENCE -> excused = count;
                default -> {
                    // PARTIAL / TO_CONFIRM éventuels : non comptés dans les 4 cartes.
                }
            }
        }
        long pending = justificationRepository.countBySubmittedByIdAndStatus(userId, JustificationStatus.PENDING);
        long rejected = justificationRepository.countBySubmittedByIdAndStatus(userId, JustificationStatus.REJECTED);
        return new StudentAttendanceDigest(present, late, absent, excused, pending, rejected);
    }
}
