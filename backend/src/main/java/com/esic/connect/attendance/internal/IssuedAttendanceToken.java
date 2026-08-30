package com.esic.connect.attendance.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Couple de capacités d'émargement fraîchement émis pour une séance.
 *
 * <p>{@code token} et {@code shortCode} ne transitent que dans le corps
 * HTTPS de la réponse et en mémoire de l'écran formateur : jamais dans
 * une URL, jamais journalisés, jamais persistés en base.
 *
 * @param token          jeton opaque (encodé dans le QR)
 * @param shortCode       code court équivalent (saisie manuelle)
 * @param expiresAt       instant d'expiration
 * @param sessionPublicId séance concernée
 */
record IssuedAttendanceToken(String token, String shortCode, Instant expiresAt, UUID sessionPublicId) {
}
