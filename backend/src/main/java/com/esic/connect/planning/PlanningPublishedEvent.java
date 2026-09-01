package com.esic.connect.planning;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Événement métier publié <strong>dans la transaction de publication</strong>
 * d'une version de planning (RG-033 : « une modification publiée génère
 * une notification »). Consommé après commit par {@code audit} et, à
 * partir de G1-D, par {@code notification}.
 *
 * <p>Contenu neutre : identifiants publics uniquement, aucun contenu de
 * cellule, aucune donnée personnelle.
 *
 * @param schedulePublicId    identifiant public du planning
 * @param versionPublicId     identifiant public de la nouvelle version publiée
 * @param versionNumber       numéro de la nouvelle version (1, 2, …)
 * @param classGroupPublicId  classe concernée
 * @param academicYearPublicId année scolaire concernée
 * @param initialPublication  {@code true} si c'est la première publication
 *                            ({@code versionNumber == 1})
 * @param addedSessionPublicIds    séances créées par cette publication
 * @param updatedSessionPublicIds  séances mises à jour
 * @param supersededSessionPublicIds séances retirées (supersédées)
 * @param publishedAt         instant de publication
 * @param actorPublicId       auteur de la publication (peut être {@code null})
 */
public record PlanningPublishedEvent(
        UUID schedulePublicId,
        UUID versionPublicId,
        int versionNumber,
        UUID classGroupPublicId,
        UUID academicYearPublicId,
        boolean initialPublication,
        List<UUID> addedSessionPublicIds,
        List<UUID> updatedSessionPublicIds,
        List<UUID> supersededSessionPublicIds,
        Instant publishedAt,
        UUID actorPublicId) {
}
