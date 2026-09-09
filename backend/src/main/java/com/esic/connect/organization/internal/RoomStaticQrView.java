package com.esic.connect.organization.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Vue <strong>administrative dédiée</strong> du QR fixe d'une salle
 * (EF-ORG-003 ; docs/02 §7.1, §16.6).
 *
 * <p>Sépare le jeton du QR du contrat général {@code RoomResponse} : la
 * référence complète est un <em>secret d'affiche</em>, pas une donnée de
 * consultation courante. Seules les routes {@code /rooms/{id}/static-qr}
 * la renvoient, et uniquement aux rôles qui peuvent l'imprimer
 * ({@code ADMIN} / {@code SUPER_ADMIN} / {@code SCHOOL_ADMINISTRATION}).
 *
 * <ul>
 *   <li>{@code issued == false} : aucun QR n'a encore été émis pour cette
 *       salle — {@code staticQrReference}, {@code maskedReference},
 *       {@code checkInPath} et {@code staticQrIssuedAt} sont {@code null} ;</li>
 *   <li>{@code maskedReference} : forme partiellement masquée, sûre à
 *       afficher dans un pied d'affiche ou une liste (jamais la valeur
 *       complète) ;</li>
 *   <li>{@code checkInPath} : chemin relatif d'émargement à encoder dans
 *       le QR ({@code /attendance?ref=<jeton>}). L'origine publique est
 *       ajoutée côté client — le serveur ne la connaît pas de façon
 *       fiable derrière le tunnel.</li>
 * </ul>
 */
record RoomStaticQrView(
        UUID roomPublicId,
        String roomCode,
        String roomName,
        String buildingName,
        String siteName,
        String floorLabel,
        boolean issued,
        String staticQrReference,
        String maskedReference,
        String checkInPath,
        Instant staticQrIssuedAt) {

    static RoomStaticQrView from(Room room) {
        String reference = room.getStaticQrReference();
        boolean issued = reference != null && !reference.isBlank();
        return new RoomStaticQrView(
                room.getPublicId(),
                room.getCode(),
                room.getName(),
                room.getBuilding() != null ? room.getBuilding().getName() : null,
                room.getSite().getName(),
                room.getFloorLabel(),
                issued,
                issued ? reference : null,
                issued ? mask(reference) : null,
                issued ? "/attendance?ref=" + reference : null,
                issued ? room.getStaticQrIssuedAt() : null);
    }

    /**
     * Masque une référence en ne laissant que ses quatre premiers et
     * quatre derniers caractères : assez pour reconnaître une affiche,
     * jamais assez pour la reconstituer.
     */
    private static String mask(String reference) {
        if (reference.length() <= 10) {
            return "…";
        }
        return reference.substring(0, 4) + "…" + reference.substring(reference.length() - 4);
    }
}
