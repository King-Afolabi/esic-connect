package com.esic.connect.studentimport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port public de lecture des derniers imports d'apprenants pour les
 * tableaux de bord d'administration (bloc G1-F ; DEC-G1-010). Liste
 * <strong>bornée</strong> (≤ 10), triée du plus récent au plus ancien ;
 * aucune entité ni repository exposé.
 */
public interface StudentImportDashboardDirectory {

    /**
     * @param limit borne (le service impose ≤ 10)
     * @return les {@code limit} derniers jobs d'import, du plus récent au plus ancien
     */
    List<ImportJobDigest> recentJobs(int limit);

    /**
     * @param publicId  identifiant public du job
     * @param status    statut ({@code SIMULATED} / {@code APPLIED} / {@code CANCELLED} / {@code EXPIRED})
     * @param totalRows nombre de lignes du fichier
     * @param createdAt date de dépôt
     */
    record ImportJobDigest(UUID publicId, String status, int totalRows, Instant createdAt) {
    }
}
