package com.esic.connect.attendance.internal;

import java.util.UUID;

/**
 * Résultat d'une résolution de jeton / code court d'émargement : la
 * séance <em>et</em> le point de contrôle visés (V10 — plusieurs points
 * de contrôle par séance).
 *
 * @param sessionPublicId    séance concernée
 * @param checkpointPublicId point de contrôle concerné
 */
record ResolvedAttendanceToken(UUID sessionPublicId, UUID checkpointPublicId) {
}
