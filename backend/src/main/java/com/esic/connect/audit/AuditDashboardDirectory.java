package com.esic.connect.audit;

import java.time.Instant;
import java.util.List;

/**
 * Port public de lecture des <strong>dernières opérations d'audit</strong>
 * pour la carte d'administration (EF-REP-007 ; docs/02 §22.6 —
 * « exports récents, dernières opérations d'audit »).
 *
 * <p>Volontairement pauvre : quatre champs, aucun motif, aucune colonne
 * JSON, aucun identifiant de ressource. Un tableau de bord montre qu'il
 * se passe quelque chose ; lire la piste d'audit passe par
 * {@code GET /api/v1/audit-events}, réservé à l'administration
 * (EF-AUD-002).
 */
public interface AuditDashboardDirectory {

    /** Dernières traces, toutes catégories confondues. */
    List<AuditLine> recent(int limit);

    /** Derniers exports de données produits (§22.6 — « exports récents »). */
    List<AuditLine> recentExports(int limit);

    /**
     * @param occurredAt date et heure
     * @param actor      identité affichée de l'acteur, figée à l'écriture
     * @param action     action métier
     * @param result     résultat
     */
    record AuditLine(Instant occurredAt, String actor, String action, String result) {
    }
}
