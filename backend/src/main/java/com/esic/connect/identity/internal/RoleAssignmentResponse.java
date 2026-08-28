package com.esic.connect.identity.internal;

import java.time.Instant;

/**
 * Une affectation de rôle, active ou clôturée (l'historique est conservé,
 * cahier §9.7 / modèle §10.3).
 */
record RoleAssignmentResponse(
        RoleCode role,
        boolean active,
        Instant validFrom,
        Instant validUntil) {
}
