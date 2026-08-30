package com.esic.connect.attendance.internal;

import java.util.List;

/**
 * Détail d'une présence de l'apprenant : la ligne
 * {@link MyAttendanceRow}, l'historique de correction et le justificatif
 * courant s'il existe.
 */
record MyAttendanceDetail(
        MyAttendanceRow row,
        List<AttendanceCorrectionResponse> history,
        JustificationResponse justification) {
}
