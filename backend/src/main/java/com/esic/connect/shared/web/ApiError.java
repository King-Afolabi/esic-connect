package com.esic.connect.shared.web;

import java.time.Instant;
import java.util.List;

/**
 * Format d'erreur commun de l'API, tel que défini dans
 * docs/03-architecture.md §10.3.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<String> details) {
}
