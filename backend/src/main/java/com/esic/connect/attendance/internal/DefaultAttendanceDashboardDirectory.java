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
    private final AttendanceReportService reportService;
    private final com.esic.connect.academic.ClassGroupDirectory classGroupDirectory;

    DefaultAttendanceDashboardDirectory(AttendanceRecordRepository recordRepository,
                                        AttendanceJustificationRepository justificationRepository,
                                        UserDirectory userDirectory,
                                        AttendanceReportService reportService,
                                        com.esic.connect.academic.ClassGroupDirectory classGroupDirectory) {
        this.recordRepository = recordRepository;
        this.justificationRepository = justificationRepository;
        this.userDirectory = userDirectory;
        this.reportService = reportService;
        this.classGroupDirectory = classGroupDirectory;
    }

    /**
     * Assiduité par classe dans le périmètre de l'appelant (EF-REP-007).
     *
     * <p>Réutilise le calcul des rapports plutôt que d'en écrire un
     * second : deux formules produiraient tôt ou tard deux taux
     * différents pour la même classe, et c'est précisément ce dont un
     * tableau de bord ne se remet pas. Le périmètre est appliqué par
     * {@link AttendanceReportService}, à partir du contexte de sécurité.
     */
    @Override
    @Transactional(readOnly = true)
    public java.util.List<ClassAttendanceDigest> classDigests(java.time.Instant from,
                                                              java.time.Instant to) {
        java.util.List<AttendanceReports.ClassRow> rows = reportService.classReport(from, to, null, null);
        if (rows.isEmpty()) {
            return java.util.List.of();
        }
        // Résolution des codes de formation en une requête : le
        // regroupement « par formation » de la carte d'administration ne
        // doit pas coûter une requête par classe (NFR-PERF-08).
        java.util.Map<UUID, String> programCodes = new java.util.HashMap<>();
        for (com.esic.connect.academic.ClassGroupDirectory.ClassGroupRef ref
                : classGroupDirectory.findByPublicIds(rows.stream()
                        .map(AttendanceReports.ClassRow::classGroupPublicId)
                        .filter(java.util.Objects::nonNull)
                        .toList())) {
            programCodes.put(ref.publicId(), ref.programCode());
        }
        return rows.stream()
                .map(row -> {
                    AttendanceReports.HalfDayTotals t = row.totals();
                    return new ClassAttendanceDigest(row.classGroupPublicId(), row.classCode(),
                            programCodes.get(row.classGroupPublicId()),
                            t.expectedHalfDays(), t.presentHalfDays(), t.absentHalfDays(),
                            t.excusedHalfDays(), t.lateCount(), t.attendanceRate());
                })
                .toList();
    }

    /**
     * Justificatifs en attente dans le périmètre de l'appelant, sur la
     * fenêtre demandée.
     *
     * <p>Compter globalement afficherait à un responsable pédagogique des
     * dossiers qu'il ne peut pas ouvrir ; passer par la synthèse
     * complète recalculerait toute l'assiduité de la base pour un seul
     * nombre. La fenêtre est donc obligatoire, et le compte est direct.
     */
    @Override
    @Transactional(readOnly = true)
    public long countPendingJustificationsInScope(java.time.Instant from, java.time.Instant to) {
        return reportService.countPendingJustificationsInScope(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public JustificationThroughput justificationThroughput(java.time.Instant from, java.time.Instant to) {
        long pending = justificationRepository.countByStatus(JustificationStatus.PENDING);
        java.util.List<Object[]> decisions = justificationRepository.findDecisionDelays(from, to);
        if (decisions.isEmpty()) {
            // `null` et non `0.0` : aucun dossier traité n'est pas un
            // traitement instantané.
            return new JustificationThroughput(pending, 0, null);
        }
        double[] hours = decisions.stream()
                .mapToDouble(row -> java.time.Duration
                        .between((java.time.Instant) row[0], (java.time.Instant) row[1])
                        .toMinutes() / 60.0d)
                .sorted()
                .toArray();
        int middle = hours.length / 2;
        double median = hours.length % 2 == 1
                ? hours[middle]
                : (hours[middle - 1] + hours[middle]) / 2.0d;
        return new JustificationThroughput(pending, hours.length,
                Math.round(median * 10d) / 10d);
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
