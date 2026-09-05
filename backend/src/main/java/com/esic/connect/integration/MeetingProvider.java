package com.esic.connect.integration;

import java.time.Instant;
import java.util.Optional;

/**
 * Port sortant de création d'une réunion en ligne pour une séance
 * distancielle ou hybride (EF-INT-002 ; docs/02 §28.2).
 *
 * <p>Deux adaptateurs : Microsoft Graph, et un adaptateur
 * <strong>inactif</strong> qui refuse et le dit. Aucun des deux ne
 * fabrique de lien plausible : un lien de réunion qui ne mène nulle part
 * est pire qu'une absence de lien, parce qu'on ne s'en aperçoit qu'à
 * l'heure du cours.
 */
public interface MeetingProvider {

    /**
     * @return {@code true} si un fournisseur réel est configuré et
     *         joignable en configuration ; {@code false} si le produit
     *         fonctionne sans intégration
     */
    boolean isActive();

    /** Nom du fournisseur, pour affichage et journalisation. */
    String providerName();

    /**
     * Crée une réunion en ligne.
     *
     * @return le lien de réunion, ou {@link Optional#empty()} si aucun
     *         fournisseur n'est actif
     * @throws MeetingProviderException si un fournisseur actif a échoué —
     *         un échec ne doit jamais être confondu avec « pas d'intégration »
     */
    Optional<Meeting> createMeeting(MeetingRequest request);

    /**
     * @param subject       objet de la réunion
     * @param startsAt      début
     * @param endsAt        fin
     * @param organizerHint référence de l'organisateur telle que connue du
     *                      produit (identifiant public de compte) — jamais
     *                      une adresse en clair côté appelant
     */
    record MeetingRequest(String subject, Instant startsAt, Instant endsAt, String organizerHint) {
    }

    /**
     * @param joinUrl    lien de participation
     * @param externalId identifiant de la réunion chez le fournisseur
     */
    record Meeting(String joinUrl, String externalId) {
    }
}
