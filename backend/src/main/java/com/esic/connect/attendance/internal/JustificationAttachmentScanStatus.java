package com.esic.connect.attendance.internal;

/**
 * Verdict d'analyse antivirus porté par une pièce jointe (V30 ;
 * EF-JUS-002).
 *
 * <p>Quatre valeurs, parce que trois situations différentes sont
 * <em>toutes</em> distinctes de « sain » — les confondre reviendrait à
 * présenter une absence de contrôle comme un contrôle réussi.
 */
enum JustificationAttachmentScanStatus {
    /** Aucun analyseur en service : rien n'a été vérifié, et c'est dit. */
    NOT_SCANNED,
    /** Analysé, aucune signature détectée. */
    CLEAN,
    /** Signature détectée : contenu jamais servi, quel que soit le demandeur. */
    INFECTED,
    /** Analyseur configuré mais sans réponse exploitable. */
    UNAVAILABLE;

    /** {@code true} si le contenu peut être remis à un humain. */
    boolean allowsDownload(boolean scanRequired) {
        return switch (this) {
            case CLEAN -> true;
            case INFECTED -> false;
            // Sans exigence d'analyse, le produit sert le fichier en
            // déclarant qu'il n'a pas été analysé. Avec l'exigence, il
            // refuse : c'est le sens de la quarantaine du cahier.
            case NOT_SCANNED, UNAVAILABLE -> !scanRequired;
        };
    }
}
